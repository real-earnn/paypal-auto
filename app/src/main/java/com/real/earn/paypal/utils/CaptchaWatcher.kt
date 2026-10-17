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

object CaptchaWatcher {

    private var job: Job? = null
    private var lastClickAt = 0L
    private var lastCaptchaLogAt = 0L
    private var consecutiveClicks = 0
    private var alertActive = false

    @Volatile
    var picturePuzzleDetected: Boolean = false
        private set

    fun start(scope: CoroutineScope, service: AccessibilityService) {
        if (job?.isActive == true) return
        picturePuzzleDetected = false
        alertActive = false
        lastCaptchaLogAt = 0L

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
        lastCaptchaLogAt = 0L
        consecutiveClicks = 0
    }

    private fun scanAndSolve(service: AccessibilityService, ctx: Context) {
        val root = service.rootInActiveWindow ?: return

        val captchaVisible = isCaptchaPresent(root)
        val isPuzzle = captchaVisible && isPicturePuzzle(root)

        if (isPuzzle) {
            if (!alertActive) {
                AutomationState.log("🛡️ PICTURE PUZZLE detected — alarming!")
                picturePuzzleDetected = true
                CaptchaAlert.startAlert(ctx)
                alertActive = true
            }
            return
        }

        if (alertActive && !isPuzzle) {
            AutomationState.log("🛡️ Puzzle cleared — stopping alarm")
            CaptchaAlert.stopAlert()
            alertActive = false
            picturePuzzleDetected = false
        }

        if (captchaVisible) {
            // 🟢 Wait for "I am human" to fully load
            val humanNode = NodeFinder.findByTextContains(root, "I am human")
            if (humanNode == null) {
                consecutiveClicks = 0
                return // WebView is still loading
            }

            val now = System.currentTimeMillis()
            
            // 🟢 Log only once every 5 seconds
            if (consecutiveClicks == 0 && now - lastCaptchaLogAt > 5000) {
                AutomationState.log("🛡️ hCaptcha checkbox visible")
                lastCaptchaLogAt = now
            }

            if (now - lastClickAt < 4000) return // 4 sec cooldown
            if (consecutiveClicks >= 6) return

            val r = Rect(); humanNode.getBoundsInScreen(r)
            val cbX = r.left - 70
            val cbY = r.centerY()
            if (cbX > 0 && cbY > 0) {
                AutomationState.log("🛡️ Checkbox tap at ($cbX, $cbY) [click #${consecutiveClicks + 1}]")
                tapAt(service, cbX, cbY)
                lastClickAt = System.currentTimeMillis()
                consecutiveClicks++
            }
        } else {
            consecutiveClicks = 0
        }
    }

    private fun isCaptchaPresent(root: AccessibilityNodeInfo): Boolean {
        return NodeFinder.containsAny(
            root, "I am human", "Security Challenge", "hCaptcha", "Verify you are human", "Verify you are a human"
        )
    }

    private fun isPicturePuzzle(root: AccessibilityNodeInfo): Boolean {
        if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) return false
        if (NodeFinder.findByText(root, "Continue", "Next") != null) return false

        val puzzleMarkers = arrayOf(
            "Drag the", "Select all", "Choose the", "Choose all",
            "Please click", "Please drag", "Please select",
            "Click each", "Rotate the", "Match the",
            "Skip", "Audio", "Refresh"
        )
        return NodeFinder.containsAny(root, *puzzleMarkers)
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
