package com.pinyn.ime

/**
 * GrammarRules — verifica grammaticale base (molto conservativa).
 * Opera sulle parole segmentate di una frase pinyin: ogni parola ha {bare, hanzi}.
 * Porting nativo di `grammar-rules.js`.
 */
object GrammarRules {

    data class Word(val bare: String, val hanzi: String)
    data class Note(val index: Int, val message: String)

    private val DEGREE_ADV = setOf("很", "太", "非常", "特别", "真", "挺")

    private var pos: Map<String, String> = emptyMap()

    fun loadPos(content: String) {
        pos = HskData.parsePos(content)
    }

    fun setPos(map: Map<String, String>) {
        pos = map
    }

    /** POS solo per tag singoli non ambigui (es. "N" ok, "V/N" no). */
    fun posOf(hanzi: String): String? {
        val p = pos[hanzi] ?: return null
        return if ('/' in p) null else p
    }

    fun checkGrammar(words: List<Word>): List<Note> {
        val notes = mutableListOf<Note>()
        for (i in words.indices) {
            val w = words[i]
            val next = words.getOrNull(i + 1)

            // 1) avverbio di grado + nome (很 + N non è corretto)
            if (next != null && w.hanzi in DEGREE_ADV) {
                val np = posOf(next.hanzi)
                if (np == "N") {
                    notes.add(
                        Note(
                            i,
                            "\"${w.hanzi}\" prima di un nome (\"${next.hanzi}\"): di solito ${w.hanzi} modifica un aggettivo o un verbo.",
                        ),
                    )
                }
            }

            // 2) 是 + verbo (senza sostantivo dopo)
            if (next != null && w.hanzi == "是") {
                val vp = posOf(next.hanzi)
                val skip = setOf("的", "了", "过", "在", "有", "不", "没", "吗", "呢")
                if (vp == "V" && next.hanzi !in skip) {
                    notes.add(
                        Note(
                            i,
                            "\"是\" davanti al verbo \"${next.hanzi}\": di solito si usa il verbo direttamente (\"${next.hanzi}…\").",
                        ),
                    )
                }
            }

            // 3) 吗 non a fine frase
            if (w.hanzi == "吗" && i < words.size - 1) {
                notes.add(Note(i, "\"吗\" va alla fine della frase."))
            }
        }
        return notes
    }
}
