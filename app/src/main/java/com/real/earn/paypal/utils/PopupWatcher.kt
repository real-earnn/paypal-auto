package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PopupWatcher {

    private var job: Job? = null

    fun start(scope: CoroutineScope, service: AccessibilityService) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            AutomationState.log("👁 PopupWatcher started")
            while (isActive) {
                if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                try {
                    scanAndDismiss(service)
                } catch (_: Exception) {}
                delay(300)
            }
            AutomationState.log("👁 PopupWatcher stopped")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun scanAndDismiss(service: AccessibilityService) {
        val root = service.rootInActiveWindow ?: return

        // 🟢 Naye aur purane dono Google popups detect karo
        val isGooglePopup = NodeFinder.containsAny(
            root,
            "Choose an account",
            "to continue to TikTok",
            "to continue to PayPal",
            "Sign in with Google",
            "Sign-in options",
            "Use passkey"
        )

        if (!isGooglePopup) return

        // Find X / Close button
        if (clickPopupClose(root)) {
            AutomationState.log("👁 Google/Gmail popup closed")
            SystemClock.sleep(500)
        }
    }

    private fun clickPopupClose(root: AccessibilityNodeInfo): Boolean {
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎", "Close", "close", "Cancel")
        
        // 1. Try finding by text/desc match
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            
            if (n.isVisibleToUser && closeWords.any { it.equals(t, true) || it.equals(d, true) }) {
                if (NodeFinder.click(n)) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        // 2. Fallback: Find clickable ImageView at top-right of the bottom sheet
        val rootRect = Rect(); root.getBoundsInScreen(rootRect)
        val topRightThreshold = rootRect.width() - 150
        
        queue.clear()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isClickable && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                val className = n.className?.toString() ?: ""
                
                // If it's a clickable image/button near top-right
                if ((className.contains("Image", true) || className.contains("Button", true)) &&
                    r.centerX() > topRightThreshold && r.centerY() < rootRect.height() / 2) {
                    if (NodeFinder.click(n)) return true
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }
}
