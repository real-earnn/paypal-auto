package com.real.earn.paypal.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.real.earn.paypal.R

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var btnToggle: ImageView? = null
    private var btnClose: ImageView? = null

    var onToggle: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    var isPaused = false

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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 240
        }

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)
        btnToggle = overlayView?.findViewById(R.id.btnToggle)
        btnClose = overlayView?.findViewById(R.id.btnClose)

        btnToggle?.setOnClickListener { onToggle?.invoke() }
        btnClose?.setOnClickListener { onClose?.invoke() }

        // Drag support (only on the panel, not on buttons)
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
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
                            params?.x = initialX + dx.toInt()
                            params?.y = initialY + dy.toInt()
                            overlayView?.let { windowManager?.updateViewLayout(it, params) }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> return isDragging
                }
                return false
            }
        })

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {}
    }

    fun hide() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
        btnToggle = null
        btnClose = null
    }

    /** Paused → show Play icon; Running → show Pause icon */
    fun setPaused(paused: Boolean) {
        isPaused = paused
        btnToggle?.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        btnToggle?.contentDescription = if (paused) "Resume" else "Pause"
    }
}
