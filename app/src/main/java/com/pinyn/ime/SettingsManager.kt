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
}
