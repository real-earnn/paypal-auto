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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object PayPalActions {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    // 🟢 Reset signal for Password flow
    @Volatile
    var passwordFlowNeedsReset: Boolean = false
        private set

    fun launch(service: AccessibilityService): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(PAYPAL_PKG) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            service.startActivity(intent)
            StepUtils.waitForPackage(service, "paypal", 8000)
        } catch (e: Exception) {
            AutomationState.log("✘ Launch: ${e.message}")
            false
        }
    }

    fun closePayPal(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(800)
        } catch (_: Exception) {}
    }

    // 🟢 STRICT Gmail popup detection - ONLY real popup markers
    fun isGmailPopupVisible(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        // Only these appear in ACTUAL Google account chooser popup
        return NodeFinder.containsAny(
            root,
            "Choose an account",
            "to continue to PayPal",
            "to continue to TikTok"
        )
    }

    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 20000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        AutomationState.log("ℹ Waiting for Get Started (${timeoutMs / 1000}s)...")
        var lastLog = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (isGmailPopupVisible(root)) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLog > 2000) {
                        AutomationState.log("  ⚠ Gmail popup blocking — waiting")
                        lastLog = now
                    }
                    StepUtils.sleepMs(500)
                    continue
                }

                val hasEditField = findEditableUnder(root) != null
                val hasEmailText = NodeFinder.containsAny(root, "email or number", "Email or phone number")
                if (hasEditField || hasEmailText) {
                    AutomationState.log("✔ Number screen already visible")
                    return true
                }
                // Try multiple find strategies
                val node = findByTextEquals(root, "Get Started")
                    ?: findByTextContains(root, "Get Started")
                    ?: findByContentDescContains(root, "Get Started")
                if (node != null && node.isVisibleToUser) {
                    AutomationState.log("✔ Get Started found — tapping")
                    if (tapNode(service, node)) {
                        StepUtils.sleepMs(5000)
                        return true
                    }
                }
            }
            val now = SystemClock.uptimeMillis()
            if (now - lastLog > 3000) {
                AutomationState.log("ℹ Still waiting for Get Started...")
                lastLog = now
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        AutomationState.log("⚠ Get Started not found")
        return false
    }

    fun waitForGmailPopupToClose(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var sawPopup = false
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return true
            val root = service.rootInActiveWindow
            if (root != null) {
                if (!isGmailPopupVisible(root)) {
                    if (sawPopup) AutomationState.log("✔ Gmail popup closed")
                    return true
                } else {
                    if (!sawPopup) {
                        AutomationState.log("⚠ Gmail popup visible — waiting")
                        sawPopup = true
                    }
                }
            }
            StepUtils.sleepMs(500)
        }
        return false
    }

    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 25000): Boolean {
        AutomationState.log("ℹ Searching for US button...")
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastLog = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (isGmailPopupVisible(root)) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLog > 2000) {
                        AutomationState.log("  ⚠ Gmail popup blocking — waiting")
                        lastLog = now
                    }
                    StepUtils.sleepMs(500)
                    continue
                }

                if (dialogOrSearchVisible(root)) {
                    AutomationState.log("✔ Country dialog open")
                    return true
                }
                val usNode = findUsNode(root)
                if (usNode != null) {
                    AutomationState.log("ℹ US found — tapping")
                    if (tapNode(service, usNode)) {
                        StepUtils.sleepMs(2000)
                        val r2 = service.rootInActiveWindow
                        if (r2 != null && dialogOrSearchVisible(r2)) return true
                        StepUtils.sleepMs(1000)
                        tapNode(service, usNode)
                        StepUtils.sleepMs(2000)
                        val r3 = service.rootInActiveWindow
                        if (r3 != null && dialogOrSearchVisible(r3)) return true
                    }
                }
            }
            StepUtils.sleepMs(500)
        }
        return false
    }

    // 🟢 RELIABLE tap: direct click → parent click → coordinate tap
    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return tapAt(service, bounds.centerX(), bounds.centerY())
        }
        return false
    }

    private fun tapAt(service: AccessibilityService, x: Int, y: Int): Boolean {
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val latch = CountDownLatch(1)
            var ok = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { ok = false; latch.countDown() }
            }, null)
            latch.await(2, TimeUnit.SECONDS)
            ok
        } catch (e: Exception) { false }
    }

    private fun findUsNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!n.isVisibleToUser) continue
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (matchesUS("$t $d".trim())) return n
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

    fun typeAndSelectCountry(service: AccessibilityService, countryNameRaw: String, countryIso: String): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        var searchNode: AccessibilityNodeInfo? = null
        var lastLog = 0L
        val searchEnd = SystemClock.uptimeMillis() + 15000

        while (SystemClock.uptimeMillis() < searchEnd) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (isGmailPopupVisible(root)) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLog > 2000) {
                        AutomationState.log("  ⚠ Gmail popup blocking Search")
                        lastLog = now
                    }
                    StepUtils.sleepMs(500)
                    continue
                }
                val node = findByTextEquals(root, "Search") ?: findByTextContains(root, "Search")
                if (node != null && node.isVisibleToUser) {
                    searchNode = node
                    break
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (searchNode == null) return false

        if (!tapNode(service, searchNode)) {
            StepUtils.sleepMs(2000)
            val retryRoot = service.rootInActiveWindow
            if (retryRoot != null) {
                val retrySearch = findByTextEquals(retryRoot, "Search") ?: findByTextContains(retryRoot, "Search")
                if (retrySearch == null || !tapNode(service, retrySearch)) return false
            } else return false
        }
        StepUtils.sleepMs(700)

        pasteText(service, countryName, "search")
        StepUtils.sleepMs(2000)

        var clickAttempts = 0
        while (clickAttempts < 5) {
            clickAttempts++
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow ?: continue
            if (!dialogStillOpen(root)) {
                StepUtils.sleepMs(1200)
                return true
            }
            val match = findCountryResult(root, countryName, countryIso)
            if (match != null) {
                tapNode(service, match)
                StepUtils.sleepMs(1500)
                val after = service.rootInActiveWindow
                if (after != null && !dialogStillOpen(after)) {
                    StepUtils.sleepMs(1200)
                    return true
                }
            }
            StepUtils.sleepMs(800)
        }
        return false
    }

    private fun dialogStillOpen(root: AccessibilityNodeInfo): Boolean {
        return findByTextEquals(root, "Search") != null || NodeFinder.containsAny(root, "Country of residence", "Country/region")
    }

    private fun cleanCountryName(raw: String): String = raw.replace(Regex("[0-9]+(-[0-9]+)*$"), "").trim()

    private fun findCountryResult(root: AccessibilityNodeInfo?, name: String, iso: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val keyboardTop = rootR.top + (rootR.height() * 0.65).toInt()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var fallback: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable) continue
            val t = n.text?.toString() ?: ""
            if (!t.contains(name, ignoreCase = true)) {
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
                continue
            }
            val r = Rect(); n.getBoundsInScreen(r)
            if (r.top > keyboardTop) continue
            val hasCodePattern = t.contains("(", false) && t.contains(")", false)
            if (hasCodePattern) return NodeFinder.findClickableParent(n) ?: n
            if (fallback == null) fallback = NodeFinder.findClickableParent(n) ?: n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return fallback
    }

    fun hasRedError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(
            root, "Something went wrong", "can't be used", "not valid",
            "invalid", "Enter a valid", "try again", "Try again"
        )
    }

    // 🟢 ROBUST close popup: tap X → top area → 2 forced backs
    fun closeResendPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val closeTexts = arrayOf("Close", "Cancel", "Dismiss", "✕", "✖", "×", "X")

        // Method 1: tap X (with coordinate fallback via tapNode)
        for (text in closeTexts) {
            val n = findByTextEquals(root, text) ?: findByTextContains(root, text)
            if (n != null && n.isVisibleToUser) {
                AutomationState.log("  → Tap close button: '$text'")
                if (tapNode(service, n)) {
                    StepUtils.sleepMs(500)
                    if (!isResendVisible(service)) return true
                }
            }
        }

        // Method 2: tap top empty area
        val screenR = Rect(); root.getBoundsInScreen(screenR)
        val topY = screenR.top + 150
        AutomationState.log("  → Tap top area to dismiss")
        tapAt(service, screenR.centerX(), topY)
        StepUtils.sleepMs(600)
        if (!isResendVisible(service)) return true

        // Method 3: check keyboard, force 2 backs if popup still visible
        if (isKeyboardOpen(root)) {
            AutomationState.log("  → Keyboard open — forcing 2 back presses")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(400)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
        }
        return !isResendVisible(service)
    }

    fun isResendVisible(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")
    }

    private fun isKeyboardOpen(root: AccessibilityNodeInfo): Boolean {
        val kbdPkgs = listOf(
            "com.google.android.inputmethod", "com.touchtype", "com.samsung.android.honeyboard",
            "com.baidu.input", "com.sohu.inputmethod", "com.swiftkey"
        )
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val pkg = n.packageName?.toString()?.lowercase() ?: ""
            if (kbdPkgs.any { pkg.contains(it) }) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    fun clearNumberField(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val edit = findFocusedEditable(root) ?: findEditableUnder(root) ?: return false
        NodeFinder.click(edit)
        StepUtils.sleepMs(300)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            StepUtils.sleepMs(300)
            return true
        }
        return false
    }

    fun typeNumber(service: AccessibilityService, fullNumber: String, dialingCode: String): Boolean {
        var national = fullNumber.trim().removePrefix("+")
        if (dialingCode.isNotEmpty() && national.startsWith(dialingCode)) national = national.substring(dialingCode.length)
        national = national.replace(" ", "").replace("-", "")
        if (national.startsWith("0")) national = national.substring(1)

        val nationalDigits = national.replace(Regex("\\D"), "")
        if (nationalDigits.length < 6) return false
        val verifyTail = nationalDigits.takeLast(6)

        var phoneLabel: AccessibilityNodeInfo? = null
        val labelEnd = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < labelEnd) {
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
        if (phoneLabel == null) return false

        val bounds = Rect(); phoneLabel.getBoundsInScreen(bounds)
        val x = bounds.centerX(); val y = bounds.centerY()

        val delays = longArrayOf(2000L, 3000L, 5000L, 5000L, 5000L)
        for (attempt in 0 until 5) {
            if (StepUtils.shouldStop()) return false
            StepUtils.sleepMs(500)
            val root = service.rootInActiveWindow
            val edit = findEditableUnder(root)
            if (edit != null) {
                val preText = edit.text?.toString() ?: ""
                val preDigits = preText.replace(Regex("\\D"), "")
                if (preDigits.contains(verifyTail)) return true
            }

            tapAt(service, x, y); StepUtils.sleepMs(600)
            val freshRoot = service.rootInActiveWindow
            val freshEdit = findEditableUnder(freshRoot)
            if (freshEdit == null) { StepUtils.sleepMs(delays[attempt]); continue }

            val existing = freshEdit.text?.toString() ?: ""
            if (existing.isNotEmpty()) {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                freshEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                StepUtils.sleepMs(300)
            }

            val setArgs = Bundle()
            setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, national)
            freshEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
            StepUtils.sleepMs(800)

            val vRoot = service.rootInActiveWindow
            val vEdit = findEditableUnder(vRoot)
            val vText = vEdit?.text?.toString() ?: ""
            val vDigits = vText.replace(Regex("\\D"), "")
            if (vDigits.isNotEmpty() && vDigits.contains(verifyTail)) return true
            if (vDigits.length >= 8) return true

            StepUtils.sleepMs(delays[attempt])
        }
        return false
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser && n.isFocused) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun pasteText(service: AccessibilityService, text: String, label: String) {
        try {
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
        } catch (_: Exception) {}
        StepUtils.sleepMs(200)
        val root = service.rootInActiveWindow
        val edit = findFocusedEditable(root) ?: findEditableUnder(root)
        if (edit != null) {
            edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (edit.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    fun tapContinue(service: AccessibilityService): Boolean {
        var root = service.rootInActiveWindow ?: return false
        var node = NodeFinder.findByText(root, "Continue", "Next")
        if (node == null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(500)
            root = service.rootInActiveWindow ?: return false
            node = NodeFinder.findByText(root, "Continue", "Next")
        }
        if (node != null && tapNode(service, node)) {
            AutomationState.log("ℹ Continue tapped")
            return true
        }
        return false
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

    enum class Result { OTP_SCREEN, ERROR, TIMEOUT, RETRY_CONTINUE, PASSWORD_SCREEN, STUCK }

    fun checkResult(service: AccessibilityService, timeoutMs: Long = 12000): Result {
        val startTime = SystemClock.uptimeMillis()
        var deadline = startTime + timeoutMs
        var captchaSeen = false
        var errorSeenAt = 0L
        var stuckCheckStart = startTime + 6000

        while (SystemClock.uptimeMillis() < deadline) {
            if (StepUtils.shouldStop()) return Result.TIMEOUT
            val now = SystemClock.uptimeMillis()
            val root = service.rootInActiveWindow
            if (root == null) { StepUtils.sleepMs(StepUtils.POLL_MS); continue }

            if (NodeFinder.containsAny(root, "Password", "More Options", "login")) return Result.PASSWORD_SCREEN
            if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) return Result.OTP_SCREEN

            if (isCaptchaPresent(root)) {
                captchaSeen = true
                val ext = now + 60_000
                if (ext > deadline) deadline = ext
                StepUtils.sleepMs(StepUtils.POLL_MS); continue
            }

            if (NodeFinder.containsAny(root, "Something went wrong", "can't be used", "not valid", "invalid", "Enter a valid", "try again", "Try again")) {
                if (errorSeenAt == 0L) errorSeenAt = now
                else if (now - errorSeenAt >= 400) return Result.ERROR
                StepUtils.sleepMs(StepUtils.POLL_MS); continue
            } else errorSeenAt = 0L

            if (now > stuckCheckStart) {
                val hasInput = findEditableUnder(root) != null
                val hasContinue = NodeFinder.findByText(root, "Continue", "Next") != null
                if (hasInput && hasContinue && !captchaSeen) return Result.STUCK
            }

            if (captchaSeen) {
                val contBtn = NodeFinder.findByText(root, "Continue", "Next")
                if (contBtn != null && contBtn.isVisibleToUser) {
                    StepUtils.sleepMs(1500)
                    return Result.RETRY_CONTINUE
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return if (errorSeenAt > 0L) Result.ERROR else Result.TIMEOUT
    }

    private fun isCaptchaPresent(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return NodeFinder.containsAny(root, "I am human", "Security Challenge", "hCaptcha", "Verify you are human", "Drag the", "Select all")
    }

    // 🟢 Password flow with FORCE RESET on unexpected screen
    fun handlePasswordScreen(service: AccessibilityService): Boolean {
        passwordFlowNeedsReset = false
        AutomationState.log("  🔒 Handling Password Screen...")

        dismissAutofillOverlay(service)

        if (!clickText(service, "More Options", "More options", "More")) {
            AutomationState.log("  ✘ 'More Options' not found")
            forceBack(service, 2)
            return false
        }
        StepUtils.sleepMs(1500)

        if (!clickText(service, "Create New Password", "Create new password", "Create a new code", "Create new code")) {
            AutomationState.log("  ✘ 'Create New Password' not found")
            forceBack(service, 2)
            return false
        }
        StepUtils.sleepMs(2000)

        // 🟢 After Create New Password: MUST see "Text a code" (or variants)
        var textCodeFound = false
        val textCodeEnd = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < textCodeEnd) {
            if (StepUtils.shouldStop()) return false
            val r = service.rootInActiveWindow
            if (r != null) {
                val node = findByTextEquals(r, "Text a code")
                    ?: findByTextEquals(r, "Text code")
                    ?: findByTextContains(r, "Text a code")
                    ?: findByTextContains(r, "Text code")
                    ?: findByTextEquals(r, "SMS")
                if (node != null && node.isVisibleToUser) {
                    tapNode(service, node)
                    textCodeFound = true
                    break
                }
            }
            StepUtils.sleepMs(500)
        }

        // 🟢 FORCE RESET if unexpected screen
        if (!textCodeFound) {
            AutomationState.log("  ⚠ UNEXPECTED screen (no 'Text a code') — FORCE RESET")
            passwordFlowNeedsReset = true
            return false
        }
        StepUtils.sleepMs(1500)

        // Loop: More Options -> Text a new code -> check Return to login
        for (loop in 0 until 8) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow ?: continue

            if (NodeFinder.findByTextContains(root, "Return to login") != null) {
                AutomationState.log("  ✔ 'Return to login' found")
                clickText(service, "Return to login")
                StepUtils.sleepMs(1200)

                // 🟢 1 back + check screen
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(1500)

                val afterBack1 = service.rootInActiveWindow
                if (afterBack1 != null && isOnNumberInputScreen(afterBack1)) {
                    // On number screen — WAIT for Gmail popup cycle (number is locked until popup)
                    AutomationState.log("  ✔ On number screen — waiting for Gmail popup cycle...")
                    StepUtils.sleepMs(2000)
                    waitForGmailPopupToClose(service, 10000)
                    return true
                }

                // Not on number screen — 2nd back
                AutomationState.log("  ↳ Not on number screen — 2nd back")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(1500)

                val afterBack2 = service.rootInActiveWindow
                if (afterBack2 != null && isOnNumberInputScreen(afterBack2)) {
                    StepUtils.sleepMs(2000)
                    waitForGmailPopupToClose(service, 10000)
                    return true
                }
                return true
            }

            AutomationState.log("  ↳ Loop ${loop + 1}: More Options -> Text a new code")
            dismissAutofillOverlay(service)
            clickText(service, "More Options", "More options", "More")
            StepUtils.sleepMs(600)
            clickText(service, "Text a new code", "Create new code", "Send new code", "Text a code")
            StepUtils.sleepMs(2000)
        }
        passwordFlowNeedsReset = true
        return false
    }

    // 🟢 Check if we're ACTUALLY on number input screen (not password/OTP)
    private fun isOnNumberInputScreen(root: AccessibilityNodeInfo): Boolean {
        if (findEditableUnder(root) == null) return false
        if (NodeFinder.containsAny(root, "Password", "More Options", "Resend Code", "Resend code", "6-digit")) return false
        return NodeFinder.containsAny(root, "Phone", "phone", "email or number", "Email or phone")
    }

    private fun dismissAutofillOverlay(service: AccessibilityService) {
        val root = service.rootInActiveWindow ?: return
        val screenR = Rect(); root.getBoundsInScreen(screenR)
        tapAt(service, screenR.centerX(), screenR.top + 150)
        StepUtils.sleepMs(300)
    }

    private fun forceBack(service: AccessibilityService, times: Int) {
        repeat(times) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(600)
        }
    }

    private fun clickText(service: AccessibilityService, vararg texts: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        for (text in texts) {
            val node = NodeFinder.findByText(root, text) ?: NodeFinder.findByTextContains(root, text)
            if (node != null && node.isVisibleToUser) {
                tapNode(service, node)
                return true
            }
        }
        return false
    }

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
