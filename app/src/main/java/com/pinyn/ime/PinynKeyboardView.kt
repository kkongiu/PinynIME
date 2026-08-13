package com.pinyn.ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View

class PinynKeyboardView(context: Context) : View(context) {

    sealed class Key {
        data class Char(val code: kotlin.Char) : Key()
        data class Candidate(val index: Int) : Key()
        data object SPACE : Key()
        data object ENTER : Key()
        data object BACKSPACE : Key()
        data object GLOBE : Key()
        data object CLOSE : Key()
        data object HANZI : Key()
        data object SYMBOL : Key()
        data object SYMBOL_SHIFT : Key()
        data object SETTINGS : Key()
    }

    var onKeyPress: ((Key) -> Unit)? = null
    var onCandidateTap: ((Int) -> Unit)? = null
    var hanziMode = false
    var symbolMode = false
    var symbolPage2 = false
    var darkMode = true
        set(v) {
            field = v
            applyTheme()
            invalidate()
        }

    private var candidates: List<Candidate> = emptyList()
    private var composing: String = ""
    private var warnings: List<String> = emptyList()

    private val keyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spaceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kbBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val candidatePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 36f
        typeface = Typeface.DEFAULT
    }

    private val candidateBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 230, 240)
    }

    private val candidateSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 160, 240)
    }

    private val composingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
    }

    private val warningPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 100, 30)
        textSize = 26f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
    }

    private val warningBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 244, 224)
    }

    init {
        applyTheme()
    }

    private fun applyTheme() {
        if (darkMode) {
            keyPaint.color = Color.WHITE
            keyBgPaint.color = Color.rgb(60, 60, 70)
            specialBgPaint.color = Color.rgb(80, 80, 90)
            spaceBgPaint.color = Color.rgb(50, 50, 60)
            topBarBgPaint.color = Color.rgb(30, 30, 40)
            kbBgPaint.color = Color.rgb(30, 30, 40)
            composingPaint.color = Color.WHITE
        } else {
            keyPaint.color = Color.rgb(30, 30, 40)
            keyBgPaint.color = Color.rgb(230, 230, 235)
            specialBgPaint.color = Color.rgb(205, 205, 215)
            spaceBgPaint.color = Color.rgb(215, 215, 225)
            topBarBgPaint.color = Color.rgb(240, 240, 245)
            kbBgPaint.color = Color.rgb(240, 240, 245)
            composingPaint.color = Color.rgb(30, 30, 40)
        }
    }

    private val letterRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m", "⌫"),
        listOf("123", ",", "SPACE", ".", "字", "↵"),
    )

    private val symbolRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("*", "-", "/", ":", ";", "(", ")", "$", "&", "@"),
        listOf("#+=", "_", "?", "!", "'", "\"", "⌫"),
        listOf("abc", "⚙", ",", "SPACE", ".", "字", "↵"),
    )

    private val symbolRows2 = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        listOf("123", "_", "?", "!", "'", "\"", "⌫"),
        listOf("abc", "⚙", ",", "SPACE", ".", "字", "↵"),
    )

    private val keyRows: List<List<String>> get() = when {
        !symbolMode -> letterRows
        symbolPage2 -> symbolRows2
        else -> symbolRows
    }

    private val keyRects = mutableListOf<Triple<RectF, Key, String>>()

    private val candidateHeight = 100f
    private val topBarHeight = 40f
    private val warningBarHeight = 32f
    private val keyVerticalGap = 6f
    private val keyHorizontalGap = 6f
    private val cornerRadius = 8f

    private val REPEAT_INITIAL_DELAY = 400L
    private val REPEAT_INTERVAL = 50L
    private val LONG_PRESS_DELAY = 500L

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenH = resources.displayMetrics.heightPixels
        val desiredH = (screenH * 0.42).toInt()
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(desiredH, MeasureSpec.EXACTLY)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        keyRects.clear()

        // Top bar: composing text
        canvas.drawRect(0f, 0f, w, topBarHeight, topBarBgPaint)
        if (composing.isNotEmpty()) {
            canvas.drawText(composing, 16f, topBarHeight - 10f, composingPaint)
        }

        // Candidate bar
        val candY = topBarHeight
        canvas.drawRect(0f, candY, w, candY + candidateHeight, candidateBgPaint)
        drawCandidates(canvas, candY, w)

        // Warning strip (grammar / sentence LM), only when present
        val warnH = if (warnings.isNotEmpty()) warningBarHeight else 0f
        if (warnings.isNotEmpty()) {
            val warnTop = candY + candidateHeight
            canvas.drawRect(0f, warnTop, w, warnTop + warningBarHeight, warningBgPaint)
            val text = warnings.joinToString("  •  ")
            canvas.drawText(text, 16f, warnTop + warningBarHeight - 8f, warningPaint)
        }

        // Keyboard background
        val kbTop = candY + candidateHeight + warnH
        canvas.drawRect(0f, kbTop, w, h, kbBgPaint)

        // Draw keys
        val usableW = w
        val usableRows = keyRows
        val totalRows = usableRows.size
        val totalGapV = keyVerticalGap * (totalRows + 1)
        val keyH = (h - kbTop - totalGapV) / totalRows

        for ((rowIdx, row) in usableRows.withIndex()) {
            val units = row.sumOf { widthFactor(it).toDouble() }.toFloat()
            val totalGapH = keyHorizontalGap * (row.size + 1)
            val keyW = (usableW - totalGapH) / units
            val rowTop = kbTop + keyVerticalGap + rowIdx * (keyH + keyVerticalGap)

            var x = keyHorizontalGap
            for (label in row) {
                val keyWidth = keyW * widthFactor(label)
                val rect = RectF(x, rowTop, x + keyWidth, rowTop + keyH)
                drawKey(canvas, rect, label)
                val key = labelToKey(label)
                keyRects.add(Triple(rect, key, label))
                x += keyWidth + keyHorizontalGap
            }
        }
    }

    private fun widthFactor(label: String): Float = when (label) {
        "SPACE" -> 3.5f
        "⌫", "↵" -> 1.8f
        "123", "abc", "⚙", "#+=" -> 1.5f
        else -> 1f
    }

    private fun labelToKey(label: String): Key = when (label) {
        "SPACE" -> Key.SPACE
        "↵" -> Key.ENTER
        "⌫" -> Key.BACKSPACE
        "字" -> Key.HANZI
        "⚙" -> Key.SETTINGS
        "#+=" -> Key.SYMBOL_SHIFT
        "123" -> if (symbolMode && symbolPage2) Key.SYMBOL_SHIFT else Key.SYMBOL
        "abc" -> Key.SYMBOL
        else -> if (label.length == 1) Key.Char(label[0]) else Key.CLOSE
    }

    private fun drawKey(canvas: Canvas, rect: RectF, label: String) {
        val bg = when (label) {
            "SPACE" -> spaceBgPaint
            "⌫", "↵", "123", "abc", "⚙", "#+=" -> specialBgPaint
            else -> keyBgPaint
        }
        val effectiveBg = if (label == "字" && hanziMode) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 153, 255) }
        } else bg
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, effectiveBg)
        keyPaint.textSize = when (label) { "⌫" -> 30f; "↵" -> 30f; "123" -> 22f; "abc" -> 22f; "⚙" -> 26f; "#+=" -> 20f; "字" -> 22f; else -> 40f }
        canvas.drawText(label, rect.centerX(), rect.centerY() + keyPaint.textSize / 3, keyPaint)
    }

    private fun drawCandidates(canvas: Canvas, top: Float, width: Float) {
        if (candidates.isEmpty()) return
        val pad = 12f
        val gap = 8f
        val itemW = (width - pad * 2 - gap * (candidates.size - 1)) / candidates.size.coerceAtLeast(1)
        var x = pad
        for ((i, c) in candidates.withIndex()) {
            val rect = RectF(x, top + 8f, x + itemW, top + candidateHeight - 8f)
            val bg = if (i == 0) candidateSelectedBgPaint else
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(240, 240, 250) }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bg)
            candidatePaint.textSize = 30f
            candidatePaint.color = Color.BLACK
            val label = if (c.hanzi.isNotEmpty()) "${c.hanzi} ${c.marked}" else c.marked
            if (c.hanzi.isNotEmpty()) {
                candidatePaint.textSize = 34f
                candidatePaint.color = Color.rgb(20, 20, 30)
                canvas.drawText(c.hanzi, rect.centerX(), rect.centerY() - 14f, candidatePaint)
                candidatePaint.textSize = 20f
                candidatePaint.color = Color.GRAY
                canvas.drawText(c.marked, rect.centerX(), rect.centerY() + 12f, candidatePaint)
                if (c.gloss.isNotEmpty()) {
                    candidatePaint.textSize = 15f
                    candidatePaint.color = Color.rgb(120, 120, 130)
                    canvas.drawText(shortGloss(c.gloss), rect.centerX(), rect.centerY() + 34f, candidatePaint)
                }
            } else {
                candidatePaint.textSize = 32f
                candidatePaint.color = Color.rgb(20, 20, 30)
                canvas.drawText(c.marked, rect.centerX(), rect.centerY() + 10f, candidatePaint)
            }
            x += itemW + gap
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                // Check candidate taps
                if (y < topBarHeight + candidateHeight && candidates.isNotEmpty()) {
                    val idx = findCandidateIndex(x, width.toFloat())
                    if (idx >= 0) {
                        onCandidateTap?.invoke(idx)
                        return true
                    }
                }
                // Check key taps
                for ((rect, key, _) in keyRects) {
                    if (rect.contains(x, y)) {
                        if (key == Key.SPACE) {
                            spaceDown = true
                            spaceLongPressed = false
                            spaceRunnable?.let { repeatHandler.removeCallbacks(it) }
                            spaceRunnable = Runnable {
                                spaceLongPressed = true
                                onGlobePress?.invoke()
                            }
                            repeatHandler.postDelayed(spaceRunnable!!, LONG_PRESS_DELAY)
                        } else {
                            onKeyPress?.invoke(key)
                            if (key == Key.BACKSPACE) startRepeat(key)
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (spaceDown && !spaceLongPressed) {
                    onKeyPress?.invoke(Key.SPACE)
                }
                spaceDown = false
                spaceRunnable?.let { repeatHandler.removeCallbacks(it) }
                spaceRunnable = null
                stopRepeat()
            }
        }
        return true
    }

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatingKey: Key? = null
    private var repeatRunnable: Runnable? = null
    private var spaceRunnable: Runnable? = null
    private var spaceDown = false
    private var spaceLongPressed = false

    var onGlobePress: (() -> Unit)? = null

    private fun startRepeat(key: Key) {
        stopRepeat()
        repeatingKey = key
        repeatRunnable = object : Runnable {
            override fun run() {
                if (repeatingKey != null) {
                    onKeyPress?.invoke(repeatingKey!!)
                    repeatHandler.postDelayed(this, REPEAT_INTERVAL)
                }
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, REPEAT_INITIAL_DELAY)
    }

    private fun stopRepeat() {
        repeatingKey = null
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRepeat()
    }

    private fun findCandidateIndex(x: Float, width: Float): Int {
        if (candidates.isEmpty()) return -1
        val pad = 12f
        val gap = 8f
        val itemW = (width - pad * 2 - gap * (candidates.size - 1)) / candidates.size
        for (i in candidates.indices) {
            val left = pad + i * (itemW + gap)
            if (x in left..(left + itemW)) return i
        }
        return -1
    }

    fun updateCandidates(list: List<Candidate>) {
        candidates = list
        invalidate()
    }

    fun updateWarnings(list: List<String>) {
        warnings = list
        invalidate()
    }

    fun updateComposing(text: String) {
        composing = text
        invalidate()
    }

    private fun shortGloss(g: String): String {
        val max = 16
        return if (g.length <= max) g else g.substring(0, max - 1) + "…"
    }
}
