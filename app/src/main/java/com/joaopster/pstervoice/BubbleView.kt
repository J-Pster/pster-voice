package com.joaopster.pstervoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.sqrt

class BubbleView(context: Context) : View(context) {

    enum class State { IDLE, LISTENING, PROCESSING }
    enum class Zone { CANCEL, CONFIRM, WAVEFORM }

    companion object {
        private val BAR_WEIGHTS = floatArrayOf(0.45f, 0.75f, 1f, 1f, 0.75f, 0.45f)
    }

    private var state = State.IDLE
    private var audioLevel = 0f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_letter)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_waveform)
    }
    private val confirmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_confirm)
    }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_cancel)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_letter)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        updateBackgroundColor()
    }

    fun setState(newState: State) {
        state = newState
        updateBackgroundColor()
        invalidate()
    }

    fun setAudioLevel(level: Float) {
        audioLevel = level.coerceIn(0f, 1f)
        if (state == State.LISTENING) invalidate()
    }

    fun hitTestZone(x: Float): Zone = when {
        x < width / 3f -> Zone.CANCEL
        x > width * 2f / 3f -> Zone.CONFIRM
        else -> Zone.WAVEFORM
    }

    private fun updateBackgroundColor() {
        val colorRes = when (state) {
            State.IDLE -> R.color.bubble_idle
            State.LISTENING -> R.color.bubble_pill_listening_bg
            State.PROCESSING -> R.color.bubble_pill_processing_bg
        }
        backgroundPaint.color = ContextCompat.getColor(context, colorRes)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (state) {
            State.IDLE -> drawIdle(canvas)
            State.LISTENING -> drawExpanded(canvas, animated = true)
            State.PROCESSING -> drawExpanded(canvas, animated = false)
        }
    }

    private fun drawIdle(canvas: Canvas) {
        val cornerRadius = width * 0.22f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, backgroundPaint)

        textPaint.textSize = height * 0.5f
        val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("P", width / 2f, textY, textPaint)
    }

    private fun drawExpanded(canvas: Canvas, animated: Boolean) {
        val cornerRadius = height / 2f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, backgroundPaint)

        drawCancelButton(canvas)
        drawWaveform(canvas, animated)
        drawConfirmButton(canvas)
    }

    private fun drawCancelButton(canvas: Canvas) {
        val cx = width / 6f
        val cy = height / 2f
        val radius = height * 0.32f
        canvas.drawCircle(cx, cy, radius, cancelPaint)
        iconPaint.strokeWidth = radius * 0.18f
        val offset = radius * 0.45f
        canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, iconPaint)
        canvas.drawLine(cx - offset, cy + offset, cx + offset, cy - offset, iconPaint)
    }

    private fun drawConfirmButton(canvas: Canvas) {
        val cx = width * 5f / 6f
        val cy = height / 2f
        val radius = height * 0.32f
        canvas.drawCircle(cx, cy, radius, confirmPaint)
        iconPaint.strokeWidth = radius * 0.18f
        val checkPath = Path().apply {
            moveTo(cx - radius * 0.5f, cy)
            lineTo(cx - radius * 0.1f, cy + radius * 0.4f)
            lineTo(cx + radius * 0.5f, cy - radius * 0.4f)
        }
        canvas.drawPath(checkPath, iconPaint)
    }

    private fun drawWaveform(canvas: Canvas, animated: Boolean) {
        val zoneLeft = width / 3f
        val zoneWidth = width / 3f
        val gap = zoneWidth / (BAR_WEIGHTS.size * 2f)
        val barWidth = gap
        val maxBarHeight = height * 0.7f
        val minBarHeight = height * 0.15f
        val centerY = height / 2f
        // RMS de fala/ambiente normal fica na casa de 0.001-0.05, bem abaixo de 1.0;
        // sqrt + ganho traz esses valores pra uma faixa visivel nas barras.
        val level = if (animated) (sqrt(audioLevel) * 5f).coerceIn(0f, 1f) else 0.3f

        for (i in BAR_WEIGHTS.indices) {
            val barHeight = max(minBarHeight, maxBarHeight * level * BAR_WEIGHTS[i])
            val x = zoneLeft + gap + i * (barWidth + gap)
            canvas.drawRoundRect(
                RectF(x, centerY - barHeight / 2f, x + barWidth, centerY + barHeight / 2f),
                barWidth / 2f,
                barWidth / 2f,
                barPaint
            )
        }
    }
}
