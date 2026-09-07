package com.joaopster.pstervoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.sqrt

class BubbleView(context: Context) : View(context) {

    enum class State { IDLE, CONNECTING, LISTENING, PROCESSING }
    enum class Zone { CANCEL, CONFIRM, WAVEFORM }

    companion object {
        private val BAR_WEIGHTS = floatArrayOf(0.45f, 0.75f, 1f, 1f, 0.75f, 0.45f)
        private const val LEVEL_SMOOTHING = 0.3f
    }

    private var state = State.IDLE
    private var targetAudioLevel = 0f
    private var displayedAudioLevel = 0f
    private var animationRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            displayedAudioLevel += (targetAudioLevel - displayedAudioLevel) * LEVEL_SMOOTHING
            invalidate()
            if (state == State.LISTENING) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                animationRunning = false
            }
        }
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_letter)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val connectingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bubble_waveform)
        textAlign = Paint.Align.CENTER
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
        if (newState == State.LISTENING) {
            startAnimationLoopIfNeeded()
        } else {
            targetAudioLevel = 0f
            displayedAudioLevel = 0f
        }
        invalidate()
    }

    fun setAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)
        startAnimationLoopIfNeeded()
    }

    private fun startAnimationLoopIfNeeded() {
        if (!animationRunning && state == State.LISTENING) {
            animationRunning = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun hitTestZone(x: Float): Zone = when {
        x < width / 3f -> Zone.CANCEL
        x > width * 2f / 3f -> Zone.CONFIRM
        else -> Zone.WAVEFORM
    }

    private fun updateBackgroundColor() {
        val colorRes = when (state) {
            State.IDLE -> R.color.bubble_idle
            State.CONNECTING -> R.color.bubble_pill_listening_bg
            State.LISTENING -> R.color.bubble_pill_listening_bg
            State.PROCESSING -> R.color.bubble_pill_processing_bg
        }
        backgroundPaint.color = ContextCompat.getColor(context, colorRes)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (state) {
            State.IDLE -> drawIdle(canvas)
            State.CONNECTING -> drawConnecting(canvas)
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

    private fun drawConnecting(canvas: Canvas) {
        val cornerRadius = height / 2f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, backgroundPaint)

        drawCancelButton(canvas)
        connectingTextPaint.textSize = height * 0.28f
        val textY = height / 2f - (connectingTextPaint.descent() + connectingTextPaint.ascent()) / 2f
        canvas.drawText("Conectando...", width / 2f, textY, connectingTextPaint)
        drawConfirmButton(canvas)
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
        val level = if (animated) (sqrt(displayedAudioLevel) * 5f).coerceIn(0f, 1f) else 0.3f

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
