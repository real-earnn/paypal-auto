package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object PayPalActions {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    // ── Launch PayPal ─────────────────────────────────────
    fun launchPayPal(service: AccessibilityService): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(PAYPAL_PKG)
            if (intent == null) {
                AutomationState.log("✘ PayPal installed nahi hai")
                return false
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            service.startActivity(intent)
            Thread.sleep(1500)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Launch error: ${e.message}")
            false
        }
    }

    // ── Wait for number screen (Get Started → number screen) ──
    /**
     * Handles:
     *   - Wait for PayPal to launch
     *   - If "Get Started" → tap it, wait
     *   - Then wait for number screen with "email or number" text
     *   - Ensure number input field is present
     */
    fun waitForNumberScreen(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        var getStartedClicked = false
        var numberScreenSeenAt = 0L

        while (System.currentTimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root == null) {
                Thread.sleep(400)
                continue
            }

            // 1. Get Started button — click once
            if (!getStartedClicked && NodeFinder.containsAny(root, "Get Started")) {
                if (NodeFinder.clickAny(root, "Get Started")) {
                    AutomationState.log("ℹ Get Started tapped")
                    getStartedClicked = true
                    Thread.sleep(2500) // wait for transition
                    continue
                }
            }

            // 2. Number screen — check that both text AND input exist
            val hasNumberText = NodeFinder.containsAny(
                root,
                "email or number",
                "Email or phone number",
                "Let's start"
            )
            if (hasNumberText) {
                val input = NodeFinder.findEditText(root)
                if (input != null) {
                    if (numberScreenSeenAt == 0L) {
                        numberScreenSeenAt = System.currentTimeMillis()
                    }
                    // Wait another 1.5s to ensure screen is stable
                    if (System.currentTimeMillis() - numberScreenSeenAt > 1500) {
                        AutomationState.log("✔ Number screen ready")
                        return true
                    }
                }
            }

            Thread.sleep(400)
        }
        return false
    }

    // ── Dismiss popups ────────────────────────────────────
    fun dismissPopupsIfAny(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val closeTexts = arrayOf(
            "Close", "Cancel", "Dismiss", "X", "✕", "✖", "×",
            "No thanks", "Not now", "Skip"
        )
        NodeFinder.findByText(root, *closeTexts)?.let {
            if (NodeFinder.click(it)) {
                AutomationState.log("ℹ Popup dismissed")
                Thread.sleep(700)
                return true
            }
        }
        for (t in closeTexts) {
            val n = NodeFinder.findByTextContains(root, t)
            if (n != null && NodeFinder.click(n)) {
                AutomationState.log("ℹ Popup dismissed (desc)")
                Thread.sleep(700)
                return true
            }
        }
        return false
    }

    // ── Click country selector (top-right corner, shows "US" by default) ──
    fun openCountrySelector(service: AccessibilityService, timeoutMs: Long = 6000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                // Try to find 2-letter country code near top-right corner
                if (tryClickCountryCode(root)) {
                    AutomationState.log("ℹ Country selector opened")
                    Thread.sleep(1500)
                    return true
                }
            }
            Thread.sleep(400)
        }
        return false
    }

    private fun tryClickCountryCode(root: AccessibilityNodeInfo): Boolean {
        // Common country codes PayPal shows
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN"
        )
        for (code in codes) {
            val node = NodeFinder.findByText(root, code)
            if (node != null && NodeFinder.isVisible(node)) {
                // Make sure the parent is clickable (it's usually a small button)
                val clickable = NodeFinder.findClickableParent(node) ?: node
                if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }
        return false
    }

    // ── Search + select country ───────────────────────────
    fun selectCountry(
        service: AccessibilityService,
        countryName: String,
        countryIso: String
    ): Boolean {
        try {
            // Wait for search field to appear
            Thread.sleep(1500)

            val root = service.rootInActiveWindow
            val search = NodeFinder.findEditText(root)
            if (search == null) {
                AutomationState.log("✘ Search field nahi mila")
                return false
            }

            // Click search field first to focus
            NodeFinder.click(search)
            Thread.sleep(500)

            // Type country name
            if (!NodeFinder.setText(search, countryName)) {
                NodeFinder.setText(search, countryName)
            }
            AutomationState.log("ℹ Typed country: $countryName")
            Thread.sleep(1800)

            // Find matching result
            val r2 = service.rootInActiveWindow
            val match = findCountryResult(r2, countryName, countryIso)
            if (match == null) {
                AutomationState.log("✘ Result nahi mila: $countryName ($countryIso)")
                return false
            }
            if (!NodeFinder.click(match)) {
                AutomationState.log("✘ Result click fail")
                return false
            }
            AutomationState.log("✔ Country selected: $countryName")
            Thread.sleep(2200)

            // Verify: check that country ISO now appears in the screen
            if (countryIso.isNotEmpty()) {
                val verifyRoot = service.rootInActiveWindow
                if (verifyRoot != null) {
                    val seen = NodeFinder.containsAny(verifyRoot, countryIso)
                    if (seen) {
                        AutomationState.log("✔ Verified: $countryIso shown")
                    } else {
                        AutomationState.log("⚠ $countryIso verify — proceeding anyway")
                    }
                }
            }
            return true
        } catch (e: Exception) {
            AutomationState.log("✘ selectCountry error: ${e.message}")
            return false
        }
    }

    private fun findCountryResult(
        root: AccessibilityNodeInfo,
        countryName: String,
        countryIso: String
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString() ?: ""

            if (t.contains(countryName, ignoreCase = true)) {
                // Verify ISO match if we have one
                if (countryIso.isBlank() ||
                    t.contains("($countryIso)", ignoreCase = true)
                ) {
                    return NodeFinder.findClickableParent(n) ?: n
                }
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // ── Fill phone number ─────────────────────────────────
    fun fillNumber(
        service: AccessibilityService,
        fullNumber: String,
        dialingCode: String
    ): Boolean {
        try {
            var national = fullNumber.trim().removePrefix("+")
            if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
                national = national.substring(dialingCode.length)
            }
            national = national.trim().replace(" ", "").replace("-", "")

            val root = service.rootInActiveWindow ?: return false
            val edit = NodeFinder.findEditText(root)
            if (edit == null) {
                AutomationState.log("✘ Number field nahi mila")
                return false
            }

            NodeFinder.click(edit)
            Thread.sleep(300)
            if (!NodeFinder.setText(edit, national)) {
                NodeFinder.setText(edit, national)
            }
            AutomationState.log("ℹ Number typed: $national")
            Thread.sleep(800)
            return true
        } catch (e: Exception) {
            AutomationState.log("✘ fillNumber error: ${e.message}")
            return false
        }
    }

    // ── Tap Continue ──────────────────────────────────────
    fun tapContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, "Continue", "Next")
        if (node != null && NodeFinder.click(node)) {
            AutomationState.log("ℹ Continue tapped")
            return true
        }
        return false
    }

    // ── Wait for result ───────────────────────────────────
    enum class Result { OTP_SCREEN, ERROR, TIMEOUT }

    fun waitForResult(service: AccessibilityService, timeoutMs: Long = 10000): Result {
        val end = System.currentTimeMillis() + timeoutMs
        var errorSeen = false
        while (System.currentTimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                    return Result.OTP_SCREEN
                }
                if (!errorSeen && NodeFinder.containsAny(
                        root,
                        "Something went wrong", "can't be used", "not valid",
                        "invalid", "try again", "Try again"
                    )
                ) {
                    errorSeen = true
                }
            }
            Thread.sleep(400)
        }
        return if (errorSeen) Result.ERROR else Result.TIMEOUT
    }

    // ── Resend code 2x ────────────────────────────────────
    fun tapResendTwice(service: AccessibilityService) {
        repeat(2) { attempt ->
            val root = service.rootInActiveWindow
            val node = NodeFinder.findByText(root, "Resend Code", "Resend code", "Resend")
            if (node != null && NodeFinder.click(node)) {
                AutomationState.log("ℹ Resend tapped (${attempt + 1}/2)")
            } else {
                AutomationState.log("ℹ Resend not found (${attempt + 1}/2)")
            }
            Thread.sleep(5000)
        }
    }

    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Thread.sleep(400)
        } catch (_: Exception) {}
    }
}
