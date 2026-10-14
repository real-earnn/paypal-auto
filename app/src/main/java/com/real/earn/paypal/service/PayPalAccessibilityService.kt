package com.real.earn.paypal.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * PayPal Accessibility Service — Phase 5/6 mein yahan automation logic aayega.
 * Abhi sirf skeleton hai taake manifest valid rahe.
 */
class PayPalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PayPalA11ySvc"

        @Volatile
        var instance: PayPalAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 5/6 mein yahan event-driven logic aayega
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
