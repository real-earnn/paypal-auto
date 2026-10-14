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

    // 🟢 Hardcoded US button position (pointer location se confirm kiya)
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
    //  TAP GET STARTED
    // ══════════════════════════════════════════════════════
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // Direct US text visible → we're done
                if (NodeFinder.containsAny(root, "US")
                    || NodeFinder.containsAny(root, "Country of residence")
                ) {
                    return true
                }

                val node = NodeFinder.findByText(root, "Get Started")
                    ?: NodeFinder.findByTextContains(root, "Get Started")
                if (node != null && NodeFinder.click(node)) {
                    AutomationState.log("ℹ Get Started tapped")
                    StepUtils.sleepMs(1800)
                    return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return true
    }

    // ══════════════════════════════════════════════════════
    //  CLICK US BUTTON  — (600, 125) HARDCODED
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 8000): Boolean {
        AutomationState.log("ℹ US tap at ($US_TAP_X, $US_TAP_Y)")

        // Wait a moment to let PayPal settle
        StepUtils.sleepMs(1500)

        // Tap hardcoded location
        val ok = tapAt(service, US_TAP_X, US_TAP_Y)
        if (!ok) {
            AutomationState.log("✘ Tap gesture failed")
            return false
        }

        // Wait for country dialog to appear
        StepUtils.sleepMs(1500)
        if (waitForCountryDialog(service, 3000)) {
            AutomationState.log("✔ Country dialog appeared")
            return true
        }

        // Retry once
        AutomationState.log("ℹ Dialog not visible — retry tap")
        tapAt(service, US_TAP_X, US_TAP_Y)
        StepUtils.sleepMs(1500)
        if (waitForCountryDialog(service, 3000)) {
            AutomationState.log("✔ Country dialog appeared (retry)")
            return true
        }

        AutomationState.log("✘ Country dialog did not open")
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

    private fun waitForCountryDialog(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Country of residence", "Country/region") ||
                    findSearchField(root) != null
                ) {
                    return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

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
    //  TYPE COUNTRY
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Search: $countryName (from $countryNameRaw)")

        // Wait for search field (dialog should already be open by now)
        var search: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 3500
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val dialogOpen = NodeFinder.containsAny(root, "Country of residence", "Country/region")
                search = findSearchField(root)
                if (search != null && dialogOpen) break
                if (search != null && dialogOpen == false) {
                    // search field exists but no dialog text — probably still loading
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (search == null) {
            AutomationState.log("✘ Search field not found — aborting")
            return false
        }

        // Click + type
        NodeFinder.click(search)
        StepUtils.sleepMs(500)
        NodeFinder.setText(search, countryName)
        StepUtils.sleepMs(1500)

        // Find & click result
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
    //  TYPE NUMBER
    // ══════════════════════════════════════════════════════
    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        national = national.replace(" ", "").replace("-", "")

        StepUtils.sleepMs(1000)   // wait for popup watcher to clear any Gmail popup

        var edit: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 4000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
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
        StepUtils.sleepMs(600)

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
