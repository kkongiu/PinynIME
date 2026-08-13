package com.pinyn.ime

import android.content.res.AssetManager

object WordIndex {
    data class Entry(val pinyin: String, val hanzi: String)

    private var pinyinMap: Map<String, String> = emptyMap()
    private var hanziMap: Map<String, String> = emptyMap()
    private var customPinyin: Map<String, String> = emptyMap()
    private var customHanzi: Map<String, String> = emptyMap()
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

    fun pinyin(compact: String): String? = customPinyin[compact] ?: pinyinMap[compact]

    fun hanzi(compact: String): String? = customHanzi[compact] ?: hanziMap[compact]

    fun isLoaded(): Boolean = loaded

    /** Sostituisce i termini personalizzati (compact, pinyin_numbers, hanzi). */
    fun setCustom(entries: List<Triple<String, String, String>>) {
        customPinyin = entries.associate { it.first to it.second }
        customHanzi = entries.associate { it.first to it.third }
    }
}
