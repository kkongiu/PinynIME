package com.pinyn.ime

/**
 * Utilità pinyin: normalizzazione toni (numeri <-> diacritici), tokenizzazione
 * e conversione. Porting nativo di `pinyin-utils.js`.
 */
object PinyinUtils {

    data class Token(val text: String, val isLatin: Boolean)
    data class ToneResult(val base: String, val tone: Int)

    private val BASE_VOWELS = charArrayOf('a', 'e', 'i', 'o', 'u', 'ü')

    private val TONED: Map<Char, Pair<Char, Int>> = mapOf(
        'ā' to ('a' to 1), 'á' to ('a' to 2), 'ǎ' to ('a' to 3), 'à' to ('a' to 4),
        'ē' to ('e' to 1), 'é' to ('e' to 2), 'ě' to ('e' to 3), 'è' to ('e' to 4),
        'ī' to ('i' to 1), 'í' to ('i' to 2), 'ǐ' to ('i' to 3), 'ì' to ('i' to 4),
        'ō' to ('o' to 1), 'ó' to ('o' to 2), 'ǒ' to ('o' to 3), 'ò' to ('o' to 4),
        'ū' to ('u' to 1), 'ú' to ('u' to 2), 'ǔ' to ('u' to 3), 'ù' to ('u' to 4),
        'ǖ' to ('ü' to 1), 'ǘ' to ('ü' to 2), 'ǚ' to ('ü' to 3), 'ǜ' to ('ü' to 4),
    )

    private val MARK: Map<Int, Map<Char, Char>> = mapOf(
        1 to mapOf('a' to 'ā', 'e' to 'ē', 'i' to 'ī', 'o' to 'ō', 'u' to 'ū', 'ü' to 'ǖ'),
        2 to mapOf('a' to 'á', 'e' to 'é', 'i' to 'í', 'o' to 'ó', 'u' to 'ú', 'ü' to 'ǘ'),
        3 to mapOf('a' to 'ǎ', 'e' to 'ě', 'i' to 'ǐ', 'o' to 'ǒ', 'u' to 'ǔ', 'ü' to 'ǚ'),
        4 to mapOf('a' to 'à', 'e' to 'è', 'i' to 'ì', 'o' to 'ò', 'u' to 'ù', 'ü' to 'ǜ'),
    )

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    /** Ordine di collocazione del tono (regola pinyin). */
    fun toneIndex(syllable: String): Int {
        for (v in charArrayOf('a', 'o', 'e')) {
            val idx = syllable.indexOf(v)
            if (idx != -1) return idx
        }
        val iu = syllable.indexOf("iu")
        if (iu != -1) return iu + 1
        val ui = syllable.indexOf("ui")
        if (ui != -1) return ui + 1
        for (v in charArrayOf('i', 'u', 'ü')) {
            val idx = syllable.indexOf(v)
            if (idx != -1) return idx
        }
        return -1
    }

    /** "nǐ" -> base "ni", tono 3; "ni3" -> base "ni", tono 3. */
    fun extractTone(syllable: String): ToneResult {
        if (syllable.isEmpty()) return ToneResult("", 0)
        val s = normalize(syllable.lowercase())
        for (ch in s) {
            val t = TONED[ch]
            if (t != null) {
                val base = normalizeVowelV(s.replace(ch.toString(), t.first.toString()))
                return ToneResult(base, t.second)
            }
        }
        val m = Regex("(\\d)$").find(s)
        if (m != null) return ToneResult(normalizeVowelV(s.dropLast(1)), m.groupValues[1].toInt())
        return ToneResult(normalizeVowelV(s), 0)
    }

    /** In pinyin, la "v" da tastiera corrisponde a "ü" (lv -> lü). */
    fun normalizeVowelV(s: String): String = s.replace("v", "ü")

    /** "ni" + 3 -> "nǐ". */
    fun addToneMark(base: String, tone: Int): String {
        val b = normalizeVowelV(normalize(base.lowercase()))
        if (tone < 1 || tone > 4 || !bodyHasVowel(b)) return b
        val idx = toneIndex(b)
        if (idx == -1) return b
        val ch = b[idx]
        val replacement = MARK[tone]?.get(ch) ?: ch
        return b.substring(0, idx) + replacement + b.substring(idx + 1)
    }

    private fun bodyHasVowel(s: String): Boolean = BASE_VOWELS.any { s.contains(it) }

    /** "nǐ" -> "ni3". */
    fun toToneNumber(syllable: String): String {
        val r = extractTone(syllable)
        return if (r.tone > 0) r.base + r.tone else r.base
    }

    /** "ni3" -> "nǐ". */
    fun toToneMark(syllable: String): String {
        val r = extractTone(syllable)
        return addToneMark(r.base, r.tone)
    }

    /** Rimuove TUTTI i diacritici di tono (e i numeri): "nǐ hǎo" -> "ni hao". */
    fun stripTone(syllable: String): String {
        if (syllable.isEmpty()) return ""
        var s = normalizeVowelV(normalize(syllable.lowercase()))
        s = s.replace(Regex("[āáǎà]"), "a")
            .replace(Regex("[ēéěè]"), "e")
            .replace(Regex("[īíǐì]"), "i")
            .replace(Regex("[ōóǒò]"), "o")
            .replace(Regex("[ūúǔù]"), "u")
            .replace(Regex("[ǖǘǚǜ]"), "ü")
            .replace(Regex("[0-5]"), "")
        return s
    }

    /** Un token è un candidato pinyin? (solo segmento latino/ü) */
    fun isPinyinToken(token: String): Boolean =
        Regex("^[a-zA-Zü]+$").matches(token) && token.isNotEmpty()

    /** true se il token è un pinyin in formato "hao3" (base + cifra tonale). */
    fun isToneNumbered(syllable: String): Boolean =
        Regex("^[a-zü]+[0-5]$", RegexOption.IGNORE_CASE).matches(syllable)

    /** true se il token contiene già un diacritico di tono (āáǎà…). */
    fun hasToneMark(syllable: String): Boolean =
        Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]", RegexOption.IGNORE_CASE).containsMatchIn(syllable)

    private val TOKEN_RE = Regex(
        "[a-zA-Züōóǒòāáǎàēéěèīíǐìūúǔùǖǘǚǜ]+[0-5]?|[^a-zA-Züōóǒòāáǎàēéěèīíǐìūúǔùǖǘǚǜ]+",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Suddivide il testo in token preservando il testo non-latino.
     * Una cifra tonale (0-5) finale è parte del token pinyin ("hao3").
     */
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        for (m in TOKEN_RE.findAll(text)) {
            val value = m.value
            tokens.add(Token(value.lowercase(), Regex("[a-zA-Zü]").containsMatchIn(value)))
        }
        return tokens
    }

    /**
     * Converte i toni numerici del testo in diacritici, lasciando intatto il resto.
     * "ni3 hao3" -> "nǐ hǎo"; "ma0" -> "ma".
     */
    fun convertToneNumbers(text: String): String {
        val re = Regex("([a-zA-Zü]+)([0-5])\\b", RegexOption.IGNORE_CASE)
        return re.replace(text) { m ->
            val tone = m.groupValues[2].toInt()
            if (tone in 1..4) addToneMark(m.groupValues[1], tone) else m.groupValues[1]
        }
    }
}
