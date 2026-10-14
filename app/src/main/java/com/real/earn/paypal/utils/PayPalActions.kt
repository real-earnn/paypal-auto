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
            StepUtils.waitForPackageOrText(
                service, "paypal", 6000,
                "Get Started", "email or number", "Email or phone number",
                "Let's start", "Country of residence"
            )
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  GMAIL POPUP DISMISS (STRICT: X only if same row has "PayPal" on left)
    // ══════════════════════════════════════════════════════
    /**
     * Sirf wahi X click karega jis row mein baayin taraf "PayPal" likha ho.
     * Search dialog / country selector ka X skip hoga.
     */
    fun dismissGmailPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false

        // Step 1: Find "PayPal" text node in top area (popup header)
        val paypalNode = findPaypalHeaderNode(root) ?: return false

        val pr = Rect(); paypalNode.getBoundsInScreen(pr)

        // Step 2: Find close-X in same row (within 80px Y) to the right
        val closeTexts = arrayOf("✕", "✖", "×", "X", "✗", "❌", "Close", "close")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""

            val isClose = closeTexts.any { it.equals(t, true) || it.equals(d, true) }
            if (isClose) {
                val r = Rect(); n.getBoundsInScreen(r)
                val sameRow = Math.abs(r.centerY() - pr.centerY()) < 100
                val onRight = r.centerX() > pr.centerX()
                if (sameRow && onRight) {
                    if (NodeFinder.click(n)) {
                        AutomationState.log("ℹ Gmail popup closed (PayPal header verified)")
                        StepUtils.sleepMs(700)
                        return true
                    }
                }
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun findPaypalHeaderNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Find "PayPal" in top 25% of screen
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val topLimit = rootR.top + rootR.height() * 0.25

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            if (t.equals("PayPal", true)) {
                val r = Rect(); n.getBoundsInScreen(r)
                if (r.centerY() < topLimit) return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** Simple popup dismiss — X/Close anywhere. Use after country select, not during. */
    fun dismissPopupsLoop(service: AccessibilityService, durationMs: Long) {
        val end = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return
            // Try strict Gmail first
            if (!dismissGmailPopup(service)) {
                // Then generic
                dismissGenericPopup(service)
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
    }

    private fun dismissGenericPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val words = arrayOf("Close", "Cancel", "Dismiss", "No thanks", "Not now", "Skip", "✕", "✖", "×")
        NodeFinder.findByText(root, *words)?.let {
            if (NodeFinder.click(it)) return true
        }
        for (w in words) {
            val n = NodeFinder.findByTextContains(root, w)
            if (n != null && NodeFinder.isVisible(n) && NodeFinder.click(n)) return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  TAP GET STARTED (if present)
    // ══════════════════════════════════════════════════════
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            dismissGmailPopup(service)

            val root = service.rootInActiveWindow
            if (root != null) {
                // Already showing something after Get Started?
                if (findCountryCodeNode(root) != null) return true
                val hasInput = NodeFinder.findEditText(root) != null
                if (hasInput) return true

                val node = NodeFinder.findByText(root, "Get Started")
                    ?: NodeFinder.findByTextContains(root, "Get Started")
                if (node != null && NodeFinder.click(node)) {
                    AutomationState.log("ℹ Get Started tapped")
                    StepUtils.sleepMs(1500)
                    return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return true
    }

    // ══════════════════════════════════════════════════════
    //  FIND "US" or any country code top-right
    // ══════════════════════════════════════════════════════
    private fun findCountryCodeNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN", "KH", "KZ"
        )
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        for (code in codes) {
            val node = NodeFinder.findByText(root, code) ?: continue
            if (!NodeFinder.isVisible(node)) continue
            val r = Rect(); node.getBoundsInScreen(r)
            val inTop = r.centerY() < rootR.top + rootR.height() * 0.30
            val inRight = r.centerX() > rootR.left + rootR.width() * 0.50
            if (inTop && inRight) return node
        }
        return null
    }

    // ══════════════════════════════════════════════════════
    //  CLICK US / TOP-RIGHT CORNER FALLBACK
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 10000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var usedCornerFallback = false

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            // Popup first
            dismissGmailPopup(service)

            val root = service.rootInActiveWindow
            if (root != null) {
                // Dialog already open?
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    AutomationState.log("ℹ Country dialog open")
                    return true
                }

                // Try direct "US" text
                val node = findCountryCodeNode(root)
                if (node != null) {
                    val clickable = NodeFinder.findClickableParent(node) ?: node
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        AutomationState.log("ℹ Country code clicked (text match)")
                        StepUtils.sleepMs(800)
                        if (waitForCountryDialog(service, 3000)) return true
                    }
                }
            }

            // Halfway through timeout → try corner tap
            val elapsed = SystemClock.uptimeMillis() - (end - timeoutMs)
            if (!usedCornerFallback && elapsed > timeoutMs / 2) {
                AutomationState.log("ℹ US text not found — trying top-right corner")
                if (tapTopRightCorner(service)) {
                    StepUtils.sleepMs(1000)
                    if (waitForCountryDialog(service, 3000)) return true
                }
                usedCornerFallback = true
            }

            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ US button not found")
        return false
    }

    private fun tapTopRightCorner(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val r = Rect(); root.getBoundsInScreen(r)

        // Leave ~60px from right, ~180px from top (status bar + margin)
        val x = r.right - 70
        val y = r.top + 190

        AutomationState.log("ℹ Tap at ($x, $y)")

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
            AutomationState.log("✘ Corner tap fail: ${e.message}")
            false
        }
    }

    private fun waitForCountryDialog(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region") ||
                    NodeFinder.findEditText(root) != null) {
                    StepUtils.sleepMs(700)
                    return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  TYPE COUNTRY (name cleaned from "Malaysia10" → "Malaysia")
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Search: $countryName (from $countryNameRaw)")

        dismissGmailPopup(service)
        StepUtils.sleepMs(400)

        // Find search field
        var search: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false
            dismissGmailPopup(service)
            val root = service.rootInActiveWindow
            search = NodeFinder.findEditText(root)
            if (search != null) break
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        if (search == null) {
            AutomationState.log("✘ Search field not found")
            return false
        }

        // Click + type
        NodeFinder.click(search)
        StepUtils.sleepMs(500)
        NodeFinder.setText(search, countryName)
        StepUtils.sleepMs(1500)

        // Result
        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            dismissGmailPopup(service)

            val r = service.rootInActiveWindow
            val match = findCountryResult(r, countryName, countryIso)
            if (match != null && NodeFinder.click(match)) {
                AutomationState.log("✔ Country clicked: $countryName")
                StepUtils.sleepMs(1200)
                return true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ Country result not found")
        return false
    }

    /** "Malaysia10" → "Malaysia" ; "Nigeria10-1" → "Nigeria" ; "sri0" → "sri" */
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
    //  WAIT + CLOSE GMAIL POPUP
    // ══════════════════════════════════════════════════════
    fun waitAndClosePopup(service: AccessibilityService, timeoutMs: Long = 6000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (dismissGmailPopup(service)) return true
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
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

        dismissGmailPopup(service)
        StepUtils.sleepMs(500)

        var edit: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            dismissGmailPopup(service)
            val root = service.rootInActiveWindow
            edit = NodeFinder.findEditText(root)
            if (edit != null) break
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        if (edit == null) {
            AutomationState.log("✘ Number field not found")
            return false
        }
        NodeFinder.click(edit)
        StepUtils.sleepMs(400)
        NodeFinder.setText(edit, national)
        AutomationState.log("ℹ Number typed: $national")
        StepUtils.sleepMs(700)
        return true
    }

    // ══════════════════════════════════════════════════════
    //  CONTINUE
    // ══════════════════════════════════════════════════════
    fun tapContinue(service: AccessibilityService): Boolean {
        dismissGmailPopup(service)
        StepUtils.sleepMs(400)

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
