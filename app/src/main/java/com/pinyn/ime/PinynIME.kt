package com.pinyn.ime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo

class PinynIME : InputMethodService() {

    lateinit var keyboard: PinynKeyboardView
    lateinit var settings: SettingsManager

    val composing = StringBuilder()
    var candidates: List<Candidate> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    override fun onEvaluateFullscreenMode(): Boolean = false
    override fun isExtractViewShown(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager(this)
        PinyinEngine.init(assets)
    }

    override fun onCreateInputView(): View {
        keyboard = PinynKeyboardView(this).apply {
            onKeyPress = { key -> handleKey(key) }
            onCandidateTap = { idx -> selectCandidate(idx) }
        }
        return keyboard
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        window.window?.apply {
            decorView?.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                x = 0
            }
        }
        composing.clear()
        candidates = emptyList()
        keyboard.hanziMode = settings.hanziMode
        keyboard.updateCandidates(candidates)
        keyboard.updateComposing("")
    }

    private fun handleKey(key: PinynKeyboardView.Key) {
        when (key) {
            PinynKeyboardView.Key.CLOSE -> requestHideSelf(0)
            PinynKeyboardView.Key.GLOBE -> switchToNextInputMethod(false)

            PinynKeyboardView.Key.BACKSPACE -> {
                if (composing.isNotEmpty()) {
                    composing.deleteCharAt(composing.lastIndex)
                    updateComposing()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }

            PinynKeyboardView.Key.ENTER -> {
                commitAndClear()
                currentInputConnection?.commitText("\n", 1)
            }

            PinynKeyboardView.Key.SPACE -> {
                if (candidates.isNotEmpty()) {
                    selectCandidate(0)
                } else {
                    commitAndClear()
                    currentInputConnection?.commitText(" ", 1)
                }
            }

            is PinynKeyboardView.Key.Candidate -> {
                selectCandidate(key.index)
            }

            is PinynKeyboardView.Key.Char -> {
                if (key.code in 'a'..'z' || key.code == '\'') {
                    composing.append(key.code)
                    updateComposing()
                } else {
                    commitAndClear()
                    currentInputConnection?.commitText(key.code.toString(), 1)
                }
            }

            PinynKeyboardView.Key.HANZI -> {
                settings.hanziMode = !settings.hanziMode
                keyboard.hanziMode = settings.hanziMode
                keyboard.invalidate()
            }

            else -> {}
        }
    }

    private fun updateComposing() {
        val raw = composing.toString()
        currentInputConnection?.setComposingText(raw, 1)
        keyboard.updateComposing(raw)

        if (raw.isNotEmpty()) {
            candidates = PinyinEngine.process(raw)
            keyboard.updateCandidates(candidates)
        } else {
            candidates = emptyList()
            keyboard.updateCandidates(candidates)
        }
    }

    private fun selectCandidate(idx: Int) {
        if (idx in candidates.indices) {
            val c = candidates[idx]
            val text = if (settings.hanziMode) c.hanzi else c.marked
            currentInputConnection?.commitText(text + " ", 1)
            composing.clear()
            candidates = emptyList()
            keyboard.updateComposing("")
            keyboard.updateCandidates(candidates)
        }
    }

    private fun commitAndClear() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing.toString(), 1)
        }
        composing.clear()
        keyboard.updateComposing("")
        candidates = emptyList()
        keyboard.updateCandidates(candidates)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val digit = keyCode - KeyEvent.KEYCODE_0
        if (digit in 1..9 && candidates.isNotEmpty()) {
            selectCandidate(digit - 1)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
