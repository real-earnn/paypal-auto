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
    private var consecutiveClicks = 0
    private var alertActive = false

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

        val captchaVisible = NodeFinder.containsAny(
            root,
            "I am human", "Security Challenge", "hCaptcha", "Verify you are human"
        )

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
            if (consecutiveClicks == 0) {
                AutomationState.log("🛡️ hCaptcha checkbox visible")
            }

            val now = System.currentTimeMillis()
            if (now - lastClickAt < 2500) return
            if (consecutiveClicks >= 6) return

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

    /**
     * 🟢 Improved: 
     *   1. Agar Resend / Continue visible → PUZZLE NAHI
     *   2. Sirf explicit puzzle text markers pe bharosa (ImageView heuristic hataya)
     */
    private fun isPicturePuzzle(root: AccessibilityNodeInfo): Boolean {
        // ═══ Priority 1: Agar number/OTP screen pe hain → puzzle nahi ═══
        if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) return false
        if (NodeFinder.findByText(root, "Continue", "Next") != null) return false

        // ═══ Priority 2: Sirf explicit puzzle text markers ═══
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
