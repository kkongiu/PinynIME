package com.pinyn.ime

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = SettingsManager(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        addSwitch(layout, "Convert to Hanzi (show characters)", settings.hanziMode) {
            settings.hanziMode = it
        }
        addSwitch(layout, "Auto Accents (add tone marks)", settings.accentMode) {
            settings.accentMode = it
        }
        addSwitch(layout, "Grammar Check", settings.grammarCheck) {
            settings.grammarCheck = it
        }

        val about = TextView(this).apply {
            text = "\nPinyn IME v0.15 — Pinyin correction keyboard\n\n" +
                   "Select Pinyn Keyboard in:\n" +
                   "Settings → System → Languages & Input → Virtual Keyboard\n\n" +
                   "Then switch to it with the globe key or from the keyboard switcher."
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(about)
        setContentView(layout)
    }

    private fun addSwitch(layout: LinearLayout, label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(tv)
        row.addView(sw)
        layout.addView(row)
    }
}
