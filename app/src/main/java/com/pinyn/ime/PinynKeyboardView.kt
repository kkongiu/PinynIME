package com.pinyn.ime

import android.content.Context
import android.graphics.*
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
    }

    var onKeyPress: ((Key) -> Unit)? = null
    var onCandidateTap: ((Int) -> Unit)? = null
    var hanziMode = false

    private var candidates: List<Candidate> = emptyList()
    private var composing: String = ""

    private val keyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 60, 70)
    }

    private val specialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 80, 90)
    }

    private val spaceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(50, 50, 60)
    }

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
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
    }

    val keyRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "⌫"),
        listOf("123", "⇄", ",", "SPACE", ".", "字", "↵")
    )

    private val keyRects = mutableListOf<Triple<RectF, Key, String>>()

    private val candidateHeight = 100f
    private val topBarHeight = 40f
    private val keyVerticalGap = 6f
    private val keyHorizontalGap = 6f
    private val cornerRadius = 8f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenH = resources.displayMetrics.heightPixels
        val desiredH = (screenH * 0.45).toInt()
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(desiredH, MeasureSpec.EXACTLY)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val rightMargin = w * 0.12f  // margin only on right side (system inset)

        keyRects.clear()

        // Top bar: composing text
        canvas.drawRect(0f, 0f, w, topBarHeight, Paint().apply { color = Color.rgb(30, 30, 40) })
        if (composing.isNotEmpty()) {
            canvas.drawText(composing, 16f, topBarHeight - 10f, composingPaint)
        }

        // Candidate bar
        val candY = topBarHeight
        canvas.drawRect(0f, candY, w, candY + candidateHeight, candidateBgPaint)
        drawCandidates(canvas, candY, w - rightMargin)

        // Keyboard background
        val kbTop = candY + candidateHeight
        canvas.drawRect(0f, kbTop, w, h, Paint().apply { color = Color.rgb(30, 30, 40) })

        // Draw keys (left-aligned, right margin only)
        val usableW = w - rightMargin
        val usableRows = keyRows
        val totalRows = usableRows.size
        val totalGapV = keyVerticalGap * (totalRows + 1)
        val keyH = (h - kbTop - totalGapV) / totalRows

        for ((rowIdx, row) in usableRows.withIndex()) {
            val n = row.size
            val totalGapH = keyHorizontalGap * (n + 1)
            val keyW = (usableW - totalGapH) / n
            val rowTop = kbTop + keyVerticalGap + rowIdx * (keyH + keyVerticalGap)

            var x = keyHorizontalGap
            for ((colIdx, label) in row.withIndex()) {
                val keyWidth = when (label) {
                    "SPACE" -> keyW * 3 + keyHorizontalGap * 2
                    "SHIFT", "⌫", "↵" -> keyW * 1.5f + keyHorizontalGap * 0.5f
                    else -> keyW
                }

                val rect = RectF(x, rowTop, x + keyWidth, rowTop + keyH)
                drawKey(canvas, rect, label)
                val key = labelToKey(label)
                keyRects.add(Triple(rect, key, label))
                x += keyWidth + keyHorizontalGap
            }
        }
    }

    private fun labelToKey(label: String): Key = when (label) {
        "SPACE" -> Key.SPACE
        "↵" -> Key.ENTER
        "⌫" -> Key.BACKSPACE
        "⇄" -> Key.GLOBE
        "字" -> Key.HANZI
        "SHIFT", "123" -> Key.CLOSE
        "," -> Key.Char(',')
        "." -> Key.Char('.')
        "'" -> Key.Char('\'')
        else -> if (label.length == 1 && label[0] in 'a'..'z') Key.Char(label[0]) else Key.CLOSE
    }

    private fun drawKey(canvas: Canvas, rect: RectF, label: String) {
        val bg = when (label) {
            "SPACE" -> spaceBgPaint
            "⌫", "↵", "SHIFT", "123", "⇄" -> specialBgPaint
            else -> keyBgPaint
        }
        val effectiveBg = if (label == "字" && hanziMode) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 153, 255) }
        } else bg
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, effectiveBg)
        keyPaint.textSize = when (label) { "⌫" -> 30f; "↵" -> 30f; "⇄" -> 32f; "123" -> 24f; "SHIFT" -> 22f; "字" -> 22f; else -> 40f }
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
                canvas.drawText(c.hanzi, rect.centerX(), rect.centerY() - 6f, candidatePaint)
                candidatePaint.textSize = 22f
                candidatePaint.color = Color.GRAY
                canvas.drawText(c.marked, rect.centerX(), rect.centerY() + 24f, candidatePaint)
            } else {
                candidatePaint.textSize = 32f
                candidatePaint.color = Color.rgb(20, 20, 30)
                canvas.drawText(c.marked, rect.centerX(), rect.centerY() + 10f, candidatePaint)
            }
            x += itemW + gap
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
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
                    onKeyPress?.invoke(key)
                    return true
                }
            }
        }
        return true
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

    fun updateComposing(text: String) {
        composing = text
        invalidate()
    }
}
