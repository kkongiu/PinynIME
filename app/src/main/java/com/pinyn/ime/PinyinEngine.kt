package com.pinyn.ime

object PinyinEngine {

    private var toneNet: ToneNet? = null
    private var toneNetReady = false

    fun init(assets: android.content.res.AssetManager) {
        toneNet = ToneNet()
        toneNetReady = toneNet!!.loadFromAssets(assets)
        WordIndex.load(assets)
        android.util.Log.d("PinyinEngine", "ToneNet ready: $toneNetReady")
    }

    fun process(raw: String): List<Candidate> {
        val clean = raw.lowercase().replace(Regex("[^a-z' ]"), "").trim()
        if (clean.isEmpty()) return emptyList()

        val results = mutableListOf<Candidate>()

        // 1) Match parola multi-sillaba (frase compatta senza spazi)
        val compact = clean.replace(" ", "")
        if (compact.length >= 2) {
            val wordPinyin = WordIndex.pinyin(compact)
            if (wordPinyin != null) {
                val marked = numbersToMarks(wordPinyin)
                val hanzi = WordIndex.hanzi(compact) ?: ""
                results.add(Candidate(compact, marked, hanzi, 1000))
            }
        }

        // 2) Segmentazione in sillabe
        val parts = clean.split(Regex("\\s+"))
        val allSegments = mutableListOf<String>()
        for (part in parts) {
            val seg = segment(part)
            if (seg.isNotEmpty()) allSegments.addAll(seg)
            else allSegments.add(part)
        }

        // 3) Toni predetti dalla rete (contesto)
        val predictedTones: IntArray? = if (toneNetReady) {
            toneNet?.predictTones(allSegments.toTypedArray())
        } else null

        // 4) Candidati per sillaba (ponderati per livello HSK)
        for ((i, syl) in allSegments.withIndex()) {
            val staticCands = CANDIDATE_MAP[syl]
            if (staticCands != null && staticCands.isNotEmpty()) {
                val predTone = predictedTones?.getOrNull(i) ?: 0
                val nnMarked = if (predTone > 0) addToneMark(syl, predTone) else null
                // Metti in testa il tono della rete se diverso dal primo statico
                if (nnMarked != null && staticCands.firstOrNull()?.marked != nnMarked) {
                    val existing = staticCands.firstOrNull { it.marked == nnMarked }
                    if (existing != null) {
                        results.add(existing.copy(score = 1000))
                    } else {
                        results.add(Candidate(syl, nnMarked, "", 500))
                    }
                }
                results.addAll(staticCands)
            } else {
                val predTone = predictedTones?.getOrNull(i) ?: 0
                if (predTone > 0) {
                    results.add(Candidate(syl, addToneMark(syl, predTone), "", 0))
                } else {
                    for (t in 1..4) results.add(Candidate(syl, addToneMark(syl, t), "", 0))
                }
            }
        }

        return results.take(8)
    }

    /** Segmenta pinyin compatto in sillabe valide (DP longest-first) */
    fun segment(bare: String): List<String> {
        val n = bare.length
        if (n == 0) return emptyList()

        val dp = BooleanArray(n + 1) { false }
        val prev = IntArray(n + 1) { -1 }
        dp[0] = true

        for (i in 0 until n) {
            if (!dp[i]) continue
            for (len in 6 downTo 1) {
                val j = i + len
                if (j > n) continue
                val sub = bare.substring(i, j)
                if (sub in VALID_SYLLABLES) {
                    dp[j] = true
                    prev[j] = i
                    break
                }
            }
        }

        if (!dp[n]) return listOf(bare)

        val result = mutableListOf<String>()
        var pos = n
        while (pos > 0) {
            val start = prev[pos]
            result.add(0, bare.substring(start, pos))
            pos = start
        }
        return result
    }

    /** Converte "ni3 hao3" in "nǐ hǎo" */
    fun numbersToMarks(numbers: String): String {
        return numbers.split(Regex("\\s+")).joinToString("") { part ->
            val tone = part.lastOrNull()?.takeIf { it in '1'..'4' }?.digitToInt() ?: 0
            val bare = if (tone > 0) part.dropLast(1) else part
            addToneMark(bare, tone)
        }
    }

    /** Aggiunge il diacritico del tono a una sillaba bare */
    fun addToneMark(bare: String, tone: Int): String {
        if (tone == 0 || tone == 5) return bare
        val toneChar = when (tone) {
            1 -> '\u0304'
            2 -> '\u0301'
            3 -> '\u030c'
            4 -> '\u0300'
            else -> return bare
        }
        val chars = bare.toCharArray()
        for (i in chars.indices) {
            if (chars[i].lowercaseChar() == 'a') {
                return normalize(bare.substring(0, i) + chars[i] + toneChar + bare.substring(i + 1))
            }
        }
        for (i in chars.indices) {
            if (chars[i].lowercaseChar() in "eo") {
                return normalize(bare.substring(0, i) + chars[i] + toneChar + bare.substring(i + 1))
            }
        }
        for (i in chars.indices.reversed()) {
            if (chars[i].lowercaseChar() in "iuü") {
                return normalize(bare.substring(0, i) + chars[i] + toneChar + bare.substring(i + 1))
            }
        }
        return bare + tone.toString()
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
}
