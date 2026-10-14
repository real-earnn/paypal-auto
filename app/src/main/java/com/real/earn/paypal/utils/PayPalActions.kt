package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import com.real.earn.paypal.service.AutomationState

/**
 * All individual PayPal screen actions: launch, dismiss popups,
 * country select, number entry, resend detection.
 */
object PayPalActions {

    private const val TAG = "PayPalActions"
    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    // ── Launch PayPal ─────────────────────────────────────
    fun launchPayPal(service: AccessibilityService): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(PAYPAL_PKG)
            if (intent == null) {
                AutomationState.log("✘ PayPal app installed nahi hai")
                return false
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            service.startActivity(intent)
            Thread.sleep(1500)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Launch error: ${e.message}")
            false
        }
    }

    // ── Handle Get Started / Welcome screen ───────────────
    fun handleGetStartedIfPresent(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val root = service.rootInActiveWindow ?: run { Thread.sleep(400); return@run null }
            if (root != null) {
                // Number screen?
                val hasNumberScreen = NodeFinder.containsAny(
                    root,
                    "Let's start with your email or number",
                    "Email or phone number",
                    "email or number"
                )
                if (hasNumberScreen) return true

                // Get Started?
                if (NodeFinder.containsAny(root, "Get Started")) {
                    NodeFinder.clickAny(root, "Get Started")
                    Thread.sleep(2000)
                    return true
                }
            }
            Thread.sleep(400)
        }
        return false
    }

    // ── Wait for Continue button loader to stop ───────────
    fun waitForContinueReady(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val root = service.rootInActiveWindow ?: run { Thread.sleep(400); return@run null }
            if (root != null) {
                val continueNode = NodeFinder.findByText(root, "Continue")
                if (continueNode != null && continueNode.isEnabled) {
                    // Loader is gone — Continue is clickable
                    return true
                }
            }
            Thread.sleep(400)
        }
        return false
    }

    // ── Dismiss Gmail / any popup (X button) ──────────────
    fun dismissPopupsIfAny(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val closeTexts = arrayOf(
            "Close", "Cancel", "Dismiss", "X", "✕", "✖", "×", "No thanks", "Not now"
        )

        // Text based
        NodeFinder.findByText(root, *closeTexts)?.let {
            if (NodeFinder.click(it)) {
                AutomationState.log("ℹ Popup dismissed")
                Thread.sleep(800)
                return true
            }
        }

        // Description based
        for (text in closeTexts) {
            val node = NodeFinder.findByTextContains(root, text)
            if (node != null && NodeFinder.click(node)) {
                AutomationState.log("ℹ Popup dismissed (desc)")
                Thread.sleep(800)
                return true
            }
        }
        return false
    }

    // ── Open country selector (top-right) ─────────────────
    fun openCountrySelector(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false

        // Country code shown as "US", "PK", etc. — top right
        val commonCodes = arrayOf(
            "US", "PK", "MY", "GB", "IN", "BD", "ID", "EG", "SA", "AE",
            "TR", "RU", "VN", "TH", "PH", "NG", "KE", "GH", "ZA"
        )
        for (code in commonCodes) {
            val node = NodeFinder.findByText(root, code)
            if (node != null && NodeFinder.isVisible(node)) {
                if (NodeFinder.click(node)) {
                    AutomationState.log("ℹ Country selector opened")
                    Thread.sleep(1200)
                    return true
                }
            }
        }

        // Fallback: content description contains "country"
        val node = NodeFinder.findByTextContains(root, "country", "residence")
        if (node != null && NodeFinder.click(node)) {
            AutomationState.log("ℹ Country selector opened (desc)")
            Thread.sleep(1200)
            return true
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
            // Find search field
            val root = service.rootInActiveWindow ?: return false
            val search = NodeFinder.findEditText(root) ?: run {
                AutomationState.log("✘ Search field nahi mila")
                return false
            }

            if (!NodeFinder.setText(search, countryName)) {
                // Try typing via clipboard paste not possible; try ACTION_SET_TEXT again
                NodeFinder.setText(search, countryName)
            }
            Thread.sleep(1200)

            // Find matching result — must match BOTH name and ISO code
            val r2 = service.rootInActiveWindow ?: return false
            val matches = findCountryResult(r2, countryName, countryIso)
            if (matches != null) {
                NodeFinder.click(matches)
                AutomationState.log("✔ Country selected: $countryName ($countryIso)")
                Thread.sleep(1500)
                return true
            }

            AutomationState.log("✘ Country result nahi mila")
            return false
        } catch (e: Exception) {
            AutomationState.log("✘ selectCountry error: ${e.message}")
            return false
        }
    }

    private fun findCountryResult(
        root: android.view.accessibility.AccessibilityNodeInfo,
        countryName: String,
        countryIso: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // Look for text like "Pakistan (PK)" or line with country name
        // then verify the ISO in parentheses matches.
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString() ?: ""
            if (t.contains(countryName, ignoreCase = true)) {
                // Check ISO match if present
                val hasIso = t.contains("($countryIso)", ignoreCase = true)
                    || t.contains(countryIso, ignoreCase = true)
                if (hasIso || countryIso.isBlank()) {
                    return NodeFinder.findClickableParent(n) ?: n
                }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // ── Fill phone number (national, without dialing code) ──
    fun fillNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        try {
            // Strip "+" and country code
            var national = fullNumber.trim().removePrefix("+")
            if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
                national = national.substring(dialingCode.length)
            }
            national = national.trim()

            val root = service.rootInActiveWindow ?: return false
            val edit = NodeFinder.findEditText(root) ?: run {
                AutomationState.log("✘ Number input field nahi mila")
                return false
            }

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

    // ── Detect result screen ──────────────────────────────
    enum class Result { OTP_SCREEN, ERROR, TIMEOUT, UNKNOWN }

    fun waitForResult(service: AccessibilityService, timeoutMs: Long = 10000): Result {
        val end = System.currentTimeMillis() + timeoutMs
        var firstError = false
        while (System.currentTimeMillis() < end) {
            val root = service.rootInActiveWindow ?: run { Thread.sleep(400); return@run null }
            if (root != null) {
                // OTP screen — "Resend Code" present
                if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                    return Result.OTP_SCREEN
                }

                // Red error indicators
                if (!firstError && NodeFinder.containsAny(
                        root,
                        "Something went wrong",
                        "can't be used", "cannot be used",
                        "not valid", "invalid",
                        "try again", "Try again",
                        "error", "Error"
                    )
                ) {
                    firstError = true
                    // Give a bit more time — maybe screen changes
                }
            }
            Thread.sleep(400)
        }
        return if (firstError) Result.ERROR else Result.TIMEOUT
    }

    // ── Resend code 2x ────────────────────────────────────
    fun tapResendTwice(service: AccessibilityService) {
        repeat(2) { attempt ->
            val root = service.rootInActiveWindow
            val node = NodeFinder.findByText(root, "Resend Code", "Resend code", "Resend")
            if (node != null) {
                NodeFinder.click(node)
                AutomationState.log("ℹ Resend tapped (${attempt + 1}/2)")
            } else {
                AutomationState.log("ℹ Resend button nahi mila (${attempt + 1}/2)")
            }
            Thread.sleep(5000)
        }
    }

    // ── Close PayPal app (swipe from recents) ─────────────
    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Thread.sleep(500)
        } catch (_: Exception) { }
    }
}
