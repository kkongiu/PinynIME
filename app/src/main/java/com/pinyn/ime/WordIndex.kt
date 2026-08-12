package com.pinyn.ime

import android.content.res.AssetManager

object WordIndex {
    data class Entry(val pinyin: String, val hanzi: String)

    private var pinyinMap: Map<String, String> = emptyMap()
    private var hanziMap: Map<String, String> = emptyMap()
    private var loaded = false

    fun load(assets: AssetManager) {
        if (loaded) return
        try {
            loadFromString(assets.open("data/words.txt").bufferedReader().readText())
            android.util.Log.d("WordIndex", "loaded ${pinyinMap.size} words")
        } catch (e: Exception) {
            android.util.Log.e("WordIndex", "load failed", e)
        }
    }

    /** Carica da stringa (testabile) */
    fun loadFromString(content: String) {
        val pinyin = HashMap<String, String>()
        val hanzi = HashMap<String, String>()
        for (line in content.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size >= 3) {
                pinyin[parts[0]] = parts[1]
                hanzi[parts[0]] = parts[2]
            }
        }
        pinyinMap = pinyin
        hanziMap = hanzi
        loaded = true
    }

    fun pinyin(compact: String): String? = pinyinMap[compact]

    fun hanzi(compact: String): String? = hanziMap[compact]

    fun isLoaded(): Boolean = loaded
}
