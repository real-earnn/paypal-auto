package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
            AutomationState.log("👁 PopupWatcher started (Strict Mode)")
            while (isActive) {
                if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                try {
                    scanAndDismiss(service)
                } catch (_: Exception) {}
                delay(400)
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

        // 🟢 Strict Detection: Sirf actual Google popup markers
        val isGooglePopup = 
            NodeFinder.containsAny(
                root,
                "Choose an account",
                "to continue to PayPal",
                "to continue to TikTok",
                "Sign-in options",
                "Sign in with Google",
                "Use another account"
            )

        if (!isGooglePopup) return

        // Try to find X button
        if (clickPopupClose(root, service)) {
            AutomationState.log("👁 Google popup closed (X button)")
            SystemClock.sleep(800)
            return
        }

        // 🟢 Fallback: No X button found — tap top of screen to dismiss
        AutomationState.log("👁 No X button found — tapping top of screen to dismiss")
        val screenR = Rect(); root.getBoundsInScreen(screenR)
        val tapX = screenR.centerX()
        val tapY = screenR.top + 150 // 150px from top

        tapAt(service, tapX, tapY)
        SystemClock.sleep(800)

        // Check if it's gone now
        val afterRoot = service.rootInActiveWindow
        if (afterRoot != null && !NodeFinder.containsAny(afterRoot, "Choose an account", "to continue to PayPal")) {
            AutomationState.log("👁 Popup dismissed via top tap")
        }
    }

    private fun clickPopupClose(root: AccessibilityNodeInfo, service: AccessibilityService): Boolean {
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎", "Close", "close", "Cancel")
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

    private fun tapAt(service: AccessibilityService, x: Int, y: Int): Boolean {
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val latch = java.util.concurrent.CountDownLatch(1)
            var ok = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { ok = false; latch.countDown() }
            }, null)
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            ok
        } catch (e: Exception) { false }
    }
}
