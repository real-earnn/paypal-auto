package com.real.earn.paypal.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 🟢 Water wave animation — card ke andar flowing water
 */
class WaterWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8827B86E.toInt()
        style = Paint.Style.FILL
    }
    private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55E6C15A.toInt()
        style = Paint.Style.FILL
    }
    private val paint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private var offset1 = 0f
    private var offset2 = 0f
    private var offset3 = 0f

    private var animator: ValueAnimator? = null

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                offset1 = (offset1 + 0.008f) % 1f
                offset2 = (offset2 + 0.012f) % 1f
                offset3 = (offset3 + 0.016f) % 1f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        drawWave(canvas, w, h, 0.55f, offset1, paint1)
        drawWave(canvas, w, h, 0.62f, offset2, paint2)
        drawWave(canvas, w, h, 0.70f, offset3, paint3)
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float, startRatio: Float, offset: Float, paint: Paint) {
        val path = Path()
        val startY = h * startRatio
        val amplitude = h * 0.06f
        val wavelength = w * 0.9f
        val phase = offset * wavelength * 2

        path.moveTo(0f, h)
        path.lineTo(0f, startY)

        var x = 0f
        while (x <= w) {
            val y = startY + kotlin.math.sin((x / wavelength * 2 * Math.PI + phase).toFloat()) * amplitude
            path.lineTo(x, y)
            x += 5f
        }
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
