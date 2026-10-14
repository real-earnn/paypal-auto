package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object PayPalActions {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

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
                service, "paypal", 5000,
                "Get Started", "email or number", "Email or phone number", "Let's start"
            )
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    /**
     * Aggressive: poll every 60ms. Get Started or number screen — click instantly.
     */
    fun handleGetStartedAndWaitNumber(service: AccessibilityService, timeoutMs: Long = 10000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var clickedGetStarted = false

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root == null) { StepUtils.sleepMs(StepUtils.POLL_MS); continue }

            // Number screen ready?
            val hasText = NodeFinder.containsAny(root, "email or number", "Email or phone number")
            val hasEdit = NodeFinder.findEditText(root) != null
            if (hasText && hasEdit) {
                AutomationState.log("✔ Number screen ready")
                return true
            }

            // Get Started — click instantly
            if (!clickedGetStarted && NodeFinder.containsAny(root, "Get Started")) {
                if (NodeFinder.clickAny(root, "Get Started")) {
                    AutomationState.log("ℹ Get Started tapped")
                    clickedGetStarted = true
                }
            }

            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ Number screen timeout")
        return false
    }

    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 6000): Boolean {
        val codes = listOf(
            "US", "GB", "PK", "IN", "MY", "BD", "ID", "EG", "SA", "AE",
            "NG", "KE", "PH", "VN", "TH", "TR", "RU", "BR", "MX", "AR",
            "UA", "ZA", "GH", "CM", "SN", "KH", "KZ"
        )
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // Already open?
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) {
                    AutomationState.log("ℹ Dialog already open")
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
                        AutomationState.log("ℹ US clicked ($code)")
                        return waitForDialog(service, 2500)
                    }
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ US button not found")
        return false
    }

    private fun waitForDialog(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region") ||
                    NodeFinder.findEditText(root) != null) return true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryName: String,
        countryIso: String
    ): Boolean {
        // Find search field — poll until it's ready (max 2s)
        var search: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 2000
        while (SystemClock.uptimeMillis() < sEnd) {
            val root = service.rootInActiveWindow
            search = NodeFinder.findEditText(root)
            if (search != null) break
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        if (search == null) {
            AutomationState.log("✘ Search field not found")
            return false
        }

        NodeFinder.click(search)
        StepUtils.sleepMs(150)
        NodeFinder.setText(search, countryName)
        AutomationState.log("ℹ Typed: $countryName")

        // Wait result + click (max 3s)
        val end = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val r = service.rootInActiveWindow
            val match = findResult(r, countryName, countryIso)
            if (match != null && NodeFinder.click(match)) {
                AutomationState.log("✔ Country clicked: $countryName")
                return true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("✘ Country result not found")
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

    fun waitAndClosePopup(service: AccessibilityService, timeoutMs: Long = 4000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (dismissPopupsIfAny(service)) return true
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

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
            return true
        }
        return false
    }

    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        national = national.replace(" ", "").replace("-", "")

        var edit: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 2000
        while (SystemClock.uptimeMillis() < end) {
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
        StepUtils.sleepMs(150)
        NodeFinder.setText(edit, national)
        AutomationState.log("ℹ Number typed: $national")
        return true
    }

    fun tapContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, "Continue", "Next") ?: return false
        if (NodeFinder.click(node)) {
            AutomationState.log("ℹ Continue tapped")
            return true
        }
        return false
    }

    enum class Result { OTP_SCREEN, ERROR, TIMEOUT }

    fun checkResult(service: AccessibilityService, timeoutMs: Long = 8000): Result {
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
            StepUtils.sleepMs(400)
        } catch (_: Exception) {}
    }
}
