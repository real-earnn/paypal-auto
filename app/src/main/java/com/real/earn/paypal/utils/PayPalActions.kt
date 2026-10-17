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
            val intent = service.packageManager.getLaunchIntentForPackage(PAYPAL_PKG) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
                val hasEmailText = NodeFinder.containsAny(root, "email or number", "Email or phone number")
                if (hasEditField || hasEmailText) {
                    AutomationState.log("ℹ Number screen already visible")
                    return true
                }
                val node = findByTextEquals(root, "Get Started")
                    ?: findByTextContains(root, "Get Started")
                    ?: findByContentDescContains(root, "Get Started")
                if (node != null) {
                    AutomationState.log("ℹ Get Started FOUND — clicking")
                    if (tapNode(service, node)) {
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
                    if (tapNode(service, usNode)) {
                        StepUtils.sleepMs(2000)
                        val r2 = service.rootInActiveWindow
                        if (r2 != null && dialogOrSearchVisible(r2)) {
                            AutomationState.log("✔ Dialog opened")
                            return true
                        } else {
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
            val latch = java.util.concurrent.CountDownLatch(1)
            var ok = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { ok = false; latch.countDown() }
            }, null)
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
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

    // ══════════════════════════════════════════════════════
    //  TYPE + SELECT COUNTRY
    // ══════════════════════════════════════════════════════
    fun typeAndSelectCountry(
        service: AccessibilityService,
        countryNameRaw: String,
        countryIso: String
    ): Boolean {
        val countryName = cleanCountryName(countryNameRaw)
        AutomationState.log("ℹ Country: $countryName (from $countryNameRaw)")

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
            AutomationState.log("✘ 'Search' not found")
            return false
        }

        tapNode(service, searchNode)
        StepUtils.sleepMs(700)

        pasteText(service, countryName, "search")
        AutomationState.log("ℹ Paste done — waiting for result")
        StepUtils.sleepMs(2000)

        var clickAttempts = 0
        val maxAttempts = 5

        while (clickAttempts < maxAttempts) {
            clickAttempts++
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root == null) {
                StepUtils.sleepMs(700); continue
            }

            if (!dialogStillOpen(root)) {
                AutomationState.log("✔ Country selected (dialog closed)")
                StepUtils.sleepMs(1200)
                return true
            }

            val match = findCountryResult(root, countryName, countryIso)
            if (match == null) {
                AutomationState.log("⚠ Result not found (attempt $clickAttempts)")
                val searchField = findSearchInput(root)
                val searchText = searchField?.text?.toString()?.trim() ?: ""
                if (searchText.isEmpty()) {
                    AutomationState.log("  ℹ Search field empty — re-typing country")
                    tapNode(service, searchNode)
                    StepUtils.sleepMs(500)
                    pasteText(service, countryName, "search")
                    StepUtils.sleepMs(1800)
                }
                StepUtils.sleepMs(800)
                continue
            }

            val r = Rect(); match.getBoundsInScreen(r)
            AutomationState.log("ℹ Result found @ ${r.centerX()},${r.centerY()} (attempt $clickAttempts)")

            tapNode(service, match)
            StepUtils.sleepMs(1500)

            val after = service.rootInActiveWindow
            if (after == null) {
                AutomationState.log("✔ Dialog closed — success")
                return true
            }
            if (!dialogStillOpen(after)) {
                AutomationState.log("✔ Country selected on attempt $clickAttempts")
                StepUtils.sleepMs(1200)
                return true
            }

            val stillThere = findCountryResult(after, countryName, countryIso)
            if (stillThere != null) {
                AutomationState.log("  ℹ Dialog still open, result still visible — clicking again")
                StepUtils.sleepMs(600)
            } else {
                AutomationState.log("  ⚠ Result gone — check input field")
                StepUtils.sleepMs(700)
            }
        }

        AutomationState.log("✘ Country selection failed after $maxAttempts attempts")
        return false
    }

    private fun dialogStillOpen(root: AccessibilityNodeInfo): Boolean {
        val hasSearch = findByTextEquals(root, "Search") != null
        val hasCountry = NodeFinder.containsAny(root, "Country of residence", "Country/region")
        return hasSearch || hasCountry
    }

    private fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
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
            if (hasCodePattern) {
                AutomationState.log("✔ Best match: '$t'")
                return NodeFinder.findClickableParent(n) ?: n
            }

            if (fallback == null) {
                fallback = NodeFinder.findClickableParent(n) ?: n
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return fallback
    }

    // ══════════════════════════════════════════════════════
    //  hasRedError
    // ══════════════════════════════════════════════════════
    fun hasRedError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(
            root,
            "Something went wrong", "can't be used", "not valid",
            "invalid", "Enter a valid", "try again", "Try again"
        )
    }

    // ══════════════════════════════════════════════════════
    //  closeResendPopup
    // ══════════════════════════════════════════════════════
    fun closeResendPopup(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val closeTexts = arrayOf("Close", "Cancel", "Dismiss", "✕", "✖", "×", "X")

        NodeFinder.findByText(root, *closeTexts)?.let { n ->
            if (NodeFinder.isVisible(n) && NodeFinder.click(n)) {
                AutomationState.log("ℹ Resend popup closed")
                StepUtils.sleepMs(300)
                return true
            }
        }
        for (t in closeTexts) {
            val n = NodeFinder.findByTextContains(root, t)
            if (n != null && NodeFinder.isVisible(n) && NodeFinder.click(n)) {
                AutomationState.log("ℹ Resend popup closed (fallback)")
                StepUtils.sleepMs(300)
                return true
            }
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  clearNumberField
    // ══════════════════════════════════════════════════════
    fun clearNumberField(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val edit = findFocusedEditable(root) ?: findEditableUnder(root) ?: return false
        NodeFinder.click(edit)
        StepUtils.sleepMs(300)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            AutomationState.log("ℹ Number field cleared")
            StepUtils.sleepMs(300)
            return true
        }
        return false
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
        if (national.startsWith("0")) national = national.substring(1)

        val nationalDigits = national.replace(Regex("\\D"), "")
        if (nationalDigits.length < 6) {
            AutomationState.log("✘ Number too short")
            return false
        }
        val verifyTail = nationalDigits.takeLast(6)
        AutomationState.log("ℹ Target: '$national' (tail: $verifyTail)")

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
        if (phoneLabel == null) {
            AutomationState.log("✘ Phone field not found")
            return false
        }

        val bounds = Rect(); phoneLabel.getBoundsInScreen(bounds)
        val x = bounds.centerX(); val y = bounds.centerY()

        val delays = longArrayOf(2000L, 3000L, 5000L, 5000L, 5000L)
        for (attempt in 0 until 5) {
            if (StepUtils.shouldStop()) return false
            val preRoot = service.rootInActiveWindow
            val preEdit = findFocusedEditable(preRoot) ?: findEditableUnder(preRoot)
            val preText = preEdit?.text?.toString() ?: ""
            val preDigits = preText.replace(Regex("\\D"), "")
            if (preDigits.contains(verifyTail)) {
                AutomationState.log("✔ Already filled")
                return true
            }

            AutomationState.log("ℹ Attempt #${attempt + 1}/5")
            tapAt(service, x, y); StepUtils.sleepMs(400)
            tapAt(service, x, y); StepUtils.sleepMs(900)

            val fRoot = service.rootInActiveWindow
            val focused = findFocusedEditable(fRoot)
            if (focused == null) {
                AutomationState.log("⚠ Not focused")
                StepUtils.sleepMs(delays[attempt]); continue
            }
            AutomationState.log("✔ Focused")

            val existing = focused.text?.toString() ?: ""
            if (existing.isNotEmpty()) {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                StepUtils.sleepMs(400)
            }

            pasteText(service, national, "phone")
            StepUtils.sleepMs(1300)

            val vRoot = service.rootInActiveWindow
            val vEdit = findFocusedEditable(vRoot) ?: findEditableUnder(vRoot)
            val vText = vEdit?.text?.toString() ?: ""
            val vDigits = vText.replace(Regex("\\D"), "")
            AutomationState.log("ℹ After paste: '$vText' → '$vDigits'")

            if (vDigits.isNotEmpty() && vDigits.contains(verifyTail)) {
                AutomationState.log("✔ Verified")
                return true
            }
            if (vDigits.length >= 8) {
                AutomationState.log("ℹ ${vDigits.length} digits — accepting")
                return true
            }
            StepUtils.sleepMs(delays[attempt])
        }
        AutomationState.log("✘ Number not entered")
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
    enum class Result { OTP_SCREEN, ERROR, TIMEOUT, RETRY_CONTINUE }

    // ══════════════════════════════════════════════════════════════════════
    //  🟢 checkResult — Priority-Based Logic
    //
    //  Priority:
    //    ① Resend visible → OTP_SCREEN
    //    ② Captcha visible → extend + wait
    //    ③ ERROR visible → confirm 400ms → ERROR (STOPS everything else)
    //    ④ Post-captcha Continue ready → RETRY
    //    ⑤ Post-captcha processing → wait
    // ══════════════════════════════════════════════════════════════════════
    fun checkResult(service: AccessibilityService, timeoutMs: Long = 20000): Result {
        var deadline = SystemClock.uptimeMillis() + timeoutMs
        var captchaEverSeen = false
        var captchaClearedAt = 0L
        var lastCaptchaSeenAt = 0L
        var lastLog = 0L
        var errorSeenAt = 0L

        while (SystemClock.uptimeMillis() < deadline) {
            if (StepUtils.shouldStop()) return Result.TIMEOUT
            val now = SystemClock.uptimeMillis()
            val root = service.rootInActiveWindow

            if (root == null) {
                StepUtils.sleepMs(StepUtils.POLL_MS)
                continue
            }

            // ═══════════════════════════════════════════════════════════════
            //  ① SUCCESS — Resend screen (HIGHEST PRIORITY)
            // ═══════════════════════════════════════════════════════════════
            if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                AutomationState.log("  ✔ Resend visible → OTP_SCREEN")
                return Result.OTP_SCREEN
            }

            // ═══════════════════════════════════════════════════════════════
            //  ② CAPTCHA visible — extend deadline, don't interfere
            // ═══════════════════════════════════════════════════════════════
            val captchaVisible = isCaptchaPresent(root)
            if (captchaVisible) {
                captchaEverSeen = true
                captchaClearedAt = 0L
                lastCaptchaSeenAt = now
                errorSeenAt = 0L  // reset error — captcha has priority
                val ext = now + 60_000
                if (ext > deadline) deadline = ext
                val humanNode = NodeFinder.findByTextContains(root, "I am human")
                if (humanNode == null && now - lastLog > 3000) {
                    AutomationState.log("  ⏳ Captcha loading — waiting")
                    lastLog = now
                }
                StepUtils.sleepMs(StepUtils.POLL_MS)
                continue
            }

            // ═══════════════════════════════════════════════════════════════
            //  ③ ERROR — HIGH PRIORITY. Blocks everything else.
            // ═══════════════════════════════════════════════════════════════
            val errorVisible = NodeFinder.containsAny(root,
                "Something went wrong", "can't be used", "not valid",
                "invalid", "Enter a valid", "try again", "Try again")

            if (errorVisible) {
                if (errorSeenAt == 0L) {
                    errorSeenAt = now
                    AutomationState.log("  ⚠ Error text detected — verifying…")
                } else if (now - errorSeenAt >= 400) {
                    // Persisted for 400ms → real error
                    AutomationState.log("  ✘ Error confirmed (${now - errorSeenAt}ms) → ERROR")
                    return Result.ERROR
                }
                // Don't fall through — keep checking same error
                StepUtils.sleepMs(StepUtils.POLL_MS)
                continue
            } else {
                // Error disappeared?
                if (errorSeenAt > 0L) {
                    if (now - errorSeenAt > 1500) {
                        AutomationState.log("  ℹ Error text gone — continuing")
                        errorSeenAt = 0L
                    }
                    // else: give grace — UI may be transitioning (popup, etc.)
                }
            }

            // ═══════════════════════════════════════════════════════════════
            //  ④ POST-CAPTCHA — Continue ready?
            // ═══════════════════════════════════════════════════════════════
            if (captchaEverSeen) {
                val continueBtn = NodeFinder.findByText(root, "Continue", "Next")
                val hasContinue = continueBtn != null && continueBtn.isVisibleToUser

                if (hasContinue) {
                    if (captchaClearedAt == 0L) {
                        captchaClearedAt = now
                    } else if (now - captchaClearedAt > 1500) {
                        AutomationState.log("  ✔ Captcha cleared + Continue visible → RETRY")
                        return Result.RETRY_CONTINUE
                    }
                } else {
                    captchaClearedAt = 0L
                    if (now - lastCaptchaSeenAt < 45_000) {
                        val ext = now + 15_000
                        if (ext > deadline) deadline = ext
                        if (now - lastLog > 4000) {
                            AutomationState.log("  ⏳ Post-captcha processing — waiting")
                            lastLog = now
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            //  ⑤ MID-PROCESSING — number field active
            // ═══════════════════════════════════════════════════════════════
            val hasNumberField = findEditableUnder(root) != null
            if (hasNumberField && !captchaEverSeen && errorSeenAt == 0L) {
                val ext = now + 5000
                if (ext > deadline) deadline = ext
                if (now - lastLog > 4000) {
                    AutomationState.log("  ⏳ Still processing — waiting")
                    lastLog = now
                }
            }

            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        // Final verdict
        if (errorSeenAt > 0L) {
            AutomationState.log("  ✘ Result: ERROR (timeout with error seen)")
            return Result.ERROR
        }
        AutomationState.log("  ⏱ Result: TIMEOUT")
        return Result.TIMEOUT
    }

    private fun isCaptchaPresent(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return NodeFinder.containsAny(
            root,
            "I am human",
            "Security Challenge",
            "hCaptcha",
            "Verify you are human",
            "Verify you are a human",
            "Drag the",
            "Select all",
            "Choose the",
            "Choose all",
            "Please click",
            "Please drag",
            "Please select",
            "Click each",
            "Rotate the",
            "Match the"
        )
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
