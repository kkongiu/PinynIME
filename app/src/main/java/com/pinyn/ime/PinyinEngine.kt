package com.pinyn.ime

import android.content.res.AssetManager
import android.content.res.Resources

object PinyinEngine {

    data class Analysis(
        val candidates: List<Candidate>,
        val warnings: List<String>,
    )

    @Volatile private var toneNet: ToneNet? = null
    @Volatile private var toneNetReady = false
    @Volatile private var correction: CorrectionEngine? = null
    @Volatile private var sentenceNet: SentenceNet? = null
    @Volatile private var sentenceNetReady = false
    @Volatile private var grammarLoaded = false
    @Volatile private var bareToneHanzi: Map<String, String> = emptyMap()
    @Volatile private var baseHsk: List<HskWord> = emptyList()
    @Volatile private var sentenceNetLoading = false

    private var assetsRef: AssetManager? = null

    private var desiredLevel: Double = 7.5
    private var desiredTerms: Set<String> = emptySet()
    private var appliedLevel: Double = -1.0
    private var appliedTerms: Set<String>? = null

    fun init(assets: AssetManager) {
        assetsRef = assets
        // Dizionario parole: veloce e sincrono, così il match base funziona subito.
        WordIndex.load(assets)

        // Caricamento pesante (ToneNet/HSK/POS/gloss) in background: evita l'ANR
        // al primo avvio. SentenceNet è caricato in modo pigro (ensureSentenceNet).
        Thread({
            try {
                val tn = ToneNet()
                val tnReady = tn.loadFromAssets(assets)
                val hsk = HskData.parseHsk(readAsset(assets, "data/hsk-3.0.js"))
                val bth = HskData.buildBareToneHanzi(hsk)
                val corr = CorrectionEngine(VALID_SYLLABLES, hsk).apply {
                    setTonePredictor { sylls -> tn.predictTones(sylls) }
                }
                val pos = readAsset(assets, "data/hsk-pos.js")
                val glossTxt = readAsset(assets, "data/gloss.txt")
                val reverseJs = readAsset(assets, "data/hsk-reverse.js")

                synchronized(this) {
                    toneNet = tn
                    toneNetReady = tnReady
                    correction = corr
                    GrammarRules.loadPos(pos)
                    grammarLoaded = true
                    bareToneHanzi = bth
                    HskGloss.loadGloss(glossTxt)
                    HskGloss.loadReverse(reverseJs)
                    baseHsk = hsk
                    applyToEngineLocked()
                }
                android.util.Log.d("PinyinEngine", "Loaded: ToneNet=$tnReady (SentenceNet lazy)")
            } catch (e: Exception) {
                android.util.Log.e("PinyinEngine", "background init failed", e)
            }
        }, "pinyn-engine-init").start()
    }

    /** Inizializzazione testabile da stringhe (unit test senza Android). */
    fun initData(toneNetJson: String?, hskJs: String?, sentenceNetJson: String?, posJs: String?) {
        if (toneNetJson != null) {
            toneNet = ToneNet()
            toneNetReady = toneNet!!.loadFromString(toneNetJson)
        }
        if (hskJs != null) {
            val hsk = HskData.parseHsk(hskJs)
            correction = CorrectionEngine(VALID_SYLLABLES, hsk).apply {
                setTonePredictor { sylls -> toneNet?.predictTones(sylls) }
            }
            bareToneHanzi = HskData.buildBareToneHanzi(hsk)
            baseHsk = hsk
        }
        if (sentenceNetJson != null) {
            sentenceNet = SentenceNet()
            sentenceNetReady = sentenceNet!!.loadFromString(sentenceNetJson)
        }
        if (posJs != null) {
            GrammarRules.loadPos(posJs)
            grammarLoaded = true
        }
    }

    /** Carica gloss + reverse (testabile). */
    fun initGlossData(glossTxt: String, reverseJs: String) {
        HskGloss.loadGloss(glossTxt)
        HskGloss.loadReverse(reverseJs)
    }

    /** Carica SentenceNet in modo pigro (solo quando il grammar check è attivo). */
    fun ensureSentenceNet() {
        val a = assetsRef ?: return
        synchronized(this) {
            if (sentenceNetReady || sentenceNetLoading) return
            sentenceNetLoading = true
        }
        Thread({
            try {
                val sn = SentenceNet()
                val ok = sn.loadFromAssets(a)
                synchronized(this) {
                    sentenceNet = sn
                    sentenceNetReady = ok
                }
                android.util.Log.d("PinyinEngine", "SentenceNet: $ok")
            } catch (e: Exception) {
                android.util.Log.e("PinyinEngine", "SentenceNet load failed", e)
            } finally {
                synchronized(this) { sentenceNetLoading = false }
            }
        }, "pinyn-sentencenet").start()
    }

