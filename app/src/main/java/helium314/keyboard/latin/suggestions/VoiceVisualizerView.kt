// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import helium314.keyboard.latin.utils.dpToPx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class VoiceVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        IDLE,
        CONNECTING,
        RECORDING,
        PROCESSING
    }

    private var mode = Mode.IDLE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()
    private var phase = 0f
    private var animator: ValueAnimator? = null

    var audioLevelProvider: (() -> Float)? = null
    private var smoothedLevel = 0f

    private val barWidth = 3.dpToPx(resources).toFloat()
    private val barSpacing = 3.dpToPx(resources).toFloat()
    private val slotWidth = barWidth + barSpacing
    private val minHeight = barWidth
    private val idleBreathingExtra = 2.dpToPx(resources).toFloat()
    private val minHeightExtra = 8.dpToPx(resources).toFloat()
    private val sidePadding = 6.dpToPx(resources).toFloat()

    init {
        val defaultColor = 0xFF4285F4.toInt()
        paint.color = defaultColor
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        if (newMode != Mode.RECORDING) {
            smoothedLevel = 0f
        }
        updateAnimation()
        invalidate()
    }

    private fun updateAnimation() {
        animator?.cancel()
        animator = null

        if (mode == Mode.IDLE || !isAttachedToWindow) {
            smoothedLevel = 0f
            return
        }

        val duration = when (mode) {
            Mode.CONNECTING -> 1200L
            Mode.RECORDING -> 800L
            Mode.PROCESSING -> 800L
            Mode.IDLE -> 0L
        }

        if (duration > 0) {
            animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    phase = va.animatedValue as Float
                    if (mode == Mode.RECORDING) {
                        val target = audioLevelProvider?.invoke() ?: 0f
                        val smoothingFactor = if (target > smoothedLevel) 0.35f else 0.15f
                        smoothedLevel += (target - smoothedLevel) * smoothingFactor
                    }
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
        smoothedLevel = 0f
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            updateAnimation()
        } else {
            animator?.cancel()
            animator = null
            smoothedLevel = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == Mode.IDLE) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val availableWidth = viewWidth - sidePadding * 2f
        if (availableWidth <= 0f) return

        val rawCount = ((availableWidth + barSpacing) / slotWidth).toInt()
        val barCount = rawCount.let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(7)
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing

        var startX = (viewWidth - totalBarsWidth) / 2f
        val centerY = viewHeight / 2f
        val maxHeight = (viewHeight * 0.65f).coerceAtLeast(minHeight + minHeightExtra)
        val centerIndex = (barCount - 1) / 2f
        val radius = barWidth / 2f

        val piHalf = (Math.PI / 2).toFloat()
        val level = smoothedLevel

        for (i in 0 until barCount) {
            val normDist = if (centerIndex > 0f) abs(i - centerIndex) / centerIndex else 0f

            val barHeight = when (mode) {
                Mode.RECORDING -> {
                    val envelope = 0.15f + 0.85f * cos(normDist * piHalf)
                    if (level < 0.03f) {
                        val idleWave = (sin(phase * 1.5f + i * 0.25f) + 1f) / 2f
                        minHeight + idleWave * idleBreathingExtra * envelope
                    } else {
                        val wave1 = sin(phase * 3.2f + i * 0.45f)
                        val wave2 = sin(phase * 2.0f - i * 0.55f + 1.2f)
                        val microFactor = 0.7f + 0.3f * ((wave1 + wave2) / 2f)
                        val dynamicFactor = (envelope * microFactor).coerceIn(0f, 1f)
                        val expansion = (level * dynamicFactor).coerceIn(0f, 1f)
                        minHeight + expansion * (maxHeight - minHeight)
                    }
                }
                Mode.PROCESSING -> {
                    val procEnvelope = 0.35f + 0.65f * cos(normDist * piHalf)
                    val travelWave = (sin(phase * 2.5f - i * 0.28f) + 1f) / 2f
                    minHeight + travelWave * procEnvelope * (maxHeight - minHeight) * 0.7f
                }
                Mode.CONNECTING -> {
                    val connEnvelope = 0.3f + 0.7f * cos(normDist * piHalf)
                    val pulse = (sin(phase * 1.5f + i * 0.2f) + 1f) / 2f
                    minHeight + pulse * connEnvelope * (maxHeight - minHeight) * 0.35f
                }
                Mode.IDLE -> minHeight
            }

            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val right = startX + barWidth

            barRect.set(startX, top, right, bottom)
            canvas.drawRoundRect(barRect, radius, radius, paint)

            startX += slotWidth
        }
    }
}
