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
                // Skip only if number screen visible
                val hasEditField = findEditableUnder(root) != null
                val hasEmailText = NodeFinder.containsAny(
                    root, "email or number", "Email or phone number"
                )
                if (hasEditField || hasEmailText) {
                    AutomationState.log("ℹ Number screen already visible")
                    return true
                }

                // Find Get Started (text or contentDescription)
                val node = findByTextEquals(root, "Get Started")
                    ?: findByTextContains(root, "Get Started")
                    ?: findByContentDescContains(root, "Get Started")

                if (node != null) {
                    AutomationState.log("ℹ Get Started FOUND — clicking")
                    val ok = NodeFinder.click(node)
                        || (NodeFinder.findClickableParent(node)
                                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
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
    //  CLICK US  — pure text detection, no hardcoded location
    // ══════════════════════════════════════════════════════
    fun clickUsButton(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        AutomationState.log("ℹ Searching for 'US' text on screen…")
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastDump = 0L
        var lastLog = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            val root = service.rootInActiveWindow
            if (root != null) {
                // Dialog already open?
                if (dialogOrSearchVisible(root)) {
                    AutomationState.log("✔ Country dialog already open")
                    return true
                }

                // ═══ Find "US" text — MULTIPLE strategies ═══
                val usNode = findUsNode(root)

                if (usNode != null) {
                    AutomationState.log("ℹ US found — attempting click")
                    val clicked = tryClick(usNode)
                    if (clicked) {
                        // 2 sec wait, then verify
                        StepUtils.sleepMs(2000)
                        val r2 = service.rootInActiveWindow
                        if (r2 != null && dialogOrSearchVisible(r2)) {
                            AutomationState.log("✔ Dialog opened via US click")
                            return true
                        } else {
                            AutomationState.log("⚠ Click done but dialog not visible — retrying")
                        }
                    } else {
                        AutomationState.log("⚠ US click failed")
                    }
                } else {
                    // Log every 4s that US not found
                    val now = SystemClock.uptimeMillis()
                    if (now - lastLog > 4000) {
                        AutomationState.log("ℹ 'US' not yet visible — waiting…")
                        lastLog = now
                    }
                }

                // Dump all text nodes once (after 8s if still not found)
                val elapsed = timeoutMs - (end - SystemClock.uptimeMillis())
                if (elapsed > 8000 && SystemClock.uptimeMillis() - lastDump > 10000) {
                    dumpAllText(root)
                    lastDump = SystemClock.uptimeMillis()
                }
            }

            StepUtils.sleepMs(500)
        }

        AutomationState.log("✘ US button not found in ${timeoutMs}ms")
        // Final dump for debugging
        val r = service.rootInActiveWindow
        if (r != null) dumpAllText(r)
        return false
    }

    /**
     * Find "US" node using multiple strategies:
     *   1. Exact text "US"
     *   2. Text starts with "US" (e.g., "US ")
     *   3. Content description equals "US"
     *   4. Text containing "US" as standalone word
     */
    private fun findUsNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: exact text "US"
        findByTextEquals(root, "US")?.let {
            AutomationState.log("ℹ US match: exact text")
            return it
        }

        // Strategy 2: text starts with "US" (short)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            // "US" exact, "US " trimmed, "US\n"
            if (t.equals("US", ignoreCase = true) && n.isVisibleToUser) {
                AutomationState.log("ℹ US match: case-insensitive")
                return n
            }
            // desc equals "US" or "Country: US"
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (d.equals("US", ignoreCase = true) && n.isVisibleToUser) {
                AutomationState.log("ℹ US match: contentDescription")
                return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        // Strategy 3: any visible node whose text IS "US" ignoring spaces
        queue.clear(); queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim()?.uppercase() ?: ""
            if (t == "US" && n.isVisibleToUser) {
                AutomationState.log("ℹ US match: uppercase compare")
                return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        return null
    }

    private fun tryClick(node: AccessibilityNodeInfo): Boolean {
        // Try direct click
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true

        // Try clickable parent
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true

        // Try clickable child
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }

        // Fallback: NodeFinder
        return NodeFinder.click(node)
    }

    /** Dump all visible text nodes to log for debugging */
    private fun dumpAllText(root: AccessibilityNodeInfo) {
        AutomationState.log("═══ SCREEN TEXT DUMP ═══")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 60) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (t.isNotEmpty() || d.isNotEmpty()) {
                val shown = when {
                    t.isNotEmpty() && d.isNotEmpty() -> "T='$t' D='$d'"
                    t.isNotEmpty() -> "T='$t'"
                    else -> "D='$d'"
                }
                AutomationState.log("  • $shown")
                count++
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        AutomationState.log("═══ END DUMP ($count nodes) ═══")
    }

    private fun dialogOrSearchVisible(root: AccessibilityNodeInfo): Boolean {
        if (NodeFinder.containsAny(root, "Country of residence", "Country/region")) return true
        if (findByTextEquals(root, "Search") != null) return true
        return false
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
            AutomationState.log("✘ 'Search' text not found")
            return false
        }

        val clickTarget = NodeFinder.findClickableParent(searchNode) ?: searchNode
        if (!clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            NodeFinder.click(searchNode)
        }
        AutomationState.log("ℹ Search clicked")
        StepUtils.sleepMs(700)

        var inputField = findEditableUnder(service.rootInActiveWindow) ?: searchNode
        NodeFinder.setText(inputField, countryName)
        AutomationState.log("ℹ Typed: $countryName")
        StepUtils.sleepMs(1800)

        val end = SystemClock.uptimeMillis() + 4500
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val r = service.rootInActiveWindow
            val match = findCountryResult(r, countryName, countryIso)
            if (match != null && NodeFinder.click(match)) {
                AutomationState.log("✔ Country clicked: $countryName")
                StepUtils.sleepMs(1500)
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

        StepUtils.sleepMs(1500)

        var phoneLabel: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                phoneLabel = findByTextContains(root, "phone number")
                if (phoneLabel != null) break
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }

        if (phoneLabel == null) {
            AutomationState.log("✘ 'phone number' not found")
            return false
        }

        val clickTarget = NodeFinder.findClickableParent(phoneLabel) ?: phoneLabel
        clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        StepUtils.sleepMs(600)

        val inputField = findEditableUnder(service.rootInActiveWindow) ?: phoneLabel
        NodeFinder.setText(inputField, national)
        AutomationState.log("ℹ Number typed: $national")
        StepUtils.sleepMs(800)
        return true
    }

    // ══════════════════════════════════════════════════════
    //  CONTINUE
    // ══════════════════════════════════════════════════════
    fun tapContinue(service: AccessibilityService): Boolean {
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
