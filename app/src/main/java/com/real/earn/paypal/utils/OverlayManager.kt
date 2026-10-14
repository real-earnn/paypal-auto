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
import android.widget.TextView
import com.real.earn.paypal.R

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    var onPause: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onStopAndClose: (() -> Unit)? = null

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
            y = 200
        }

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)

        overlayView?.findViewById<TextView>(R.id.btnPause)?.setOnClickListener { onPause?.invoke() }
        overlayView?.findViewById<TextView>(R.id.btnStop)?.setOnClickListener { onStop?.invoke() }
        overlayView?.findViewById<TextView>(R.id.btnStopClose)?.setOnClickListener { onStopAndClose?.invoke() }

        // Drag support
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
                        if (dx * dx + dy * dy > 100) isDragging = true
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
        } catch (e: Exception) {
            // Permission missing or other
        }
    }

    fun hide() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
    }

    fun setPauseText(text: String) {
        overlayView?.findViewById<TextView>(R.id.btnPause)?.text = text
    }
}
