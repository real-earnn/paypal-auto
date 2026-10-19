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

    fun tapGetStarted(service: AccessibilityService, timeoutMs: Long = 20000): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        AutomationState.log("ℹ Waiting for Get Started (${timeoutMs / 1000}s timeout)...")
        var lastLog = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (isGmailPopupVisible(root)) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLog > 2000) {
                        AutomationState.log("  ⚠ Gmail popup blocking Get Started — waiting")
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
                val node = findByTextEquals(root, "Get Started")
                    ?: findByTextContains(root, "Get Started")
                if (node != null) {
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
        AutomationState.log("⚠ Get Started not found after timeout")
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
                    if (sawPopup) {
                        AutomationState.log("✔ Gmail popup closed")
                    }
                    return true
                } else {
                    if (!sawPopup) {
                        AutomationState.log("⚠ Gmail popup visible — waiting for it to close")
                        sawPopup = true
                    }
                }
            }
            StepUtils.sleepMs(500)
        }
        AutomationState.log("⚠ Gmail popup still visible after ${timeoutMs/1000}s")
        return false
    }

    fun isGmailPopupVisible(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return NodeFinder.containsAny(
            root,
            "Choose an account",
            "to continue to PayPal",
            "to continue to TikTok",
            "Sign-in options",
            "Sign in with Google",
            "Use another account"
        )
    }

    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 25000): Boolean {
        AutomationState.log("ℹ Searching for US button (${timeoutMs / 1000}s timeout)...")
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastLog = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                if (isGmailPopupVisible(root)) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLog > 2000) {
                        AutomationState.log("  ⚠ Gmail popup blocking US button — waiting")
                        lastLog = now
                    }
                    StepUtils.sleepMs(500)
                    continue
                }

                if (dialogOrSearchVisible(root)) {
                    AutomationState.log("✔ Country dialog already open")
                    return true
                }
                val usNode = findUsNode(root)
                if (usNode != null) {
                    AutomationState.log("ℹ US found — tapping")
                    if (tapNode(service, usNode)) {
                        StepUtils.sleepMs(2000)
                        val r2 = service.rootInActiveWindow
                        if (r2 != null && dialogOrSearchVisible(r2)) {
                            AutomationState.log("✔ Dialog opened")
                            return true
                        } else {
                            AutomationState.log("  ⚠ Dialog didn't open — retry")
                            StepUtils.sleepMs(1000)
                            tapNode(service, usNode)
                            StepUtils.sleepMs(2000)
                            val r3 = service.rootInActiveWindow
                            if (r3 != null && dialogOrSearchVisible(r3)) return true
                        }
                    }
                }
            }
            StepUtils.sleepMs(500)
        }
        AutomationState.log("✘ US button not found after timeout")
        return false
    }

    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) return tapAt(service, bounds.centerX(), bounds.centerY())
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
                        AutomationState.log("  ⚠ Gmail popup blocking Search — waiting")
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

        if (searchNode == null) {
            AutomationState.log("✘ Search input not found after 15s")
            return false
        }

        AutomationState.log("ℹ Search input found — tapping")
        if (!tapNode(service, searchNode)) {
            AutomationState.log("  ⚠ Search tap failed — retrying after popup check")
            StepUtils.sleepMs(2000)
            val retryRoot = service.rootInActiveWindow
            if (retryRoot != null) {
                val retrySearch = findByTextEquals(retryRoot, "Search") ?: findByTextContains(retryRoot, "Search")
                if (retrySearch != null && tapNode(service, retrySearch)) {
                    AutomationState.log("  ✔ Retry tap successful")
                } else {
                    AutomationState.log("  ✘ Retry tap failed too")
                    return false
                }
            } else {
                return false
            }
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
                AutomationState.log("✔ Country selected (dialog closed)")
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

    private fun cleanCountryName(raw: String): String {
        return raw.replace(Regex("[0-9]+(-[0-9]+)*$"), "").trim()
    }

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
            root,
            "Something went wrong", "can't be used", "not valid",
            "invalid", "Enter a valid", "try again", "Try again"
        )
    }

    fun closeResendPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val closeTexts = arrayOf("Close", "Cancel", "Dismiss", "✕", "✖", "×", "X")
        NodeFinder.findByText(root, *closeTexts)?.let { n ->
            if (NodeFinder.isVisible(n) && NodeFinder.click(n)) {
                StepUtils.sleepMs(300)
                return true
            }
        }
        return false
    }

    fun isResendVisible(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")
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
            if (root == null) {
                StepUtils.sleepMs(StepUtils.POLL_MS); continue
            }

            if (NodeFinder.containsAny(root, "Password", "More Options", "login")) {
                return Result.PASSWORD_SCREEN
            }

            if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                return Result.OTP_SCREEN
            }

            if (isCaptchaPresent(root)) {
                captchaSeen = true
                val ext = now + 60_000
                if (ext > deadline) deadline = ext
                StepUtils.sleepMs(StepUtils.POLL_MS); continue
            }

            if (NodeFinder.containsAny(root, "Something went wrong", "can't be used", "not valid", "invalid", "Enter a valid", "try again", "Try again")) {
                if (errorSeenAt == 0L) {
                    errorSeenAt = now
                } else if (now - errorSeenAt >= 400) {
                    return Result.ERROR
                }
                StepUtils.sleepMs(StepUtils.POLL_MS); continue
            } else {
                errorSeenAt = 0L
            }

            if (now > stuckCheckStart) {
                val hasInput = findEditableUnder(root) != null
                val hasContinue = NodeFinder.findByText(root, "Continue", "Next") != null
                if (hasInput && hasContinue && !captchaSeen) {
                    return Result.STUCK
                }
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
        if (errorSeenAt > 0L) return Result.ERROR
        return Result.TIMEOUT
    }

    private fun isCaptchaPresent(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return NodeFinder.containsAny(root, "I am human", "Security Challenge", "hCaptcha", "Verify you are human", "Drag the", "Select all")
    }

    // ══════════════════════════════════════════════════════
    //  🟢 FIXED: Password Screen with Autofill Dismiss & Double Back
    // ══════════════════════════════════════════════════════
    fun handlePasswordScreen(service: AccessibilityService): Boolean {
        AutomationState.log("  🔒 Handling Password Screen...")

        // 1. Dismiss any autofill overlay before clicking
        dismissAutofillOverlay(service)

        // 2. Click "More Options"
        if (!clickText(service, "More Options", "More options", "More")) {
            AutomationState.log("  ✘ 'More Options' not found → Back & Retry")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
            return false
        }
        StepUtils.sleepMs(1500)

        // 3. Click "Create New Password"
        if (!clickText(service, "Create New Password", "Create new password", "Create a new code", "Create new code")) {
            AutomationState.log("  ✘ 'Create New Password' not found → Back & Retry")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
            return false
        }
        StepUtils.sleepMs(2000)

        // 4. Poll for "Text a code" (8 seconds wait)
        var textCodeFound = false
        val textCodeEnd = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < textCodeEnd) {
            if (StepUtils.shouldStop()) return false
            val r = service.rootInActiveWindow
            if (r != null) {
                val node = NodeFinder.findByText(r, "Text a code")
                    ?: NodeFinder.findByText(r, "Text code")
                    ?: NodeFinder.findByText(r, "Text a new code")
                    ?: NodeFinder.findByText(r, "SMS")
                    ?: NodeFinder.findByTextContains(r, "Text a code")
                if (node != null && node.isVisibleToUser) {
                    tapNode(service, node)
                    textCodeFound = true
                    break
                }
            }
            StepUtils.sleepMs(500)
        }

        if (!textCodeFound) {
            AutomationState.log("  ⚠ 'Text a code' NOT found after 8s (Email/WhatsApp maybe) → Back & Retry")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(800)
            return false
        }
        StepUtils.sleepMs(1500)

        // 5. Fast Loop: "More Options" -> "Text a new code" -> Check "Return to login"
        for (loop in 0 until 8) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow ?: continue

            if (NodeFinder.findByTextContains(root, "Return to login") != null) {
                AutomationState.log("  ✔ 'Return to login' found!")
                clickText(service, "Return to login")
                StepUtils.sleepMs(1000)
                
                // 🟢 DOUBLE BACK FIX: Loop back press until Phone Input screen is reached
                AutomationState.log("  ↳ Doing multi-back to reach Number Input screen...")
                for (backAttempt in 0 until 3) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    StepUtils.sleepMs(1000)
                    
                    val currentRoot = service.rootInActiveWindow
                    if (currentRoot != null) {
                        val hasEdit = findEditableUnder(currentRoot) != null
                        val hasPasswordOrOtp = NodeFinder.containsAny(currentRoot, "Password", "More Options", "Resend Code", "login")
                        
                        if (hasEdit && !hasPasswordOrOtp) {
                            AutomationState.log("  ✔ Reached actual Number Input screen after ${backAttempt + 1} backs")
                            return true
                        }
                    }
                }
                AutomationState.log("  ⚠ Could not confirm Number screen, but returning true anyway")
                return true
            }

            AutomationState.log("  ↳ Loop ${loop + 1}: More Options -> Text a new code")
            
            // 🟢 Dismiss autofill BEFORE trying to click (fix for hidden "More Options")
            dismissAutofillOverlay(service)
            
            clickText(service, "More Options", "More options", "More")
            StepUtils.sleepMs(600)
            clickText(service, "Text a new code", "Create new code", "Send new code", "Text a code")
            StepUtils.sleepMs(2000)
        }

        val finalRoot = service.rootInActiveWindow
        if (finalRoot != null && NodeFinder.findByTextContains(finalRoot, "Return to login") != null) {
            clickText(service, "Return to login")
            StepUtils.sleepMs(1000)
            for (backAttempt in 0 until 3) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(1000)
                val currentRoot = service.rootInActiveWindow
                if (currentRoot != null) {
                    val hasEdit = findEditableUnder(currentRoot) != null
                    val hasPasswordOrOtp = NodeFinder.containsAny(currentRoot, "Password", "More Options", "Resend Code", "login")
                    if (hasEdit && !hasPasswordOrOtp) return true
                }
            }
            return true
        }
        return false
    }

    // 🟢 NEW HELPER: Tap empty space at the top to dismiss Google Autofill overlay
    private fun dismissAutofillOverlay(service: AccessibilityService) {
        val root = service.rootInActiveWindow ?: return
        val screenR = Rect(); root.getBoundsInScreen(screenR)
        
        // Tap near the very top center (safe zone, away from buttons)
        val tapX = screenR.centerX()
        val tapY = screenR.top + 150
        
        AutomationState.log("    ↳ Tapping top area to dismiss autofill overlay")
        tapAt(service, tapX, tapY)
        StepUtils.sleepMs(300)
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
