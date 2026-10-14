package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object PayPalActions {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    private const val US_TAP_X = 600
    private const val US_TAP_Y = 125

    // ══════════════════════════════════════════════════════
    //  LAUNCH
    // ══════════════════════════════════════════════════════
    fun launch(service: AccessibilityService): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(PAYPAL_PKG)
                ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            service.startActivity(intent)
            // Wait for PayPal app to be foreground (package match)
            StepUtils.waitForPackage(service, "paypal", 8000)
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  TAP GET STARTED — strict
    //  Only skips if number screen (edit field or email text) is visible
    // ══════════════════════════════════════════════════════
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastLog = 0L

        AutomationState.log("ℹ Waiting for Get Started button…")

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // ONLY skip if number screen already visible
                val hasEditField = findEditableUnder(root) != null
                val hasEmailText = NodeFinder.containsAny(
                    root, "email or number", "Email or phone number"
                )
                if (hasEditField || hasEmailText) {
                    AutomationState.log("ℹ Number screen already visible — Get Started skipped")
                    return true
                }

                // Look for Get Started — text first
                val node = NodeFinder.findByText(root, "Get Started")
                    ?: NodeFinder.findByTextContains(root, "Get Started")
                    ?: findByContentDescription(root, "Get Started")

                if (node != null) {
                    AutomationState.log("ℹ Get Started FOUND — clicking…")
                    val ok = NodeFinder.click(node)
                        || (NodeFinder.findClickableParent(node)
                                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                    if (ok) {
                        AutomationState.log("✔ Get Started tapped — 5s wait for main page…")
                        StepUtils.sleepMs(5000)
                        return true
                    } else {
                        AutomationState.log("⚠ Get Started click returned false")
                    }
                }
            }

            // Log progress every 3s
            val now = SystemClock.uptimeMillis()
            if (now - lastLog > 3000) {
                AutomationState.log("ℹ Still waiting for Get Started…")
                lastLog = now
            }

            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        AutomationState.log("⚠ Get Started not found in ${timeoutMs}ms — proceeding anyway")
        return true
    }

    private fun findByContentDescription(root: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (d.contains(target, ignoreCase = true) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    // ══════════════════════════════════════════════════════
    //  CLICK US BUTTON — US text 5s, then 5 hardcoded taps
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService): Boolean {
        // ═══ STEP 1: Try "US" as TEXT for 5 seconds ═══
        AutomationState.log("ℹ Trying 'US' as text (5s)…")
        val textEnd = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < textEnd) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                if (dialogOrSearchVisible(root)) {
                    AutomationState.log("✔ Country dialog visible (US text pass)")
                    return true
                }

                val usNode = findUsTextNode(root)
                if (usNode != null) {
                    val clickable = NodeFinder.findClickableParent(usNode) ?: usNode
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        AutomationState.log("ℹ US text clicked")
                        StepUtils.sleepMs(2000)
                        val r2 = service.rootInActiveWindow
                        if (r2 != null && dialogOrSearchVisible(r2)) {
                            AutomationState.log("✔ Dialog opened via US text")
                            return true
                        }
                    }
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        AutomationState.log("ℹ US text not found in 5s — using hardcoded location")

        // ═══ STEP 2: 5 hardcoded taps with progressive delays ═══
        val delays = longArrayOf(2000L, 3000L, 5000L, 5000L, 5000L)

        for (attempt in 0 until 5) {
            if (StepUtils.shouldStop()) return false

            // Check if dialog opened already
            val r0 = service.rootInActiveWindow
            if (r0 != null && dialogOrSearchVisible(r0)) {
                AutomationState.log("✔ Dialog visible (pre-tap #${attempt + 1})")
                return true
            }

            // Tap
            AutomationState.log("ℹ US tap #${attempt + 1}/5 at ($US_TAP_X, $US_TAP_Y)")
            tapAt(service, US_TAP_X, US_TAP_Y)

            // Wait with progressive delay
            StepUtils.sleepMs(delays[attempt])

            // Verify
            val r1 = service.rootInActiveWindow
            if (r1 != null && dialogOrSearchVisible(r1)) {
                AutomationState.log("✔ Dialog visible after tap #${attempt + 1}")
                return true
            }
        }

        AutomationState.log("✘ US button failed after 5 hardcoded attempts")
        return false
    }

    /** Find node whose text equals "US" (top-right quadrant) */
    private fun findUsTextNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            if (t.equals("US", true) && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                // top 30% and right 40%
                if (r.centerY() < rootR.top + rootR.height() * 0.30 &&
                    r.centerX() > rootR.left + rootR.width() * 0.55) {
                    return n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun dialogOrSearchVisible(root: AccessibilityNodeInfo): Boolean {
        if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) return true
        if (findByExactText(root, "Search") != null) return true
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
            AutomationState.log("✘ tapAt: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  TYPE COUNTRY
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Country: $countryName (from $countryNameRaw)")

        var searchNode: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                searchNode = findByExactText(root, "Search")
                if (searchNode != null) break
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (searchNode == null) {
            AutomationState.log("✘ 'Search' text not found")
            return false
        }

        val clickTarget = NodeFinder.findClickableParent(searchNode) ?: searchNode
        if (!clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            NodeFinder.click(searchNode)
        }
        AutomationState.log("ℹ Search clicked")
        StepUtils.sleepMs(700)

        var inputField = findEditableUnder(service.rootInActiveWindow) ?: searchNode
        NodeFinder.setText(inputField, countryName)
        AutomationState.log("ℹ Typed: $countryName")
        StepUtils.sleepMs(1800)

        val end = SystemClock.uptimeMillis() + 4500
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val r = service.rootInActiveWindow
            val match = findCountryResult(r, countryName, countryIso)
            if (match != null && NodeFinder.click(match)) {
                AutomationState.log("✔ Country clicked: $countryName")
                StepUtils.sleepMs(1500)
                return true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ Country result not found")
        return false
    }

    private fun findByExactText(root: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            if (t.equals(target, ignoreCase = true) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findEditableUnder(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun cleanCountryName(raw: String): String {
        return raw.replace(Regex("[0-9]+(-[0-9]+)*$"), "").trim()
    }

    private fun findCountryResult(root: AccessibilityNodeInfo?, name: String, iso: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString() ?: ""
            if (t.contains(name, true) &&
                (iso.isBlank() || t.contains("($iso)", true))
            ) {
                return NodeFinder.findClickableParent(n) ?: n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    // ══════════════════════════════════════════════════════
    //  TYPE NUMBER
    // ══════════════════════════════════════════════════════
    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        national = national.replace(" ", "").replace("-", "")

        StepUtils.sleepMs(1500)

        var phoneLabel: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                phoneLabel = findByTextContainsExact(root, "phone number")
                if (phoneLabel != null) break
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (phoneLabel == null) {
            AutomationState.log("✘ 'phone number' not found")
            return false
        }

        val clickTarget = NodeFinder.findClickableParent(phoneLabel) ?: phoneLabel
        clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        StepUtils.sleepMs(600)

        val inputField = findEditableUnder(service.rootInActiveWindow) ?: phoneLabel
        NodeFinder.setText(inputField, national)
        AutomationState.log("ℹ Number typed: $national")
        StepUtils.sleepMs(800)
        return true
    }

    private fun findByTextContainsExact(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lower = needle.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.lowercase() ?: ""
            if (t.contains(lower) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    // ══════════════════════════════════════════════════════
    //  CONTINUE
    // ══════════════════════════════════════════════════════
    fun tapContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, "Continue", "Next") ?: return false
        if (NodeFinder.click(node)) {
            AutomationState.log("ℹ Continue tapped")
            return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  RESULT
    // ══════════════════════════════════════════════════════
    enum class Result { OTP_SCREEN, ERROR, TIMEOUT }

    fun checkResult(service: AccessibilityService, timeoutMs: Long = 10000): Result {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var errorFlag = false
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return Result.TIMEOUT

            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                    return Result.OTP_SCREEN
                }
                if (!errorFlag && NodeFinder.containsAny(
                        root,
                        "Something went wrong", "can't be used", "not valid",
                        "invalid", "Enter a valid", "try again", "Try again"
                    )
                ) errorFlag = true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return if (errorFlag) Result.ERROR else Result.TIMEOUT
    }

    fun tapResendTwice(service: AccessibilityService) {
        repeat(2) { attempt ->
            val root = service.rootInActiveWindow
            val node = NodeFinder.findByText(root, "Resend Code", "Resend code", "Resend")
            if (node != null && NodeFinder.click(node)) {
                AutomationState.log("ℹ Resend (${attempt + 1}/2)")
            }
            StepUtils.sleepMs(5000)
        }
    }

    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(800)
        } catch (_: Exception) {}
    }
}