    /** Applica livello HSK e termini personalizzati al motore. */
    fun applySettings(level: Double, terms: Set<String>) {
        synchronized(this) {
            desiredLevel = level
            desiredTerms = terms
            applyToEngineLocked()
        }
    }

    private fun applyToEngineLocked() {
        val corr = correction ?: return
        if (desiredLevel == appliedLevel && desiredTerms == appliedTerms) return
        appliedLevel = desiredLevel
        appliedTerms = desiredTerms
        corr.setLevel(desiredLevel)
        val custom = desiredTerms.mapNotNull { t ->
            val p = t.split('\t')
            if (p.size >= 2) HskWord(p[0], p[1], 0.0) else null
        }
        corr.setWords(baseHsk + custom)
        WordIndex.setCustom(custom.map { c ->
            Triple(PinyinUtils.stripTone(c.pinyinNumbers).replace(" ", ""), c.pinyinNumbers, c.word)
        })
    }

    fun reset() {
        toneNet = null
        toneNetReady = false
        correction = null
        sentenceNet = null
        sentenceNetReady = false
        grammarLoaded = false
        bareToneHanzi = emptyMap()
        baseHsk = emptyList()
        desiredLevel = 7.5
        desiredTerms = emptySet()
        appliedLevel = -1.0
        appliedTerms = null
        sentenceNetLoading = false
        WordIndex.setCustom(emptyList())
    }

    private fun readAsset(assets: AssetManager, path: String): String =
        assets.open(path).bufferedReader().use { it.readText() }

    fun process(raw: String): List<Candidate> = analyze(raw).candidates

    fun analyze(raw: String, grammarCheck: Boolean = true, resources: Resources? = null): Analysis {
        // Modalità inglese (prefisso "*"): dizionario inverso inglese -> cinese
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("*")) {
            val word = trimmed.substring(1).lowercase().filter { it in 'a'..'z' }
            if (word.isEmpty()) return Analysis(emptyList(), emptyList())
            val out = mutableListOf<Candidate>()
            HskGloss.reverseLookup(word).forEachIndexed { idx, r ->
                out.add(Candidate(r.bare, accentBare(r.bare), r.hanzi, 1000 - idx, r.gloss))
            }
            return Analysis(out, emptyList())
        }

        val clean = raw.lowercase().replace("v", "ü").replace(Regex("[^a-zü' ]"), "").trim()
        if (clean.isEmpty()) return Analysis(emptyList(), emptyList())

        val results = mutableListOf<Candidate>()
        val compact = clean.replace(" ", "")

        // 1) Whole-word exact match (dictionary with hanzi)
        if (compact.length >= 2) {
            WordIndex.pinyin(compact)?.let { wordPinyin ->
                val marked = numbersToMarks(wordPinyin)
                val hanzi = WordIndex.hanzi(compact) ?: ""
                results.add(Candidate(compact, marked, hanzi, 1000))
            }
        }

        // 2) Whole-word typo suggestions (Levenshtein vs HSK compact pinyin)
        if (compact.length >= 3 && WordIndex.pinyin(compact) == null) {
            correction?.findWordSuggestions(compact)?.take(4)?.forEach { spaced ->
                val bareCompact = PinyinUtils.stripTone(spaced).replace(" ", "")
                val markedCompact = spaced.replace(" ", "")
                val hanzi = WordIndex.hanzi(bareCompact) ?: ""
                results.add(Candidate(bareCompact, markedCompact, hanzi, 800))
            }
        }

        // 3) Segmentation + per-syllable candidates (with typo correction)
        val parts = clean.split(Regex("\\s+"))
        val allSegments = mutableListOf<String>()
        for (part in parts) {
            val seg = segment(part)
            if (seg.isNotEmpty()) allSegments.addAll(seg) else allSegments.add(part)
        }

        val predictedTones = if (toneNetReady) toneNet?.predictTones(allSegments.toTypedArray()) else null

