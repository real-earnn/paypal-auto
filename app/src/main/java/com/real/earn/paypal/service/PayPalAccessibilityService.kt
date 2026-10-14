package com.real.earn.paypal.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * PayPal Accessibility Service — singleton holder + hook for automation.
 *
 * Phase 5A: singleton + basic hooks.
 * Phase 5B: full PayPal automation methods aayenge yahan.
 */
class PayPalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PayPalA11ySvc"

        @Volatile
        var instance: PayPalAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
        AutomationState.log("🔌 Accessibility service ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 5B mein event-driven detection aayega (optional)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }
}
