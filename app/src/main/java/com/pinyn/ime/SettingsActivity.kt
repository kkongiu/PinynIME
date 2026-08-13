package com.pinyn.ime

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var settings: SettingsManager
    private lateinit var termsLayout: LinearLayout

    private val levelLabels = arrayOf("1", "2", "3", "4", "5", "6", "7-9")
    private val levelValues = arrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7.5f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        addSwitch(root, getString(R.string.hanzi_mode), settings.hanziMode) { settings.hanziMode = it }
        addSwitch(root, getString(R.string.accent_mode), settings.accentMode) { settings.accentMode = it }
        addSwitch(root, getString(R.string.grammar_check), settings.grammarCheck) { settings.grammarCheck = it }
        addSwitch(root, getString(R.string.dark_mode), settings.darkMode) { settings.darkMode = it }

        addLevelSpinner(root)

        // User dictionary
        val title = TextView(this).apply {
            text = getString(R.string.user_dictionary)
            textSize = 16f
            setPadding(0, 32, 0, 8)
        }
        root.addView(title)

        termsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(termsLayout)
        refreshTerms()

        val addBtn = Button(this).apply {
            text = getString(R.string.add_word)
            setOnClickListener { showAddTermDialog() }
        }
        root.addView(addBtn)

        val about = TextView(this).apply {
            text = "\n" + getString(R.string.about)
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        root.addView(about)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun addSwitch(layout: LinearLayout, label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
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

    private fun addLevelSpinner(layout: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }
        val tv = TextView(this).apply {
            text = getString(R.string.hsk_level)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, levelLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val idx = levelValues.indexOfFirst { it == settings.hskLevel }.let { if (it >= 0) it else levelValues.size - 1 }
            setSelection(idx)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    settings.hskLevel = levelValues[position]
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        row.addView(tv)
        row.addView(spinner)
        layout.addView(row)
    }

    private fun refreshTerms() {
        termsLayout.removeAllViews()
        val terms = settings.customTerms.toList().sortedBy { it }
        if (terms.isEmpty()) {
            val empty = TextView(this).apply {
                text = "—"
                textSize = 14f
                setPadding(0, 0, 0, 8)
            }
            termsLayout.addView(empty)
            return
        }
        for (term in terms) {
            val parts = term.split('\t')
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val tv = TextView(this).apply {
                text = if (parts.size >= 2) "${parts[0]}  (${parts[1]})" else term
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = Button(this).apply {
                text = getString(R.string.delete)
                setOnClickListener {
                    settings.removeCustomTerm(term)
                    refreshTerms()
                }
            }
            row.addView(tv)
            row.addView(del)
            termsLayout.addView(row)
        }
    }

    private fun showAddTermDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val hanziEdit = EditText(this).apply { hint = getString(R.string.hanzi_label) }
        val pinyinEdit = EditText(this).apply { hint = getString(R.string.pinyin_label) }
        layout.addView(hanziEdit)
        layout.addView(pinyinEdit)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_word))
            .setView(layout)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val hanzi = hanziEdit.text.toString().trim()
                val pinyin = pinyinEdit.text.toString().trim()
                if (hanzi.isNotEmpty() && pinyin.isNotEmpty()) {
                    settings.addCustomTerm(hanzi, pinyin)
                    refreshTerms()
                } else {
                    Toast.makeText(this, "Hanzi and pinyin are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
