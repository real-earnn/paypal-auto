package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
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
            StepUtils.waitForPackage(service, "paypal", 8000)
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  TAP GET STARTED
    // ══════════════════════════════════════════════════════
    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastLog = 0L

        AutomationState.log("ℹ Waiting for Get Started…")

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                val hasEditField = findEditableUnder(root) != null
                val hasEmailText = NodeFinder.containsAny(
                    root, "email or number", "Email or phone number"
                )
                if (hasEditField || hasEmailText) {
                    AutomationState.log("ℹ Number screen already visible")
                    return true
                }

                val node = findByTextEquals(root, "Get Started")
                    ?: findByTextContains(root, "Get Started")
                    ?: findByContentDescContains(root, "Get Started")

                if (node != null) {
                    AutomationState.log("ℹ Get Started FOUND — clicking")
                    val ok = tapNode(service, node)
                    if (ok) {
                        AutomationState.log("✔ Get Started tapped — 5s wait")
                        StepUtils.sleepMs(5000)
                        return true
                    }
                }
            }

            val now = SystemClock.uptimeMillis()
            if (now - lastLog > 3000) {
                AutomationState.log("ℹ Still waiting for Get Started…")
                lastLog = now
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("⚠ Get Started not found")
        return true
    }

    // ══════════════════════════════════════════════════════
    //  CLICK US
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 20000): Boolean {
        AutomationState.log("ℹ Searching for US button…")
        val end = SystemClock.uptimeMillis() + timeoutMs

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                if (dialogOrSearchVisible(root)) {
                    AutomationState.log("✔ Country dialog already open")
                    return true
                }

                val usNode = findUsNode(root)
                if (usNode != null) {
                    AutomationState.log("ℹ US found — tapping")
                    val clicked = tapNode(service, usNode)
                    if (clicked) {
                        StepUtils.sleepMs(2000)
                        val r2 = service.rootInActiveWindow
                        if (r2 != null && dialogOrSearchVisible(r2)) {
                            AutomationState.log("✔ Dialog opened via US tap")
                            return true
                        } else {
                            AutomationState.log("⚠ Tap done but dialog not visible — retry")
                            StepUtils.sleepMs(800)
                            tapNode(service, usNode)
                            StepUtils.sleepMs(2000)
                            val r3 = service.rootInActiveWindow
                            if (r3 != null && dialogOrSearchVisible(r3)) {
                                AutomationState.log("✔ Dialog opened on retry")
                                return true
                            }
                        }
                    }
                }
            }
            StepUtils.sleepMs(500)
        }
        AutomationState.log("✘ US button not found")
        return false
    }

    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("  ↳ click: direct")
            return true
        }
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("  ↳ click: parent")
            return true
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val x = bounds.centerX(); val y = bounds.centerY()
            AutomationState.log("  ↳ tap at ($x, $y)")
            return tapAt(service, x, y)
        }
        AutomationState.log("  ↳ tap: bounds invalid")
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
            AutomationState.log("✘ tapAt: ${e.message}")
            false
        }
    }

    private fun findUsNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!n.isVisibleToUser) continue
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (matchesUS("$t $d".trim())) {
                val r = Rect(); n.getBoundsInScreen(r)
                AutomationState.log("ℹ US match: T='$t' D='$d' bounds=${r.centerX()},${r.centerY()}")
                return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun matchesUS(s: String): Boolean {
        if (s.isEmpty()) return false
        val upper = s.uppercase()
        if (upper == "US") return true
        if (upper.contains("UNITED STATES")) return true
        if (Regex("\\bUS\\b").containsMatchIn(upper)) return true
        return false
    }

    private fun dialogOrSearchVisible(root: AccessibilityNodeInfo): Boolean {
        if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) return true
        if (findByTextEquals(root, "Search") != null) return true
        return false
    }

    // ══════════════════════════════════════════════════════
    //  TYPE COUNTRY — with verify + retry
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Country: $countryName (from $countryNameRaw)")

        // 1. Find and tap "Search"
        var searchNode: AccessibilityNodeInfo? = null
        val sEnd = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < sEnd) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                searchNode = findByTextEquals(root, "Search")
                if (searchNode != null) break
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (searchNode == null) {
            AutomationState.log("✘ 'Search' text not found")
            return false
        }

        tapNode(service, searchNode)
        AutomationState.log("ℹ Search tapped")
        StepUtils.sleepMs(700)

        // 2. Paste country name
        pasteText(service, countryName, "search")
        AutomationState.log("ℹ Paste done — waiting for result")
        StepUtils.sleepMs(1800)

        // 3. Find + click result — with retry (max 3 attempts)
        var attempt = 0
        while (attempt < 3) {
            attempt++
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root == null) {
                StepUtils.sleepMs(800)
                continue
            }

            val match = findCountryResult(root, countryName, countryIso)
            if (match == null) {
                AutomationState.log("⚠ Result not found (attempt $attempt)")
                StepUtils.sleepMs(1000)
                continue
            }

            AutomationState.log("ℹ Clicking result (attempt $attempt)")
            tapNode(service, match)
            StepUtils.sleepMs(2000)

            // 4. Verify: dialog closed? (Search text no longer visible)
            val after = service.rootInActiveWindow
            if (after == null) {
                // Screen unclear — treat as success
                AutomationState.log("✔ Dialog seems closed")
                return true
            }
            val stillOpen = findByTextEquals(after, "Search") != null
            if (!stillOpen) {
                AutomationState.log("✔ Country selected, dialog closed")
                StepUtils.sleepMs(1200)
                return true
            }

            AutomationState.log("⚠ Dialog still open — retry click")
            StepUtils.sleepMs(500)
        }

        AutomationState.log("✘ Country selection failed after 3 attempts")
        return false
    }

    private fun cleanCountryName(raw: String): String {
        return raw.replace(Regex("[0-9]+(-[0-9]+)*$"), "").trim()
    }

    /**
     * 🎯 Find country result with 3 rules:
     *   1. Skip editable fields (search input)
     *   2. Skip keyboard region (bottom 40% of screen)
     *   3. Prefer nodes with "(CODE)" pattern
     */
    private fun findCountryResult(root: AccessibilityNodeInfo?, name: String, iso: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val keyboardTop = rootR.top + (rootR.height() * 0.60).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var fallback: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            // Rule 1: skip editable
            if (n.isEditable) continue

            val t = n.text?.toString() ?: ""
            if (!t.contains(name, ignoreCase = true)) {
                // still descend to children
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
                continue
            }

            // Rule 2: skip keyboard region
            val r = Rect(); n.getBoundsInScreen(r)
            if (r.top > keyboardTop) continue

            // Rule 3: prefer "(code)" pattern
            val hasCodePattern = t.contains("(", false) && t.contains(")", false)
            if (hasCodePattern) {
                AutomationState.log("✔ Best match: '$t'")
                return NodeFinder.findClickableParent(n) ?: n
            }

            // Fallback candidate (name matched but no brackets)
            if (fallback == null) {
                fallback = NodeFinder.findClickableParent(n) ?: n
                AutomationState.log("ℹ Fallback candidate: '$t'")
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return fallback
    }

    // ══════════════════════════════════════════════════════
    //  TYPE NUMBER — DOUBLE TAP + CLIPBOARD PASTE
    // ══════════════════════════════════════════════════════
    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        national = national.replace(" ", "").replace("-", "")
        AutomationState.log("ℹ Preparing number: $national")

        StepUtils.sleepMs(1500)

        var phoneLabel: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                phoneLabel = findByTextContains(root, "phone number")
                    ?: findByTextContains(root, "email or number")
                    ?: findByTextContains(root, "Email or phone")
                if (phoneLabel != null) break
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (phoneLabel == null) {
            AutomationState.log("✘ 'phone number' field not found")
            return false
        }

        val bounds = Rect()
        phoneLabel.getBoundsInScreen(bounds)
        val x = bounds.centerX()
        val y = bounds.centerY()
        AutomationState.log("ℹ Phone field at ($x, $y) — double tap")

        tapAt(service, x, y)
        StepUtils.sleepMs(250)
        tapAt(service, x, y)
        StepUtils.sleepMs(700)

        pasteText(service, national, "phone")
        StepUtils.sleepMs(800)

        val verifyRoot = service.rootInActiveWindow
        if (verifyRoot != null) {
            val edit = findEditableUnder(verifyRoot)
            val entered = edit?.text?.toString() ?: ""
            AutomationState.log("ℹ Field now contains: '$entered'")
        }
        return true
    }

    private fun pasteText(service: AccessibilityService, text: String, label: String) {
        try {
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            AutomationState.log("ℹ Clipboard set: '$text'")
        } catch (e: Exception) {
            AutomationState.log("✘ Clipboard fail: ${e.message}")
        }

        StepUtils.sleepMs(200)

        val root = service.rootInActiveWindow
        val edit = findEditableUnder(root)

        if (edit != null) {
            edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val pasteOk = edit.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteOk) {
                AutomationState.log("✔ Pasted via ACTION_PASTE")
                return
            }

            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            val setOk = edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setOk) {
                AutomationState.log("✔ Set text via ACTION_SET_TEXT")
                return
            }

            AutomationState.log("⚠ Both paste & setText failed")
        } else {
            AutomationState.log("⚠ No editable node")
        }
    }

    // ══════════════════════════════════════════════════════
    //  CONTINUE
    // ══════════════════════════════════════════════════════
    fun tapContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, "Continue", "Next") ?: return false
        if (tapNode(service, node)) {
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
            if (node != null && tapNode(service, node)) {
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

    // ══════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════
    private fun findByTextEquals(root: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
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

    private fun findByTextContains(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
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

    private fun findByContentDescContains(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lower = needle.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            if (d.contains(lower) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

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
}
