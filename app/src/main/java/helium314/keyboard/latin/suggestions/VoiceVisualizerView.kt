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

    private val barCount = 4
    private val barRect = RectF()
    private var phase = 0f
    private var animator: ValueAnimator? = null

    var audioLevelProvider: (() -> Float)? = null
    private var smoothedLevel = 0f

    private val barWidth = 5.dpToPx(resources).toFloat()
    private val barSpacing = 7.dpToPx(resources).toFloat()
    private val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
    private val minHeight = barWidth
    private val idleBreathingExtra = 2.dpToPx(resources).toFloat()
    private val minHeightExtra = 8.dpToPx(resources).toFloat()

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
            Mode.PROCESSING -> 600L
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
        if (viewWidth <= 0 || viewHeight <= 0) return

        var startX = (viewWidth - totalBarsWidth) / 2f
        val centerY = viewHeight / 2f
        val maxHeight = (viewHeight * 0.65f).coerceAtLeast(minHeight + minHeightExtra)

        for (i in 0 until barCount) {
            val barHeight = when (mode) {
                Mode.RECORDING -> {
                    val level = smoothedLevel
                    if (level < 0.04f) {
                        // Subtle gentle idle breathing when mic is open but quiet
                        val idleWave = (sin(phase * 1.2f + i * 0.7f) + 1f) / 2f
                        minHeight + idleWave * idleBreathingExtra
                    } else {
                        // Bar sensitivity curve (inner bars react slightly more, outer bars slightly less)
                        val barWeight = when (i) {
                            0 -> 0.72f
                            1 -> 1.0f
                            2 -> 0.88f
                            3 -> 0.68f
                            else -> 0.8f
                        }
                        // Organic dynamic movement mimicking frequency spectrum
                        val microWave = (sin(phase * 2.8f + i * 1.3f) + 1f) / 2f
                        val dynamicFactor = (0.7f + 0.3f * microWave) * barWeight
                        val expansion = (level * dynamicFactor).coerceIn(0f, 1f)
                        minHeight + expansion * (maxHeight - minHeight)
                    }
                }
                Mode.PROCESSING -> {
                    val wave = (sin(phase * 2.2f + i * 0.9f) + 1f) / 2f
                    minHeight + wave * (maxHeight - minHeight) * 0.65f
                }
                Mode.CONNECTING -> {
                    val pulse = (sin(phase + i * 0.6f) + 1f) / 2f
                    minHeight + pulse * (maxHeight - minHeight) * 0.35f
                }
                Mode.IDLE -> minHeight
            }

            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val right = startX + barWidth
            val radius = barWidth / 2f

            barRect.set(startX, top, right, bottom)
            canvas.drawRoundRect(barRect, radius, radius, paint)

            startX += barWidth + barSpacing
        }
    }
}
