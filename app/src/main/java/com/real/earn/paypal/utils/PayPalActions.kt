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

    // US button hardcoded (pointer location se confirm kiya)
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
            // Wait for PayPal foreground OR any recognizable text
            StepUtils.waitForPackageOrText(
                service, "paypal", 8000,
                "Get Started", "email or number", "Email or phone number",
                "Let's start", "US"
            )
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  TAP GET STARTED — poll every 60ms, click IMMEDIATELY
    // ══════════════════════════════════════════════════════
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 10000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // Already past Get Started? (US visible)
                if (NodeFinder.containsAny(root, "US")) return true
                if (NodeFinder.containsAny(root, "Country of residence")) return true

                // Get Started — click instantly, no wait
                val node = NodeFinder.findByText(root, "Get Started")
                    ?: NodeFinder.findByTextContains(root, "Get Started")
                if (node != null && NodeFinder.click(node)) {
                    AutomationState.log("ℹ Get Started tapped")
                    return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("ℹ Get Started not seen — continuing")
        return true
    }

    // ══════════════════════════════════════════════════════
    //  CLICK US  — hardcoded (600, 125)
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var tapped = false
        var lastTapAt = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // Dialog open?
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    AutomationState.log("✔ Country dialog open")
                    return true
                }

                // Try direct US text click first
                val usNode = NodeFinder.findByText(root, "US")
                if (usNode != null && NodeFinder.isVisible(usNode)) {
                    val clickable = NodeFinder.findClickableParent(usNode) ?: usNode
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        AutomationState.log("ℹ US text clicked")
                        StepUtils.sleepMs(1000)
                        if (NodeFinder.containsAny(service.rootInActiveWindow,
                                "Country of residence", "Country/region")) {
                            return true
                        }
                    }
                }
            }

            // Tap hardcoded location (throttle to once every 2.5 sec)
            val now = SystemClock.uptimeMillis()
            if (!tapped || now - lastTapAt > 2500) {
                AutomationState.log("ℹ US tap at ($US_TAP_X, $US_TAP_Y)")
                tapAt(service, US_TAP_X, US_TAP_Y)
                lastTapAt = SystemClock.uptimeMillis()
                tapped = true
                StepUtils.sleepMs(1200)

                // Check dialog
                val r = service.rootInActiveWindow
                if (r != null && NodeFinder.containsAny(r,
                        "Country of residence", "Country/region")) {
                    AutomationState.log("✔ Country dialog open")
                    return true
                }
            }

            StepUtils.sleepMs(300)
        }
        AutomationState.log("✘ US button failed")
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
            AutomationState.log("✘ tapAt error: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  TYPE COUNTRY — find node by TEXT "Search", click, type
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Country: $countryName (from $countryNameRaw)")

        // Wait for "Search" text node to appear (poll)
        var searchNode: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // Find node whose TEXT == "Search" (case-insensitive)
                searchNode = findByExactText(root, "Search")
                    ?: findByExactText(root, "search")
                    ?: findByExactText(root, "SEARCH")
                if (searchNode != null) break
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (searchNode == null) {
            AutomationState.log("✘ 'Search' text not found")
            return false
        }

        // Click on the search node (or its clickable parent)
        val clickTarget = NodeFinder.findClickableParent(searchNode) ?: searchNode
        if (!clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            NodeFinder.click(searchNode)
        }
        AutomationState.log("ℹ Search clicked")
        StepUtils.sleepMs(500)

        // Now find the actual input field (editable) — could be the searchNode itself or near it
        var inputField = findEditableUnder(service.rootInActiveWindow)
        if (inputField == null) {
            // Fallback: searchNode itself might be editable
            inputField = searchNode
        }

        // Type the country name
        if (inputField != null) {
            NodeFinder.setText(inputField, countryName)
        } else {
            // Last resort: type via keyboard
            AutomationState.log("⚠ No editable field — trying direct")
        }
        AutomationState.log("ℹ Typed: $countryName")
        StepUtils.sleepMs(1500)

        // Find result — first the typed text repeats, then actual result
        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

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

    /** Find node whose text EQUALS target (case-insensitive) */
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

    /** Find any editable node on screen */
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
    //  TYPE NUMBER — find node by TEXT "phone number"
    // ══════════════════════════════════════════════════════
    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        national = national.replace(" ", "").replace("-", "")

        StepUtils.sleepMs(1500)   // wait for any Gmail popup to clear (watcher)

        // Find node with text containing "phone number" (small letters in app)
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
            AutomationState.log("✘ 'phone number' text not found")
            return false
        }

        // Click on it (or clickable parent — usually the input field)
        val clickTarget = NodeFinder.findClickableParent(phoneLabel) ?: phoneLabel
        clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        StepUtils.sleepMs(500)

        // Find editable field
        var inputField = findEditableUnder(service.rootInActiveWindow) ?: phoneLabel

        // Type number
        NodeFinder.setText(inputField, national)
        AutomationState.log("ℹ Number typed: $national")
        StepUtils.sleepMs(700)
        return true
    }

    /** Node whose text contains "phone number" (case-insensitive, both words) */
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
