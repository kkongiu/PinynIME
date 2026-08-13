package com.pinyn.ime

/**
 * Gloss inglesi + dizionario inverso (inglese -> cinese).
 * Dati da `gloss.txt` (hanzi / bare / gloss) e `hsk-reverse.js` (token -> bare).
 */
object HskGloss {

    data class ReverseResult(val bare: String, val hanzi: String, val gloss: String)

    private var hanziGloss: Map<String, String> = emptyMap()
    private var bareHanzi: Map<String, String> = emptyMap()
    private var reverse: Map<String, List<String>> = emptyMap()

    /** Parsa `gloss.txt`: linee `hanzi \t bare \t gloss`. */
    fun loadGloss(content: String) {
        val hg = HashMap<String, String>()
        val bh = HashMap<String, String>()
        for (line in content.lineSequence()) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val hanzi = parts[0]
            val bare = parts[1]
            val gloss = parts[2]
            hg.putIfAbsent(hanzi, gloss)
            bh.putIfAbsent(bare, hanzi)
        }
        hanziGloss = hg
        bareHanzi = bh
    }

    /** Parsa `window.__PINYN_REVERSE = { "token": [bare...], ... }`. */
    fun loadReverse(content: String) {
        val start = content.indexOf('{')
        if (start < 0) return
        var depth = 0
        var end = -1
        for (i in start until content.length) {
            when (content[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }
        if (end < 0) return
        val root = org.json.JSONObject(content.substring(start, end))
        val map = HashMap<String, List<String>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = root.getJSONArray(k)
            map[k] = (0 until arr.length()).map { arr.getString(it) }
        }
        reverse = map
    }

    fun glossFor(hanzi: String): String? = hanziGloss[hanzi]

    fun isLoaded(): Boolean = hanziGloss.isNotEmpty()

    /** Ricerca inversa per token inglese (match esatto, ordine per livello HSK). */
    fun reverseLookup(token: String): List<ReverseResult> {
        val t = token.lowercase().trim()
        if (t.isEmpty()) return emptyList()
        val bares = reverse[t] ?: return emptyList()
        val out = mutableListOf<ReverseResult>()
        for (b in bares) {
            val hanzi = bareHanzi[b] ?: continue
            val gloss = hanziGloss[hanzi] ?: ""
            out.add(ReverseResult(b, hanzi, gloss))
        }
        return out
    }
}
