package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
    //  DISMISS ANY POPUP (Gmail etc.) — called everywhere
    // ══════════════════════════════════════════════════════
    /**
     * Ek try mein koi bhi close/X button dhoondo aur click karo.
     * Return true if a popup was closed.
     */
    fun dismissAnyPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false

        // Priority 1: Text/desc based "close" buttons
        val closeWords = arrayOf(
            "Close", "Cancel", "Dismiss", "No thanks", "Not now", "Skip",
            "✕", "✖", "×", "X", "✗", "❌", "❎"
        )

        // Exact text
        NodeFinder.findByText(root, *closeWords)?.let { n ->
            if (NodeFinder.click(n)) {
                AutomationState.log("ℹ Popup closed (text)")
                return true
            }
        }

        // Contains text or desc
        for (w in closeWords) {
            val n = NodeFinder.findByTextContains(root, w)
            if (n != null && NodeFinder.isVisible(n) && NodeFinder.click(n)) {
                AutomationState.log("ℹ Popup closed (contains)")
                return true
            }
        }

        // Priority 2: view-id based (common close buttons)
        val ids = arrayOf(
            "close", "dismiss", "btnClose", "btn_close",
            "imageButton", "close_button", "imgClose", "iv_close"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("com.paypal.android.p2pmobile:id/$id")
            nodes.firstOrNull()?.let { n ->
                if (n.isVisibleToUser && NodeFinder.click(n)) {
                    AutomationState.log("ℹ Popup closed (id $id)")
                    return true
                }
            }
        }

        return false
    }

    /** Loop dismiss for N ms. Good for waiting periods. */
    fun dismissPopupsLoop(service: AccessibilityService, durationMs: Long) {
        val end = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return
            dismissAnyPopup(service)
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
    }

    // ══════════════════════════════════════════════════════
    //  TAP "GET STARTED" (if present)
    // ══════════════════════════════════════════════════════
    /**
     * Waits up to timeoutMs for Get Started. Clicks it once. Returns true if clicked.
     * If number screen / US button already visible → returns true immediately.
     */
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            // Popup first
            if (dismissAnyPopup(service)) {
                StepUtils.sleepMs(600)
                continue
            }

            val root = service.rootInActiveWindow
            if (root != null) {
                // Already on number screen? Then no need
                if (findCountryCodeNode(root) != null) {
                    AutomationState.log("ℹ Already on number screen")
                    return true
                }
                val hasInput = NodeFinder.findEditText(root) != null
                if (hasInput) {
                    AutomationState.log("ℹ Input already visible")
                    return true
                }

                // Tap Get Started
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
        AutomationState.log("ℹ Get Started not present — continuing")
        return true   // proceed anyway
    }

    // ══════════════════════════════════════════════════════
    //  FIND COUNTRY CODE NODE (US/PK/etc top-right)
    // ══════════════════════════════════════════════════════
    private fun findCountryCodeNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN", "KH", "KZ"
        )
        val rootR = Rect(); root.getBoundsInScreen(rootR)

        // First: check if dialog already open
        if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
            return root
        }

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
    //  CLICK US BUTTON (continuous dismiss + retry)
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 10000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            // Popup dismiss first (Gmail etc.)
            if (dismissAnyPopup(service)) {
                StepUtils.sleepMs(700)
                continue
            }

            val root = service.rootInActiveWindow
            if (root != null) {
                // Dialog already open?
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    AutomationState.log("ℹ Country dialog open")
                    return true
                }
                // Find & click country code node
                val node = findCountryCodeNode(root)
                if (node != null && node !== root) {
                    val clickable = NodeFinder.findClickableParent(node) ?: node
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        AutomationState.log("ℹ US/country code clicked")
                        // wait for dialog
                        val dEnd = SystemClock.uptimeMillis() + 3000
                        while (SystemClock.uptimeMillis() < dEnd) {
                            val r2 = service.rootInActiveWindow
                            if (r2 != null && (
                                NodeFinder.containsAny(r2, "Country of residence", "Country/region") ||
                                NodeFinder.findEditText(r2) != null
                            )) {
                                AutomationState.log("✔ Country dialog appeared")
                                return true
                            }
                            dismissAnyPopup(service)
                            StepUtils.sleepMs(StepUtils.POLL_MS)
                        }
                    }
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ US button not found")
        return false
    }

    // ══════════════════════════════════════════════════════
    //  SEARCH + SELECT COUNTRY
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryName: String,
        countryIso: String
    ): Boolean {
        // Popup first
        dismissAnyPopup(service)
        StepUtils.sleepMs(400)

        // 1. Find search field
        var search: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false
            dismissAnyPopup(service)
            val root = service.rootInActiveWindow
            search = NodeFinder.findEditText(root)
            if (search != null) break
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        if (search == null) {
            AutomationState.log("✘ Search field not found")
            return false
        }

        // 2. Click + type
        NodeFinder.click(search)
        StepUtils.sleepMs(500)
        NodeFinder.setText(search, countryName)
        AutomationState.log("ℹ Typed: $countryName")
        StepUtils.sleepMs(1500)

        // 3. Find & click result
        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            dismissAnyPopup(service)

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
    //  WAIT + CLOSE GMAIL POPUP (after country select)
    // ══════════════════════════════════════════════════════
    fun waitAndClosePopup(service: AccessibilityService, timeoutMs: Long = 5000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (dismissAnyPopup(service)) {
                StepUtils.sleepMs(700)
                return true
            }
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

        dismissAnyPopup(service)
        StepUtils.sleepMs(500)

        var edit: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            dismissAnyPopup(service)
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
        dismissAnyPopup(service)
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
