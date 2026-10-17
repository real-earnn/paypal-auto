package com.real.earn.paypal.utils

import android.annotation.SuppressLint
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.real.earn.paypal.R

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var llExpanded: LinearLayout? = null
    private var ivMinimized: ImageView? = null
    private var btnToggle: ImageButton? = null
    private var btnClose: ImageButton? = null
    private var btnMinimize: ImageButton? = null

    var onToggle: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private var paused: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null

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
            y = 300 // Thoda upar rakha hai taake bottom tabs block na hon
        }

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)
        
        llExpanded = overlayView?.findViewById(R.id.llExpanded)
        ivMinimized = overlayView?.findViewById(R.id.ivMinimized)
        btnToggle = overlayView?.findViewById(R.id.btnToggle)
        btnClose = overlayView?.findViewById(R.id.btnClose)
        btnMinimize = overlayView?.findViewById(R.id.btnMinimize)

        btnToggle?.setOnClickListener { 
            onToggle?.invoke()
            resetAutoHideTimer()
        }
        
        btnClose?.setOnClickListener { 
            onClose?.invoke()
        }
        
        btnMinimize?.setOnClickListener { 
            collapseOverlay()
        }

        ivMinimized?.setOnClickListener {
            expandOverlay()
        }

        // Touch listener for dragging (Simplified)
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
                        resetAutoHideTimer() // User touched, reset timer
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
            resetAutoHideTimer()
        } catch (_: Exception) {}
    }

    fun hide() {
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
        llExpanded = null
        ivMinimized = null
        btnToggle = null
        btnClose = null
        btnMinimize = null
    }

    fun updatePausedVisuals(isPaused: Boolean) {
        paused = isPaused
        btnToggle?.setImageResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause)
        btnToggle?.contentDescription = if (isPaused) "Resume" else "Pause"
    }

    // 🟢 Auto Hide Logic
    private fun resetAutoHideTimer() {
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = Runnable {
            collapseOverlay()
        }
        handler.postDelayed(autoHideRunnable!!, 5000L) // 5 seconds
    }

    private fun collapseOverlay() {
        llExpanded?.visibility = View.GONE
        ivMinimized?.visibility = View.VISIBLE
        autoHideRunnable?.let { handler.removeCallbacks(it) } // Stop timer when collapsed
    }

    private fun expandOverlay() {
        llExpanded?.visibility = View.VISIBLE
        ivMinimized?.visibility = View.GONE
        resetAutoHideTimer() // Restart timer when expanded
    }
}
