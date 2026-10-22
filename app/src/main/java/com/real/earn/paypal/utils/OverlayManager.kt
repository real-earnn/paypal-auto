package com.real.earn.paypal.utils

import android.annotation.SuppressLint
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import com.real.earn.paypal.R

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var ovPulseRing: View? = null
    private var ovActions: View? = null
    private var btnToggle: ImageButton? = null
    private var btnPause: ImageButton? = null
    private var btnStop: ImageButton? = null

    var onToggle: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private var isExpanded = false
    private var paused = false

    private val handler = Handler(Looper.getMainLooper())
    private var pulseAnimator: ValueAnimator? = null
    private var ringAnimator: ObjectAnimator? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 20
            y = 300
        }

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)

        ovPulseRing = overlayView?.findViewById(R.id.ovPulseRing)
        ovActions = overlayView?.findViewById(R.id.ovActions)
        btnToggle = overlayView?.findViewById(R.id.btnToggle)
        btnPause = overlayView?.findViewById(R.id.btnPause)
        btnStop = overlayView?.findViewById(R.id.btnStop)

        // ── Toggle button ──
        btnToggle?.setOnClickListener {
            if (isExpanded) collapse() else expand()
        }

        // ── Pause button ──
        btnPause?.setOnClickListener {
            onToggle?.invoke()
            btnPause?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(100)
                ?.withEndAction {
                    btnPause?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()
                }?.start()
        }

        // ── Stop button ──
        btnStop?.setOnClickListener {
            btnStop?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(100)
                ?.withEndAction {
                    onClose?.invoke()
                }?.start()
        }

        // ── Draggable (drag by toggle bubble) ──
        btnToggle?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        touchX = event.rawX
                        touchY = event.rawY
                        isDragging = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (dx * dx + dy * dy > 150) isDragging = true
                        if (isDragging) {
                            params?.x = initialX - dx.toInt()
                            params?.y = initialY - dy.toInt()
                            overlayView?.let { windowManager?.updateViewLayout(it, params) }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(overlayView, params)
            startPulseAnimation()
        } catch (_: Exception) {}
    }

    fun hide() {
        stopPulseAnimation()
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
        ovPulseRing = null
        ovActions = null
        btnToggle = null
        btnPause = null
        btnStop = null
        isExpanded = false
    }

    // ═══════════════════════════════════════════════════════════════════
    // 🟢 Expand — Toggle button ko gold + X icon banao + actions slide in
    // ═══════════════════════════════════════════════════════════════════
    private fun expand() {
        if (isExpanded || ovActions == null || btnToggle == null) return
        isExpanded = true

        // Animate toggle: scale down → change drawable → scale up
        btnToggle?.animate()
            ?.scaleX(0.7f)?.scaleY(0.7f)?.setDuration(120)
            ?.withEndAction {
                btnToggle?.setBackgroundResource(R.drawable.bg_overlay_toggle_open)
                btnToggle?.setImageResource(R.drawable.ic_overlay_close_x)
                btnToggle?.setPadding(
                    dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10)
                )
                btnToggle?.animate()
                    ?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(200)
                    ?.setInterpolator(OvershootInterpolator())
                    ?.start()
            }?.start()

        // Stop pulse ring when expanded
        ovPulseRing?.animate()?.alpha(0f)?.setDuration(200)?.start()
        stopRingRotation()

        // Show and animate actions (slide from right)
        ovActions?.visibility = View.VISIBLE
        ovActions?.alpha = 0f
        ovActions?.translationX = dpToPx(40).toFloat()
        ovActions?.animate()
            ?.alpha(1f)
            ?.translationX(0f)
            ?.setDuration(280)
            ?.setInterpolator(OvershootInterpolator())
            ?.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 🟢 Collapse — Toggle button wapas emerald + lightning + actions hide
    // ═══════════════════════════════════════════════════════════════════
    private fun collapse() {
        if (!isExpanded || ovActions == null || btnToggle == null) return
        isExpanded = false

        // Animate toggle: scale down → change drawable → scale up
        btnToggle?.animate()
            ?.scaleX(0.7f)?.scaleY(0.7f)?.setDuration(120)
            ?.withEndAction {
                btnToggle?.setBackgroundResource(R.drawable.bg_overlay_toggle_closed)
                btnToggle?.setImageResource(R.drawable.ic_overlay_lightning)
                btnToggle?.setPadding(
                    dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10)
                )
                btnToggle?.animate()
                    ?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(200)
                    ?.setInterpolator(OvershootInterpolator())
                    ?.start()
            }?.start()

        // Bring back pulse ring
        ovPulseRing?.animate()?.alpha(0.6f)?.setDuration(200)?.start()
        startRingRotation()

        // Hide actions (slide to right)
        ovActions?.animate()
            ?.alpha(0f)
            ?.translationX(dpToPx(40).toFloat())
            ?.setDuration(200)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction {
                ovActions?.visibility = View.GONE
                ovActions?.translationX = 0f
            }?.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 🟢 Pulse animation (breathing effect on toggle)
    // ═══════════════════════════════════════════════════════════════════
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                if (!isExpanded) {
                    val scale = 1f + (animator.animatedValue as Float) * 0.08f
                    btnToggle?.scaleX = scale
                    btnToggle?.scaleY = scale
                }
            }
            start()
        }
        startRingRotation()
    }

    private fun startRingRotation() {
        ringAnimator?.cancel()
        ovPulseRing?.rotation = 0f
        ringAnimator = ObjectAnimator.ofFloat(ovPulseRing, View.ROTATION, 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopRingRotation() {
        ringAnimator?.cancel()
        ringAnimator = null
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        stopRingRotation()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 🟢 Update paused visuals
    // ═══════════════════════════════════════════════════════════════════
    fun updatePausedVisuals(isPaused: Boolean) {
        paused = isPaused
        btnPause?.setImageResource(
            if (isPaused) R.drawable.ic_overlay_lightning   // Resume icon (lightning = fast forward vibe)
            else R.drawable.ic_overlay_pause
        )
        btnPause?.contentDescription = if (isPaused) "Resume" else "Pause"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