        var prevSyl: String? = null
        for ((i, syl) in allSegments.withIndex()) {
            val predTone = predictedTones?.getOrNull(i) ?: 0

            if (syl in VALID_SYLLABLES) {
                val staticCands = CANDIDATE_MAP[syl]
                if (staticCands != null && staticCands.isNotEmpty()) {
                    val nnMarked = if (predTone > 0) addToneMark(syl, predTone) else null
                    if (nnMarked != null && staticCands.firstOrNull()?.marked != nnMarked) {
                        val existing = staticCands.firstOrNull { it.marked == nnMarked }
                        if (existing != null) results.add(existing.copy(score = 1000))
                        else results.add(Candidate(syl, nnMarked, "", 500))
                    }
                    results.addAll(staticCands)
                } else {
                    if (predTone > 0) {
                        results.add(Candidate(syl, addToneMark(syl, predTone), "", 0))
                    } else {
                        for (t in 1..4) results.add(Candidate(syl, addToneMark(syl, t), "", 0))
                    }
                }
                prevSyl = syl
            } else {
                // Typo: suggest corrections with bigram context from the last valid syllable
                val sugg = correction?.rankSuggestions(syl, prevSyl) ?: emptyList()
                for (s in sugg) {
                    val cands = CANDIDATE_MAP[s] ?: continue
                    results.addAll(cands)
                }
            }
        }

        val warnings = computeWarnings(allSegments, grammarCheck, resources)

        // Ogni candidato deve avere un hanzi: riempie dai toni HSK o scarta il candidato.
        val enriched = results.mapNotNull { c ->
            val hanzi = if (c.hanzi.isNotEmpty()) c.hanzi else (bareToneHanzi[keyFor(c)] ?: "")
            if (hanzi.isEmpty()) null
            else {
                val gloss = if (c.gloss.isNotEmpty()) c.gloss else (HskGloss.glossFor(hanzi) ?: "")
                c.copy(hanzi = hanzi, gloss = gloss)
            }
        }
        return Analysis(enriched.take(8), warnings)
    }

    private fun keyFor(c: Candidate): String {
        val tone = PinyinUtils.extractTone(c.marked).tone
        return c.compact + if (tone > 0) tone else 0
    }

    private fun accentBare(bare: String): String {
        val seg = correction?.accentRun(listOf(bare))?.firstOrNull() ?: return bare
        return seg.text
    }

    private fun computeWarnings(allSegments: List<String>, grammarCheck: Boolean, resources: Resources?): List<String> {
        val warnings = mutableListOf<String>()
        val words = correction?.segmentWords(allSegments) ?: allSegments

        // Sentence LM: flag unusual sentence structure (>= 3 words)
        if (sentenceNetReady && words.size > 2) {
            val score = sentenceNet?.scoreSentence(words)
            if (score != null && score.flagged) {
                warnings.add(str(resources, R.string.warn_sentence_unusual, "Unusual sentence structure"))
            }
        }

        // Grammar rules: conservative checks using the best hanzi guess per word
        if (grammarCheck && grammarLoaded && words.size >= 2) {
            val gw = words.map { GrammarRules.Word(it, hanziFor(it)) }
            for (note in GrammarRules.checkGrammar(gw)) warnings.add(formatNote(resources, note))
        }

        return warnings
    }

    private fun formatNote(resources: Resources?, note: GrammarRules.Note): String = when (note.kind) {
        GrammarRules.Kind.DEGREE_ADV_NOUN -> str(
            resources, R.string.grammar_degree_adv_noun, note.args[0], note.args[1],
            "\"${note.args[0]}\" before a noun (\"${note.args[1]}\"): usually ${note.args[0]} modifies an adjective or a verb.",
        )
        GrammarRules.Kind.SHI_VERB -> str(
            resources, R.string.grammar_shi_verb, note.args[0],
            "\"是\" before the verb \"${note.args[0]}\": usually the verb is used directly (\"${note.args[0]}…\").",
        )
        GrammarRules.Kind.MA_NOT_FINAL -> str(
            resources, R.string.grammar_ma_not_final,
            "\"吗\" goes at the end of the sentence.",
        )
    }

    private fun str(resources: Resources?, id: Int, default: String, vararg args: Any): String {
        return if (resources != null) resources.getString(id, *args) else default
    }

    private fun hanziFor(word: String): String {
        if (word.length >= 2) {
            WordIndex.hanzi(word)?.let { return it }
        }
        return CANDIDATE_MAP[word]?.firstOrNull { it.hanzi.isNotEmpty() }?.hanzi ?: ""
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
