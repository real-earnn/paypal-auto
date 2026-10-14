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
    //  GMAIL POPUP DISMISS — PayPal header + X same row
    // ══════════════════════════════════════════════════════
    /**
     * Robust popup dismiss:
     *  1. If "Choose an account" / "Sign-in options" text visible → it's Gmail popup
     *  2. Else if "PayPal" text visible anywhere → find X/close in same row (right side)
     */
    fun dismissGmailPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false

        // Method A: "Choose an account for PayPal" is Gmail popup
        if (NodeFinder.containsAny(root, "Choose an account", "Sign-in options")) {
            if (clickAnyCloseButton(root)) {
                AutomationState.log("ℹ Gmail popup closed (account chooser)")
                StepUtils.sleepMs(700)
                return true
            }
        }

        // Method B: Find "PayPal" text anywhere, then X in same Y row
        val paypalTexts = findAllByText(root, "PayPal")
        for (pp in paypalTexts) {
            if (clickCloseInSameRow(root, pp)) {
                AutomationState.log("ℹ Gmail popup closed (PayPal header)")
                StepUtils.sleepMs(700)
                return true
            }
        }

        return false
    }

    private fun findAllByText(root: AccessibilityNodeInfo?, target: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            if (t.equals(target, true) && n.isVisibleToUser) result.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return result
    }

    /**
     * Same Y row (±120px), to the right of "header" → click X.
     */
    private fun clickCloseInSameRow(root: AccessibilityNodeInfo, header: AccessibilityNodeInfo): Boolean {
        val hr = Rect(); header.getBoundsInScreen(hr)
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎", "Close", "close", "Cancel")

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""

            val isClose = closeWords.any { it.equals(t, true) || it.equals(d, true) }
            if (isClose && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                val sameRow = Math.abs(r.centerY() - hr.centerY()) < 120
                val onRight = r.centerX() > hr.centerX()
                if (sameRow && onRight) {
                    if (NodeFinder.click(n)) return true
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    /** Generic X click anywhere. Used as fallback. */
    private fun clickAnyCloseButton(root: AccessibilityNodeInfo): Boolean {
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎")
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

    // ══════════════════════════════════════════════════════
    //  TAP GET STARTED
    // ══════════════════════════════════════════════════════
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            dismissGmailPopup(service)

            val root = service.rootInActiveWindow
            if (root != null) {
                if (findCountryCodeNode(root) != null) return true

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
    //  FIND COUNTRY CODE NODE
    // ══════════════════════════════════════════════════════
    private fun findCountryCodeNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN", "KH", "KZ", "DZ"
        )
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        for (code in codes) {
            val node = NodeFinder.findByText(root, code) ?: continue
            if (!NodeFinder.isVisible(node)) continue
            val r = Rect(); node.getBoundsInScreen(r)
            val inTop = r.centerY() < rootR.top + rootR.height() * 0.35
            val inRight = r.centerX() > rootR.left + rootR.width() * 0.50
            if (inTop && inRight) return node
        }
        return null
    }

    // ══════════════════════════════════════════════════════
    //  CLICK US BUTTON (with popup dismiss + corner fallback)
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 10000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var cornerTried = false

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            // Try to close popup first
            if (dismissGmailPopup(service)) {
                StepUtils.sleepMs(800)
                continue
            }

            val root = service.rootInActiveWindow
            if (root != null) {
                // Already open?
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    AutomationState.log("ℹ Country dialog open")
                    return true
                }

                // Try "US" or any country code
                val node = findCountryCodeNode(root)
                if (node != null) {
                    val clickable = NodeFinder.findClickableParent(node) ?: node
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        AutomationState.log("ℹ Country code clicked (text)")
                        StepUtils.sleepMs(900)
                        if (waitForCountryDialog(service, 3000)) return true
                    }
                }
            }

            // Halfway through — try corner tap
            val elapsed = SystemClock.uptimeMillis() - (end - timeoutMs)
            if (!cornerTried && elapsed > timeoutMs / 2) {
                AutomationState.log("ℹ US not found — trying corner tap")
                if (tapTopRightCorner(service)) {
                    StepUtils.sleepMs(1200)
                    if (waitForCountryDialog(service, 3000)) return true
                }
                cornerTried = true
            }

            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ US button not found")
        return false
    }

    private fun tapTopRightCorner(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val r = Rect(); root.getBoundsInScreen(r)

        // 12% from right, 10% from top (below status bar, hits US area)
        val x = r.right - (r.width() * 0.12f).toInt()
        val y = r.top + (r.height() * 0.10f).toInt()

        AutomationState.log("ℹ Corner tap at ($x, $y)")

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
                    findSearchField(root) != null) {
                    StepUtils.sleepMs(700)
                    return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  FIND SEARCH FIELD (hint = "Search")
    // ══════════════════════════════════════════════════════
    private fun findSearchField(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser) {
                val hint = n.hintText?.toString()?.lowercase() ?: ""
                if (hint.contains("search")) return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** Field with hint containing email/phone/number → main number input */
    private fun findNumberField(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser) {
                val hint = n.hintText?.toString()?.lowercase() ?: ""
                if (hint.contains("email") || hint.contains("phone") || hint.contains("number"))
                    return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    // ══════════════════════════════════════════════════════
    //  TYPE COUNTRY (STRICT — verify dialog open before typing)
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Search: $countryName (from $countryNameRaw)")

        dismissGmailPopup(service)
        StepUtils.sleepMs(500)

        // ⚠️ VERIFY country dialog is actually open — search field with hint="Search"
        var search: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 3500
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false
            dismissGmailPopup(service)

            val root = service.rootInActiveWindow
            if (root != null) {
                // Positive signal: "Country of residence" visible OR search field with hint "Search"
                val dialogOpen = NodeFinder.containsAny(root, "Country of residence", "Country/region")
                val searchField = findSearchField(root)
                if (dialogOpen || searchField != null) {
                    search = searchField
                    if (search != null) break
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (search == null) {
            AutomationState.log("✘ Search field not found — aborting (dialog not open)")
            return false
        }

        // Click + type
        NodeFinder.click(search)
        StepUtils.sleepMs(500)
        NodeFinder.setText(search, countryName)
        StepUtils.sleepMs(1500)

        // Result click
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

    /** "Malaysia10" → "Malaysia" ; "Nigeria10-1" → "Nigeria" */
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
    //  WAIT + CLOSE POPUP
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
    //  TYPE NUMBER (STRICT — only into field with email/phone hint)
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
            // Strict: only match field with hint containing email/phone/number
            edit = findNumberField(root)
            if (edit != null) break
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (edit == null) {
            AutomationState.log("✘ Number field (email/phone hint) not found")
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
