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
            AutomationState.log("👁 PopupWatcher started (Generic)")
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

        // 🟢 Generic Google Sign-in / Gmail popup markers
        val isGmailPopup =
            NodeFinder.containsAny(
                root,
                "Choose an account",
                "Sign-in options",
                "Sign in with Google",
                "Use another account"
            )

        if (!isGmailPopup) return

        // Find X / close / cross button and click it
        if (clickPopupClose(root)) {
            AutomationState.log("👁 Gmail/Google popup closed")
            SystemClock.sleep(500)
        }
    }

    private fun clickPopupClose(root: AccessibilityNodeInfo): Boolean {
        // Method 1: Generic X / Close buttons
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎", "Close", "close")
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
        return false
    }
}
