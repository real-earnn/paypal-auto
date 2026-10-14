package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object PayPalActions {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    // ── Launch PayPal, wait until foreground ──
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

            val end = SystemClock.uptimeMillis() + 6000
            while (SystemClock.uptimeMillis() < end) {
                val root = service.rootInActiveWindow
                val pkg = root?.packageName?.toString() ?: ""
                if (pkg.contains("paypal")) {
                    sleepMs(600)
                    return true
                }
                sleepMs(300)
            }
            false
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    // ── Wait Get Started → tap → number screen ──
    fun handleGetStartedAndWaitNumber(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var clicked = false
        var stableAt = 0L

        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow ?: run { sleepMs(300); continue }

            // Get Started (once)
            if (!clicked && NodeFinder.containsAny(root, "Get Started")) {
                if (NodeFinder.clickAny(root, "Get Started")) {
                    AutomationState.log("ℹ Get Started tapped")
                    clicked = true
                    sleepMs(2500)
                    continue
                }
            }

            // Number screen: has text + EditText
            val hasText = NodeFinder.containsAny(root, "email or number", "Email or phone number")
            val hasEdit = NodeFinder.findEditText(root) != null
            if (hasText && hasEdit) {
                if (stableAt == 0L) stableAt = SystemClock.uptimeMillis()
                if (SystemClock.uptimeMillis() - stableAt > 1200) {
                    AutomationState.log("✔ On number screen")
                    return true
                }
            } else stableAt = 0L

            sleepMs(300)
        }
        return false
    }

    // ── Click US button (top-right quadrant) ──
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN", "KH", "KZ"
        )
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            // Close Gmail popup if it appears mid-wait
            dismissPopupsIfAny(service)

            val root = service.rootInActiveWindow
            if (root != null) {
                // If dialog already open → done
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    AutomationState.log("ℹ Country dialog open")
                    return true
                }

                val rootR = Rect(); root.getBoundsInScreen(rootR)

                for (code in codes) {
                    val node = NodeFinder.findByText(root, code) ?: continue
                    if (!NodeFinder.isVisible(node)) continue

                    val r = Rect(); node.getBoundsInScreen(r)
                    val inTop = r.centerY() < rootR.top + rootR.height() * 0.28
                    val inRight = r.centerX() > rootR.left + rootR.width() * 0.55
                    if (!(inTop && inRight)) continue

                    val clickable = NodeFinder.findClickableParent(node) ?: node
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        AutomationState.log("ℹ US button clicked ($code)")
                        // Wait for search dialog to appear
                        if (waitForDialog(service, 3000)) return true
                    }
                }
            }
            sleepMs(400)
        }
        return false
    }

    private fun waitForDialog(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region") ||
                    NodeFinder.findEditText(root) != null) return true
            }
            sleepMs(300)
        }
        return false
    }

    // ── Search country: click search → type → click result ──
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryName: String,
        countryIso: String
    ): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val search = NodeFinder.findEditText(root)
        if (search == null) {
            AutomationState.log("✘ Search field not found")
            return false
        }

        // Click search field
        NodeFinder.click(search)
        sleepMs(500)

        // Type country name
        NodeFinder.setText(search, countryName)
        AutomationState.log("ℹ Typed: $countryName")
        sleepMs(1500)

        // Find result and click
        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            val r = service.rootInActiveWindow
            val match = findResult(r, countryName, countryIso)
            if (match != null && NodeFinder.click(match)) {
                AutomationState.log("✔ Country clicked: $countryName")
                return true
            }
            sleepMs(400)
        }
        AutomationState.log("✘ No result for $countryName")
        return false
    }

    private fun findResult(root: AccessibilityNodeInfo?, name: String, iso: String): AccessibilityNodeInfo? {
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

    // ── Wait for Gmail popup, close it ──
    fun waitAndClosePopup(service: AccessibilityService, timeoutMs: Long = 5000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (dismissPopupsIfAny(service)) return true
            sleepMs(300)
        }
        return false
    }

    // ── Dismiss any popup now ──
    fun dismissPopupsIfAny(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val candidates = arrayOf(
            "Close", "Cancel", "Dismiss", "No thanks", "Not now", "Skip", "✕", "✖", "×", "X"
        )
        val node = NodeFinder.findByText(root, *candidates)
            ?: NodeFinder.findByTextContains(root, *candidates)
            ?: return false
        if (NodeFinder.click(node)) {
            AutomationState.log("ℹ Popup closed")
            sleepMs(700)
            return true
        }
        return false
    }

    // ── Type number into EditText ──
    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
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
        NodeFinder.setText(edit, national)
        AutomationState.log("ℹ Number typed: $national")
        sleepMs(600)
        return true
    }

    // ── Continue ──
    fun tapContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, "Continue", "Next") ?: return false
        if (NodeFinder.click(node)) {
            AutomationState.log("ℹ Continue tapped")
            return true
        }
        return false
    }

    // ── Check result ──
    enum class Result { OTP_SCREEN, ERROR, TIMEOUT }

    fun checkResult(service: AccessibilityService, timeoutMs: Long = 10000): Result {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var errorFlag = false
        while (SystemClock.uptimeMillis() < end) {
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
            sleepMs(400)
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
            sleepMs(5000)
        }
    }

    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            sleepMs(600)
        } catch (_: Exception) {}
    }

    private fun sleepMs(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
