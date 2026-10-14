package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object PayPalActions {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    // ══════════════════════════════════════════════════════
    //  LAUNCH
    // ══════════════════════════════════════════════════════
    fun launchPayPal(service: AccessibilityService): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(PAYPAL_PKG)
            if (intent == null) {
                AutomationState.log("✘ PayPal installed nahi")
                return false
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            service.startActivity(intent)
            sleepMs(1500)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Launch error: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  WAIT FOR NUMBER SCREEN
    // ══════════════════════════════════════════════════════
    fun waitForNumberScreen(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var getStartedClicked = false
        var screenSeenAt = 0L

        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root == null) { sleepMs(300); continue }

            if (!getStartedClicked && NodeFinder.containsAny(root, "Get Started")) {
                if (NodeFinder.clickAny(root, "Get Started")) {
                    AutomationState.log("ℹ Get Started tapped")
                    getStartedClicked = true
                    sleepMs(2500)
                    continue
                }
            }

            val hasText = NodeFinder.containsAny(
                root, "email or number", "Email or phone number", "Let's start"
            )
            if (hasText) {
                val input = NodeFinder.findEditText(root)
                if (input != null) {
                    if (screenSeenAt == 0L) screenSeenAt = SystemClock.uptimeMillis()
                    if (SystemClock.uptimeMillis() - screenSeenAt > 1200) {
                        AutomationState.log("✔ Number screen ready")
                        return true
                    }
                }
            }
            sleepMs(300)
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  DISMISS POPUPS
    // ══════════════════════════════════════════════════════
    fun dismissPopupsIfAny(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val closeTexts = arrayOf(
            "Close", "Cancel", "Dismiss", "X", "✕", "✖", "×",
            "No thanks", "Not now", "Skip"
        )
        NodeFinder.findByText(root, *closeTexts)?.let {
            if (NodeFinder.click(it)) {
                AutomationState.log("ℹ Popup dismissed")
                sleepMs(700)
                return true
            }
        }
        for (t in closeTexts) {
            val n = NodeFinder.findByTextContains(root, t)
            if (n != null && NodeFinder.click(n)) {
                AutomationState.log("ℹ Popup dismissed (desc)")
                sleepMs(700)
                return true
            }
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  OPEN COUNTRY SELECTOR
    // ══════════════════════════════════════════════════════
    fun openCountrySelector(service: AccessibilityService, timeoutMs: Long = 6000): Boolean {
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN"
        )

        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                // Skip if dialog already open
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    return true
                }
                for (code in codes) {
                    val node = NodeFinder.findByText(root, code)
                    if (node != null && NodeFinder.isVisible(node)) {
                        val clickable = NodeFinder.findClickableParent(node) ?: node
                        if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            AutomationState.log("ℹ Country selector opened ($code)")
                            if (waitForCountryDialog(service, 4000)) return true
                        }
                    }
                }
            }
            sleepMs(400)
        }
        return false
    }

    private fun waitForCountryDialog(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Country of residence", "Search", "Country/region")) {
                    return true
                }
            }
            sleepMs(300)
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  SELECT COUNTRY
    // ══════════════════════════════════════════════════════
    fun selectCountry(
        service: AccessibilityService,
        countryName: String,
        countryIso: String
    ): Boolean {
        val root = service.rootInActiveWindow
        val search = NodeFinder.findEditText(root)
        if (search == null) {
            AutomationState.log("✘ Search field not found")
            return false
        }

        NodeFinder.click(search)
        sleepMs(600)

        if (!NodeFinder.setText(search, countryName)) {
            NodeFinder.setText(search, countryName)
        }
        AutomationState.log("ℹ Typed: $countryName")
        sleepMs(1800)

        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            val r = service.rootInActiveWindow
            val match = findCountryMatch(r, countryName, countryIso)
            if (match != null) {
                if (NodeFinder.click(match)) {
                    AutomationState.log("✔ Country selected: $countryName ($countryIso)")
                    waitForDialogClose(service, 5000)
                    return true
                }
            }
            sleepMs(400)
        }
        AutomationState.log("✘ No match for $countryName ($countryIso)")
        return false
    }

    private fun findCountryMatch(
        root: AccessibilityNodeInfo?,
        countryName: String,
        countryIso: String
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString() ?: ""
            if (t.contains(countryName, ignoreCase = true) &&
                (countryIso.isBlank() || t.contains("($countryIso)", true) || t.contains(countryIso, true))
            ) {
                return NodeFinder.findClickableParent(n) ?: n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun waitForDialogClose(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                val hasDialog = NodeFinder.containsAny(root, "Country of residence", "Country/region")
                val hasSearch = NodeFinder.findEditText(root) != null
                if (!hasDialog && !hasSearch) return true
            }
            sleepMs(400)
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  FILL NUMBER
    // ══════════════════════════════════════════════════════
    fun fillNumber(
        service: AccessibilityService,
        fullNumber: String,
        dialingCode: String
    ): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        national = national.replace(" ", "").replace("-", "")

        val root = service.rootInActiveWindow ?: return false
        val edit = NodeFinder.findEditText(root)
        if (edit == null) {
            AutomationState.log("✘ Number field not found")
            return false
        }

        NodeFinder.click(edit)
        sleepMs(400)
        if (!NodeFinder.setText(edit, national)) {
            NodeFinder.setText(edit, national)
        }
        AutomationState.log("ℹ Number typed: $national")
        sleepMs(700)
        return true
    }

    // ══════════════════════════════════════════════════════
    //  TAP CONTINUE
    // ══════════════════════════════════════════════════════
    fun tapContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, "Continue", "Next")
        if (node != null && NodeFinder.click(node)) {
            AutomationState.log("ℹ Continue tapped")
            return true
        }
        AutomationState.log("✘ Continue not found")
        return false
    }

    // ══════════════════════════════════════════════════════
    //  WAIT FOR RESULT
    // ══════════════════════════════════════════════════════
    enum class Result { OTP_SCREEN, ERROR, TIMEOUT }

    fun waitForResult(service: AccessibilityService, timeoutMs: Long = 10000): Result {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var errorSeen = false
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                    return Result.OTP_SCREEN
                }
                if (!errorSeen && NodeFinder.containsAny(
                        root,
                        "Something went wrong", "can't be used", "not valid",
                        "invalid", "try again", "Try again", "Enter a valid"
                    )
                ) {
                    errorSeen = true
                }
            }
            sleepMs(400)
        }
        return if (errorSeen) Result.ERROR else Result.TIMEOUT
    }

    fun tapResendTwice(service: AccessibilityService) {
        repeat(2) { attempt ->
            val root = service.rootInActiveWindow
            val node = NodeFinder.findByText(root, "Resend Code", "Resend code", "Resend")
            if (node != null && NodeFinder.click(node)) {
                AutomationState.log("ℹ Resend (${attempt + 1}/2)")
            } else {
                AutomationState.log("ℹ Resend not found (${attempt + 1}/2)")
            }
            sleepMs(5000)
        }
    }

    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            sleepMs(400)
        } catch (_: Exception) {}
    }

    private fun sleepMs(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
