package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background captcha watcher.
 * Detects PayPal's "I am human" hCaptcha checkbox and clicks it.
 * Runs in parallel with the main automation flow.
 */
object CaptchaWatcher {

    private var job: Job? = null
    private var lastClickAt = 0L
    private var consecutiveClicks = 0

    fun start(scope: CoroutineScope, service: AccessibilityService) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            AutomationState.log("🛡️ CaptchaWatcher started")
            while (isActive) {
                if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                try {
                    scanAndSolve(service)
                } catch (_: Exception) {}
                delay(600)
            }
            AutomationState.log("🛡️ CaptchaWatcher stopped")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastClickAt = 0L
        consecutiveClicks = 0
    }

    private fun scanAndSolve(service: AccessibilityService) {
        val root = service.rootInActiveWindow ?: return

        // ── Detect hCaptcha page ──
        val isCaptchaPage = NodeFinder.containsAny(
            root,
            "I am human", "Security Challenge", "hCaptcha", "Privacy", "Terms"
        )
        if (!isCaptchaPage) {
            consecutiveClicks = 0
            return
        }

        // Only log once per appearance
        if (consecutiveClicks == 0) {
            AutomationState.log("🛡️ hCaptcha detected")
        }

        // Throttle: don't click more than once per 2.5s
        val now = System.currentTimeMillis()
        if (now - lastClickAt < 2500) return

        // Limit: max 6 clicks per captcha appearance
        if (consecutiveClicks >= 6) return

        // ── Strategy 1: Find "I am human" text, click checkbox to its LEFT ──
        val humanNode = NodeFinder.findByTextContains(root, "I am human")
        if (humanNode != null) {
            val r = Rect()
            humanNode.getBoundsInScreen(r)

            // Checkbox square is usually ~60-75px to the LEFT of "I am human"
            val cbX = r.left - 70
            val cbY = r.centerY()

            if (cbX > 0 && cbY > 0) {
                AutomationState.log("🛡️ Click checkbox at ($cbX, $cbY) [click #${consecutiveClicks + 1}]")
                tapAt(service, cbX, cbY)
                lastClickAt = System.currentTimeMillis()
                consecutiveClicks++
                return
            }
        }

        // ── Strategy 2: Find small clickable square node in top area ──
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val topLimit = rootR.top + (rootR.height() * 0.50).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isClickable && n.isVisibleToUser) {
                val r = Rect()
                n.getBoundsInScreen(r)
                val w = r.width()
                val h = r.height()

                // Checkbox heuristic: roughly square, small, in top half
                val isSquare = w in 35..110 && h in 35..110 && Math.abs(w - h) < 30
                if (isSquare && r.centerY() < topLimit) {
                    AutomationState.log("🛡️ Click checkbox node [click #${consecutiveClicks + 1}]")
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        lastClickAt = System.currentTimeMillis()
                        consecutiveClicks++
                        return
                    }
                    // Fallback: gesture tap at center
                    tapAt(service, r.centerX(), r.centerY())
                    lastClickAt = System.currentTimeMillis()
                    consecutiveClicks++
                    return
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
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
        } catch (e: Exception) {
            AutomationState.log("🛡️ tapAt err: ${e.message}")
            false
        }
    }
}
