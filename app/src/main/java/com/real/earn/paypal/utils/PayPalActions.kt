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
            wait(1500)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Launch error: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  WAIT FOR NUMBER SCREEN  (Get Started → number screen)
    // ══════════════════════════════════════════════════════
    fun waitForNumberScreen(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var getStartedClicked = false
        var screenSeenAt = 0L

        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root == null) { wait(300); continue }

            // Get Started? → tap once, wait for transition
            if (!getStartedClicked && NodeFinder.containsAny(root, "Get Started")) {
                if (NodeFinder.clickAny(root, "Get Started")) {
                    AutomationState.log("ℹ Get Started tapped")
                    getStartedClicked = true
                    wait(2500)
                    continue
                }
            }

            // Number screen present?
            val hasText = NodeFinder.containsAny(
                root, "email or number", "Email or phone number", "Let's start"
            )
            if (hasText) {
                val input = NodeFinder.findEditText(root)
                if (input != null) {
                    if (screenSeenAt == 0L) screenSeenAt = SystemClock.uptimeMillis()
                    // Require 1.2s stability
                    if (SystemClock.uptimeMillis() - screenSeenAt > 1200) {
                        AutomationState.log("✔ Number screen ready")
                        return true
                    }
                }
            }
            wait(300)
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
                wait(700)
                return true
            }
        }
        for (t in closeTexts) {
            val n = NodeFinder.findByTextContains(root, t)
            if (n != null && NodeFinder.click(n)) {
                AutomationState.log("ℹ Popup dismissed (desc)")
                wait(700)
                return true
            }
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  OPEN COUNTRY SELECTOR  (click "US" top-right)
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
                for (code in codes) {
                    val node = NodeFinder.findByText(root, code)
                    if (node != null && NodeFinder.isVisible(node)) {
                        // Make sure we're not clicking something inside a dialog
                        if (isInsideDialog(root, node)) continue

                        val clickable = NodeFinder.findClickableParent(node) ?: node
                        if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            AutomationState.log("ℹ Country selector opened ($code)")
                            // Wait for dialog "Country of residence" or "Search"
                            if (waitForCountryDialog(service, 4000)) return true
                        }
                    }
                }
            }
            wait(400)
        }
        return false
    }

    private fun isInsideDialog(root: AccessibilityNodeInfo, node: AccessibilityNodeInfo): Boolean {
        // Quick heuristic — if the screen has "Country of residence" text, we're already in dialog
        return NodeFinder.containsAny(root, "Country of residence", "Country/region")
    }

    private fun waitForCountryDialog(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                // Dialog present if we see "Search" placeholder or "Country of residence"
                if (NodeFinder.containsAny(root, "Country of residence", "Search", "Country/region")) {
                    return true
                }
            }
            wait(300)
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  SELECT COUNTRY  (search field → type → match)
    // ══════════════════════════════════════════════════════
    fun selectCountry(
        service: AccessibilityService,
        countryName: String,
        countryIso: String
    ): Boolean {
        // ── STEP 1: Find search field ─────────────────────
        val root = service.rootInActiveWindow
        val search = NodeFinder.findEditText(root)
        if (search == null) {
            AutomationState.log("✘ Search field not found")
            return false
        }

        // ── STEP 2: Click search field ────────────────────
        NodeFinder.click(search)
        wait(600)

        // ── STEP 3: Type country name ─────────────────────
        if (!NodeFinder.setText(search, countryName)) {
            // retry
            NodeFinder.setText(search, countryName)
        }
        AutomationState.log("ℹ Typed: $countryName")
        wait(1800)

        // ── STEP 4: Wait for results and click match ──────
        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            val r = service.rootInActiveWindow
            val match = findCountryMatch(r, countryName, countryIso)
            if (match != null) {
                if (NodeFinder.click(match)) {
                    AutomationState.log("✔ Country selected: $countryName ($countryIso)")
                    // Wait for dialog to close
                    waitForDialogClose(service, 5000)
                    return true
                }
            }
            wait(400)
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
                val clickable = NodeFinder.findClickableParent(n) ?: n
                return clickable
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
                // If search field gone and "Country of residence" not present → dialog closed
                val hasDialog = NodeFinder.containsAny(root, "Country of residence", "Country/region")
                val hasSearch = NodeFinder.findEditText(root) != null
                if (!hasDialog && !hasSearch) return true
            }
            wait(400)
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
        wait(400)
        if (!NodeFinder.setText(edit, national)) {
            NodeFinder.setText(edit, national)
        }
        AutomationState.log("ℹ Number typed: $national")
        wait(700)
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
    //  WAIT FOR RESULT  (OTP / error / timeout)
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
            wait(400)
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
            wait(5000)
        }
    }

    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            wait(400)
        } catch (_: Exception) {}
    }

    private fun wait(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
