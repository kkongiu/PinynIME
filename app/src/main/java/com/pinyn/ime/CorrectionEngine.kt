package com.pinyn.ime

/**
 * CorrectionEngine — correzione contestuale, segmentazione e accentazione.
 * Porting nativo di `correction-engine.js`.
 */
class CorrectionEngine(
    private val syllables: Set<String>,
    initialWords: List<HskWord>,
    private var hskLevel: Double = 9.0,
) {

    data class Entry(val numbers: String, val level: Double, val rank: Int)
    data class WordCompact(val compact: String, val numbers: String, val level: Double, val rank: Int)
    data class ToneChoice(val tone: Int, val share: Double)
    data class Segment(val count: Int, val text: String)

    var words: List<HskWord> = initialWords
        private set

    var bigrams: Map<String, Int> = emptyMap()
        private set

    val toneFreq = HashMap<String, HashMap<Int, Double>>()

    private val maxSyllableLen: Int = (syllables.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1)

    private val wordIndex = HashMap<String, Entry>()
    private val wordIndexCompact = HashMap<String, Entry>()
    private var hskSet: Set<String> = emptySet()
    private val wordCompacts = ArrayList<WordCompact>()
    private val pinyinLevelMap = HashMap<String, Double>()
    private var predictor: ((Array<String>) -> IntArray?)? = null

    init {
        bigrams = buildBigrams(words)
        hskSet = buildHskSet(words, hskLevel)
        indexWords()
    }

    fun setTonePredictor(fn: ((Array<String>) -> IntArray?)?) {
        predictor = fn
    }

    fun setLevel(level: Double) {
        hskLevel = level
        hskSet = buildHskSet(words, level)
    }

    fun setWords(wordList: List<HskWord>) {
        words = wordList
        bigrams = buildBigrams(words)
        indexWords()
        hskSet = buildHskSet(words, hskLevel)
    }

    fun bigramScore(prev: String?, cand: String): Int {
        if (prev.isNullOrEmpty()) return 0
        return bigrams["$prev $cand"] ?: 0
    }

    /** Suggerimenti ordinati per una sillaba senza tono (eventualmente con typo). */
    fun rankSuggestions(bare: String, prev: String?, maxDist: Int = if (bare.length >= 4) 2 else 1): List<String> {
        if (bare.length < 2) return emptyList()
        val cands = mutableListOf<Pair<String, Int>>()
        for (s in syllables) {
            if (Math.abs(s.length - bare.length) > maxDist) continue
            val d = Levenshtein.distance(s, bare)
            if (d <= maxDist) {
                val score = d * 10 - bigramScore(prev, s) * 3 - (if (s in hskSet) 1 else 0)
                cands.add(s to score)
            }
        }
        cands.sortBy { it.second }
        return cands.take(5).map { it.first }
    }

    /** Segmentazione DP di una stringa in sillabe valide. null se non segmentabile. */
    fun segment(bare: String): List<String>? {
        if (bare.isEmpty()) return null
        val best = arrayOfNulls<List<String>>(bare.length + 1)
        best[0] = emptyList()
        val maxLen = maxSyllableLen
        for (i in 1..bare.length) {
            for (l in maxLen downTo 1) {
                if (l > i) continue
                val s = bare.substring(i - l, i)
                val prev = best[i - l]
                if (s in syllables && prev != null) {
                    best[i] = prev + s
                    break
                }
            }
        }
        return best[bare.length]
    }

    /**
     * Segmenta una sequenza di sillabe in PAROLE del dizionario (maximal munch).
     * Le parole vengono restituite come bare compatto (es. ["wo","qu","xuexiao"]).
     */
    fun segmentWords(bares: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < bares.size) {
            var matchLen = 1
            for (len in minOf(6, bares.size - i) downTo 2) {
                val slice = bares.subList(i, i + len)
                if (wordIndex.containsKey(slice.joinToString(" ")) ||
                    wordIndexCompact.containsKey(slice.joinToString(""))
                ) {
                    matchLen = len
                    break
                }
            }
            out.add(bares.subList(i, i + matchLen).joinToString(""))
            i += matchLen
        }
        return out
    }

    /** Match a livello di PAROLA sul pinyin compatto. Ritorna pinyin accentato spaziato. */
    fun findWordSuggestions(bare: String, maxDist: Int = 2): List<String> {
        if (bare.length < 3) return emptyList()
        data class Match(val numbers: String, val distance: Int, val level: Double, val rank: Int)

        val out = mutableListOf<Match>()
        val seen = HashSet<String>()
        val first = bare[0]
        for (wc in wordCompacts) {
            if (wc.level > hskLevel + 1) continue
            if (wc.compact.firstOrNull() != first) continue
            if (Math.abs(wc.compact.length - bare.length) > maxDist) continue
            val d = Levenshtein.distance(wc.compact, bare)
            if (d <= maxDist && seen.add(wc.numbers)) {
                out.add(Match(wc.numbers, d, wc.level, wc.rank))
            }
        }
        out.sortWith(compareBy({ it.distance }, { it.level }, { it.rank }))
        return out.take(4).map { numbersToMarksSpaced(it.numbers) }
    }

    /** Accento "più probabile" per una sillaba senza tono (frequenza HSK). */
    fun accentSingle(syl: String): String {
        val freq = toneFreq[syl] ?: return syl
        var bestTone = -1
        var bestCount = -1.0
        for ((t, c) in freq) {
            if (c > bestCount) {
                bestCount = c
                bestTone = t
            }
        }
        if (bestTone <= 0) return syl
        return PinyinUtils.toToneMark(syl + bestTone)
    }

    /** Applica gli accenti a una frase di sillabe senza tono (match parole HSK prima). */
    fun addAccentsToPhrase(bares: List<String>): List<String> {
        val out = mutableListOf<String>()
        val L = bares.size
        var i = 0
        while (i < L) {
            var matched: Pair<Int, List<String>>? = null
            for (len in minOf(6, L - i) downTo 2) {
                val slice = bares.subList(i, i + len)
                val entry = wordIndex[slice.joinToString(" ")] ?: wordIndexCompact[slice.joinToString("")]
                if (entry != null) {
                    val parts = entry.numbers.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (parts.size == len) {
                        matched = len to parts
                        break
                    }
                }
            }
            if (matched != null) {
                for (p in matched.second) out.add(PinyinUtils.toToneMark(p))
                i += matched.first
            } else {
                out.add(accentSingle(bares[i]))
                i++
            }
        }
        return out
    }

    /**
     * Segmenta una frase di sillabe in parole del dizionario (maximal munch) e
     * restituisce segmenti accentati. Le parole vengono UNITE senza spazi.
     */
    fun accentRun(bares: List<String>): List<Segment> {
        val out = mutableListOf<Segment>()
        val L = bares.size
        var i = 0
        var ctxTones: IntArray? = null
        if (predictor != null) {
            ctxTones = try {
                predictor!!.invoke(bares.toTypedArray())
            } catch (_: Exception) {
                null
            }
        }
        while (i < L) {
            var match: Pair<Int, String>? = null
            for (len in minOf(6, L - i) downTo 2) {
                val slice = bares.subList(i, i + len)
                val entry = wordIndex[slice.joinToString(" ")] ?: wordIndexCompact[slice.joinToString("")]
                if (entry != null) {
                    match = len to entry.numbers
                    break
                }
            }
            if (match != null) {
                val text = joinedMarks(match.second)
                out.add(Segment(match.first, text))
                i += match.first
                continue
            }
            val cur = bares[i]
            if (cur !in syllables) {
                val entry = wordIndexCompact[cur]
                if (entry != null) {
                    out.add(Segment(1, joinedMarks(entry.numbers)))
                    i++
                    continue
                }
            }
            if (cur in syllables) {
                val choice = accentChoice(cur)
                val CONF = 0.65
                val text: String = when {
                    choice.share >= CONF && choice.tone >= 0 -> PinyinUtils.toToneMark(cur + choice.tone)
                    ctxTones != null && i < ctxTones.size && ctxTones[i] in 0..4 ->
                        PinyinUtils.toToneMark(cur + ctxTones[i])
                    else -> accentSingle(cur)
                }
                out.add(Segment(1, text))
            } else {
                out.add(Segment(1, accentSingle(cur)))
            }
            i++
        }
        return out
    }

    /** Tono + confidenza (share del tono più frequente, pesato per livello). */
    fun accentChoice(bare: String): ToneChoice {
        val freq = toneFreq[bare] ?: return ToneChoice(0, 0.0)
        var total = 0.0
        for (c in freq.values) total += c
        if (total == 0.0) return ToneChoice(0, 0.0)
        var bestTone = 0
        var bestCount = -1.0
        for ((k, c) in freq) {
            if (c > bestCount) {
                bestCount = c
                bestTone = k
            }
        }
        return ToneChoice(bestTone, bestCount / total)
    }

    /** Tutte le varianti di tono di una sillaba (ordinate per peso). */
    fun toneVariants(bare: String, minShare: Double = 0.01): List<String> {
        val freq = toneFreq[bare] ?: return emptyList()
        var total = 0.0
        for (c in freq.values) total += c
        if (total == 0.0) return emptyList()
        val keys = freq.keys.sortedByDescending { freq[it] }
        val out = mutableListOf<String>()
        for (k in keys) {
            if ((freq[k] ?: 0.0) / total < minShare) continue
            out.add(PinyinUtils.toToneMark(bare + k))
        }
        return out
    }

    /** Un tono è "sospetto" se la sua frequenza per quella sillaba è < 1%. */
    fun toneSuspect(bare: String, tone: Int): Boolean {
        val freq = toneFreq[bare] ?: return false
        var total = 0.0
        for (c in freq.values) total += c
        if (total == 0.0) return false
        val share = (freq[tone] ?: 0.0) / total
        return share < 0.01
    }

    /** Livello HSK della parola corrispondente a una stringa pinyin (accenti o numeri). */
    fun pinyinLevel(pinyinStr: String): Double? {
        if (pinyinStr.isEmpty()) return null
        val nums = pinyinStr.lowercase().replace("v", "ü")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { PinyinUtils.toToneNumber(it) }
        val bare = stripTones(nums)
        return pinyinLevelMap[bare]
    }

    private fun stripTones(numbers: String): String =
        numbers.lowercase().replace("v", "ü").replace(Regex("[0-5]"), "").trim()

    private fun joinedMarks(numbers: String): String =
        numbers.lowercase().replace("v", "ü")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString("") { PinyinUtils.toToneMark(it) }

    private fun numbersToMarksSpaced(numbers: String): String =
        numbers.lowercase()
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { PinyinUtils.toToneMark(it) }

    private fun better(a: Entry, b: Entry): Boolean =
        if (a.level != b.level) a.level < b.level else a.rank < b.rank

    private fun buildBigrams(words: List<HskWord>): Map<String, Int> {
        val map = HashMap<String, Int>()
        fun add(a: String?, b: String?) {
            if (a.isNullOrEmpty() || b.isNullOrEmpty()) return
            val k = "$a $b"
            map[k] = (map[k] ?: 0) + 1
        }
        for (w in words) {
            val syls = stripTones(w.pinyinNumbers).split(Regex("\\s+")).filter { it.isNotEmpty() }
            for (i in 1 until syls.size) add(syls[i - 1], syls[i])
        }
        for (seq in COMMON_SEQUENCES) {
            for (i in 1 until seq.size) add(seq[i - 1], seq[i])
        }
        return map
    }

    private fun indexWords() {
        wordIndex.clear()
        wordIndexCompact.clear()
        toneFreq.clear()
        wordCompacts.clear()
        pinyinLevelMap.clear()
        for (w in words) {
            val numbers = w.pinyinNumbers.trim()
            if (numbers.isEmpty()) continue
            val bares = stripTones(numbers).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val key = bares.joinToString(" ")
            val keyCompact = bares.joinToString("")
            if (bares.isNotEmpty()) {
                val entry = Entry(numbers, w.level, w.rank)
                val cur = wordIndex[key]
                if (cur == null || better(entry, cur)) wordIndex[key] = entry
                val curC = wordIndexCompact[keyCompact]
                if (curC == null || better(entry, curC)) wordIndexCompact[keyCompact] = entry
                wordCompacts.add(WordCompact(keyCompact, numbers, w.level, w.rank))
                val curL = pinyinLevelMap[key] ?: 999.0
                if (w.level < curL) pinyinLevelMap[key] = w.level
            }
            val nums = numbers.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val weight = Math.pow(0.5, Math.min(w.level, 8.0) - 1)
            for (i in bares.indices) {
                val m = Regex("([0-5])$").find(nums[i])
                val t = if (m != null) (if (m.groupValues[1] == "5") 0 else m.groupValues[1].toInt()) else 0
                val freq = toneFreq.getOrPut(bares[i]) { HashMap() }
                freq[t] = (freq[t] ?: 0.0) + weight
            }
        }
    }

    private fun buildHskSet(wordList: List<HskWord>, level: Double): Set<String> {
        val set = HashSet<String>()
        for (w in wordList) {
            if (w.level <= level + 1) {
                stripTones(w.pinyinNumbers).split(Regex("\\s+")).forEach {
                    if (it.isNotEmpty()) set.add(it)
                }
            }
        }
        return set
    }

    companion object {
        private val COMMON_SEQUENCES = listOf(
            listOf("ni", "hao"),
            listOf("zai", "jian"),
            listOf("xie", "xie"),
            listOf("bu", "ke", "qi"),
            listOf("wo", "shi"),
            listOf("ta", "shi"),
            listOf("wo", "men"),
            listOf("ni", "men"),
            listOf("yi", "qian"),
            listOf("yi", "hou"),
        )
    }
}
