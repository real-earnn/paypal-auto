package com.real.earn.paypal.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * 🟢 Calm water tank animation — balance card ke andar.
 * Subtle, slow, two layers only. Not a rushing river.
 */
class WaterWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Back layer — deeper, darker emerald
    private val paintBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44168A50.toInt()
        style = Paint.Style.FILL
    }

    // Front layer — slightly brighter emerald, still subtle
    private val paintFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5527B86E.toInt()
        style = Paint.Style.FILL
    }

    private var offsetBack = 0f
    private var offsetFront = 0f

    private var animator: ValueAnimator? = null

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 14000L       // 🟢 Very slow — 14s per cycle
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                offsetBack = (offsetBack + 0.0015f) % 1f
                offsetFront = (offsetFront + 0.0022f) % 1f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 🟢 Two layers only. Low amplitude. Long wavelength.
        drawWave(canvas, w, h, 0.58f, offsetBack, paintBack, amplitudeRatio = 0.012f)
        drawWave(canvas, w, h, 0.65f, offsetFront, paintFront, amplitudeRatio = 0.010f)
    }

    private fun drawWave(
        canvas: Canvas,
        w: Float,
        h: Float,
        startRatio: Float,
        offset: Float,
        paint: Paint,
        amplitudeRatio: Float
    ) {
        val path = Path()
        val startY = h * startRatio
        val amplitude = h * amplitudeRatio
        val wavelength = w * 2.0f       // 🟢 Long wave = calm
        val phase = offset * wavelength * 2

        path.moveTo(0f, h)
        path.lineTo(0f, startY)

        var x = 0f
        while (x <= w) {
            val y = startY + sin((x / wavelength * 2 * Math.PI + phase).toFloat()) * amplitude
            path.lineTo(x, y)
            x += 6f
        }
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }
}
