package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
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
 * - Detects PayPal's "I am human" hCaptcha checkbox → clicks it
 * - If picture puzzle appears → triggers loud alarm (so human comes)
 * - Once captcha disappears → stops alarm
 */
object CaptchaWatcher {

    private var job: Job? = null
    private var lastClickAt = 0L
    private var consecutiveClicks = 0
    private var alertActive = false

    // Expose so PayPalAutomation can check
    @Volatile
    var picturePuzzleDetected: Boolean = false
        private set

    fun start(scope: CoroutineScope, service: AccessibilityService) {
        if (job?.isActive == true) return
        picturePuzzleDetected = false
        alertActive = false

        job = scope.launch(Dispatchers.Default) {
            AutomationState.log("🛡️ CaptchaWatcher started")
            val ctx: Context = service.applicationContext

            while (isActive) {
                if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                try {
                    scanAndSolve(service, ctx)
                } catch (_: Exception) {}
                delay(500)
            }

            // Cleanup
            if (alertActive) {
                CaptchaAlert.stopAlert()
                alertActive = false
            }
            picturePuzzleDetected = false
            AutomationState.log("🛡️ CaptchaWatcher stopped")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (alertActive) {
            CaptchaAlert.stopAlert()
            alertActive = false
        }
        picturePuzzleDetected = false
        lastClickAt = 0L
        consecutiveClicks = 0
    }

    private fun scanAndSolve(service: AccessibilityService, ctx: Context) {
        val root = service.rootInActiveWindow ?: return

        // ═══ Step 1: Is any captcha visible? ═══
        val captchaVisible = NodeFinder.containsAny(
            root,
            "I am human", "Security Challenge", "hCaptcha", "Verify you are human"
        )

        // ═══ Step 2: Is it a picture puzzle? ═══
        val isPuzzle = captchaVisible && isPicturePuzzle(root)

        // ═══ Step 3: Handle alarm ═══
        if (isPuzzle) {
            if (!alertActive) {
                AutomationState.log("🛡️ PICTURE PUZZLE detected — alarming!")
                picturePuzzleDetected = true
                CaptchaAlert.startAlert(ctx)
                alertActive = true
            }
            // Do NOT try to solve picture puzzle — let human do it
            return
        }

        // ═══ Step 4: Puzzle gone? Stop alarm ═══
        if (alertActive && !isPuzzle) {
            AutomationState.log("🛡️ Puzzle cleared — stopping alarm")
            CaptchaAlert.stopAlert()
            alertActive = false
            picturePuzzleDetected = false
        }

        // ═══ Step 5: If simple "I am human" checkbox visible — click it ═══
        if (captchaVisible) {
            if (consecutiveClicks == 0) {
                AutomationState.log("🛡️ hCaptcha checkbox visible")
            }

            val now = System.currentTimeMillis()
            if (now - lastClickAt < 2500) return
            if (consecutiveClicks >= 6) return

            // Strategy A: "I am human" text → click 70px to LEFT
            val humanNode = NodeFinder.findByTextContains(root, "I am human")
            if (humanNode != null) {
                val r = Rect(); humanNode.getBoundsInScreen(r)
                val cbX = r.left - 70
                val cbY = r.centerY()
                if (cbX > 0 && cbY > 0) {
                    AutomationState.log("🛡️ Checkbox tap at ($cbX, $cbY) [click #${consecutiveClicks + 1}]")
                    tapAt(service, cbX, cbY)
                    lastClickAt = System.currentTimeMillis()
                    consecutiveClicks++
                    return
                }
            }

            // Strategy B: small clickable square in top half
            val rootR = Rect(); root.getBoundsInScreen(rootR)
            val topLimit = rootR.top + (rootR.height() * 0.5).toInt()

            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (n.isClickable && n.isVisibleToUser) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    val w = r.width(); val h = r.height()
                    val isSquare = w in 35..110 && h in 35..110 && Math.abs(w - h) < 30
                    if (isSquare && r.centerY() < topLimit) {
                        AutomationState.log("🛡️ Checkbox node tap [click #${consecutiveClicks + 1}]")
                        if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            lastClickAt = System.currentTimeMillis()
                            consecutiveClicks++
                            return
                        }
                        tapAt(service, r.centerX(), r.centerY())
                        lastClickAt = System.currentTimeMillis()
                        consecutiveClicks++
                        return
                    }
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
        } else {
            consecutiveClicks = 0
        }
    }

    /** Detect picture-puzzle indicators (drag-drop, image grid, rotate) */
    private fun isPicturePuzzle(root: AccessibilityNodeInfo): Boolean {
        val puzzleMarkers = arrayOf(
            "Drag the", "Select all", "Choose the", "Choose all",
            "Please click", "Please drag", "Please select",
            "Click each", "Rotate the", "Match the",
            "Skip", "Audio", "Refresh"
        )
        // If any puzzle marker is visible, it's a picture puzzle
        if (NodeFinder.containsAny(root, *puzzleMarkers)) return true

        // Look for a challenge image container (large ImageView in middle)
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val midTop = rootR.top + (rootR.height() * 0.30).toInt()
        val midBot = rootR.top + (rootR.height() * 0.75).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val cn = n.className?.toString() ?: ""
            if (cn.contains("ImageView", true)) {
                val r = Rect(); n.getBoundsInScreen(r)
                // Large image in middle → likely puzzle
                if (r.width() > 200 && r.height() > 200 &&
                    r.top > midTop && r.top < midBot) {
                    return true
                }
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
        } catch (e: Exception) {
            false
        }
    }
}
