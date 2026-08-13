package com.pinyn.ime

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pinyn_ime", Context.MODE_PRIVATE)

    var hanziMode: Boolean
        get() = prefs.getBoolean("hanzi_mode", false)
        set(v) = prefs.edit().putBoolean("hanzi_mode", v).apply()

    var accentMode: Boolean
        get() = prefs.getBoolean("accent_mode", true)
        set(v) = prefs.edit().putBoolean("accent_mode", v).apply()

    var grammarCheck: Boolean
        get() = prefs.getBoolean("grammar_check", false)
        set(v) = prefs.edit().putBoolean("grammar_check", v).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(v) = prefs.edit().putBoolean("dark_mode", v).apply()

    /** Livello HSK: 1..6 oppure 7.5 (banda 7-9). */
    var hskLevel: Float
        get() = prefs.getFloat("hsk_level", 7.5f)
        set(v) = prefs.edit().putFloat("hsk_level", v).apply()

    /** Termini personalizzati, uno per voce nel formato "hanzi\tpinyin_numbers". */
    var customTerms: Set<String>
        get() = prefs.getStringSet("custom_terms", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("custom_terms", v).apply()

    fun addCustomTerm(hanzi: String, pinyinNumbers: String) {
        val h = hanzi.trim()
        val p = pinyinNumbers.trim().lowercase()
        if (h.isEmpty() || p.isEmpty()) return
        customTerms = customTerms + ("$h\t$p")
    }

    fun removeCustomTerm(term: String) {
        customTerms = customTerms - term
    }
}
