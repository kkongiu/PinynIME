package com.pinyn.ime

data class HskWord(
    val word: String,
    val pinyinNumbers: String,
    val level: Double,
    val rank: Int = 999999,
)

object HskData {

    /** Parsa `window.__PINYN_HSK = [{"w","p","l"},...]` in una lista di HskWord. */
    fun parseHsk(content: String): List<HskWord> {
        val start = content.indexOf('[')
        if (start < 0) return emptyList()
        var depth = 0
        var end = -1
        for (i in start until content.length) {
            when (content[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }
        if (end < 0) return emptyList()
        val arr = org.json.JSONArray(content.substring(start, end))
        val out = ArrayList<HskWord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(HskWord(o.getString("w"), o.getString("p"), o.getDouble("l")))
        }
        return out
    }

    /** Parsa `window.__PINYN_POS = {"hanzi":"POS",...}` in una mappa. */
    fun parsePos(content: String): Map<String, String> {
        val start = content.indexOf('{')
        if (start < 0) return emptyMap()
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
        if (end < 0) return emptyMap()
        val root = org.json.JSONObject(content.substring(start, end))
        val out = HashMap<String, String>(root.length())
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = root.getString(k)
        }
        return out
    }

    /**
     * Mappa (sillaba bare + tono) -> hanzi singolo più comune (livello più basso).
     * Es. "shi4" -> "是", "shi2" -> "十". Serve a dare un hanzi ai candidati
     * senza carattere nel CANDIDATE_MAP statico.
     */
    fun buildBareToneHanzi(words: List<HskWord>): Map<String, String> {
        val map = HashMap<String, String>()
        for (w in words) {
            if (w.word.length != 1) continue
            val nums = w.pinyinNumbers.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (nums.size != 1) continue
            val m = Regex("^([a-zü]+)([0-5])$").find(nums[0]) ?: continue
            val tone = if (m.groupValues[2] == "5") "0" else m.groupValues[2]
            map.putIfAbsent(m.groupValues[1] + tone, w.word)
        }
        BARE_TONE_OVERRIDE.forEach { (k, v) -> map.putIfAbsent(k, v) }
        return map
    }

    /** Sillabe rare senza parola monosillaba in HSK: hanzi manuale (fill-in). */
    private val BARE_TONE_OVERRIDE: Map<String, String> = mapOf(
        "ang2" to "昂",
        "bin1" to "宾",
        "dei3" to "得",
        "en1" to "恩",
        "fou3" to "否",
        "ga4" to "尬",
        "ha1" to "哈",
        "hang2" to "行",
        "jiong3" to "囧",
        "me0" to "么",
        "min2" to "民",
        "min3" to "敏",
        "miu4" to "谬",
        "nang2" to "囊",
        "nu2" to "奴",
        "nu3" to "努",
        "nu4" to "怒",
        "nüe4" to "虐",
        "ou1" to "欧",
        "ou3" to "偶",
        "pou1" to "剖",
        "sang1" to "桑",
        "sang3" to "嗓",
        "sang4" to "丧",
        "sen1" to "森",
        "seng1" to "僧",
        "shei2" to "谁",
        "weng1" to "翁",
        "zen3" to "怎",
        "zun1" to "尊",
    )
}
