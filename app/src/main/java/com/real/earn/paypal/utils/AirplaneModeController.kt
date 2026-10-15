package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object AirplaneModeController {

    fun toggleCycle(service: AccessibilityService): Boolean = strictToggle(service)

    fun strictToggle(service: AccessibilityService): Boolean {
        return try {
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                if (openQsAndWaitTile(service, 2000)) {
                    dumpScreenText(service, "Normalize-OFF-before-click")
                    clickTile(service)
                    waitAirplaneState(service, false, 2000)
                }
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1500)
            }

            AutomationState.log("✈ Flight ON")
            if (!openQsAndWaitTile(service, 2500)) {
                AutomationState.log("✘ QS tile not found")
                dumpScreenText(service, "QS-not-open-FAIL")
                goHome(service); return false
            }

            // 🟢 DUMP entire screen text BEFORE clicking
            dumpScreenText(service, "Before-ON-click")

            if (!clickTile(service)) {
                AutomationState.log("✘ ON click fail")
                dumpScreenText(service, "ON-click-FAIL")
                goHome(service); return false
            }
            if (!waitAirplaneState(service, true, 3000)) {
                AutomationState.log("✘ ON not confirmed")
                dumpScreenText(service, "ON-not-confirmed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight ON")

            StepUtils.sleepMs(900)

            if (!isQsOpen(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(500)
            }

            // 🟢 DUMP before OFF click (state changed now)
            dumpScreenText(service, "Before-OFF-click")

            if (!clickTile(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(500)
                if (!clickTile(service)) {
                    AutomationState.log("✘ OFF click fail")
                    dumpScreenText(service, "OFF-click-FAIL")
                    goHome(service); return false
                }
            }
            if (!waitAirplaneState(service, false, 3000)) {
                AutomationState.log("✘ OFF not confirmed")
                dumpScreenText(service, "OFF-not-confirmed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight OFF")

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.waitForPackageExit(service, "systemui", 1500)

            AutomationState.log("✔ Flight cycle done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            goHome(service)
            false
        }
    }

    private fun openQsAndWaitTile(service: AccessibilityService, timeoutMs: Long): Boolean {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (isQsOpen(service) && findTile(service) != null) return true
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    private fun isQsOpen(service: AccessibilityService): Boolean =
        StepUtils.isPackageForeground(service, "systemui")

    private fun clickTile(service: AccessibilityService): Boolean {
        val tile = findTile(service) ?: return false
        return NodeFinder.click(tile)
    }

    private fun waitAirplaneState(service: AccessibilityService, desired: Boolean, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (isAirplaneModeOn(service) == desired) return true
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  🟢 SCREEN TEXT DUMP — pura screen ka saara text log karo
    // ══════════════════════════════════════════════════════════════════════════
    private fun dumpScreenText(service: AccessibilityService, tag: String) {
        val root = service.rootInActiveWindow ?: run {
            AutomationState.log("═══ DUMP [$tag] — root null ═══")
            return
        }

        val screenR = Rect(); root.getBoundsInScreen(screenR)
        AutomationState.log("═══ DUMP [$tag] screen=${screenR.width()}x${screenR.height()} pkg=${root.packageName} ═══")

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        var lineCount = 0

        while (queue.isNotEmpty() && count < 200) {
            val n = queue.removeFirst()
            count++

            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            val id = n.viewIdResourceName?.substringAfterLast("/") ?: ""
            val cls = n.className?.toString()?.substringAfterLast(".") ?: ""

            // Only log nodes with text OR contentDescription (skip empty containers)
            if (t.isNotEmpty() || d.isNotEmpty()) {
                val r = Rect(); n.getBoundsInScreen(r)
                val clickable = if (n.isClickable) "C" else "-"
                val editable = if (n.isEditable) "E" else "-"
                val vis = if (n.isVisibleToUser) "V" else "-"

                val textShort = t.take(40).replace("\n", " ")
                val descShort = d.take(40).replace("\n", " ")

                AutomationState.log("  #$lineCount ${clickable}${editable}${vis} [${r.left},${r.top},${r.right},${r.bottom}] cls=$cls id=$id | T='$textShort' D='$descShort'")
                lineCount++
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        AutomationState.log("═══ END DUMP [$tag] — ${lineCount} nodes with text ═══")
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SMART TILE PICKER — Score Based + Full Candidate Log
    // ══════════════════════════════════════════════════════════════════════════
    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        data class Candidate(
            val node: AccessibilityNodeInfo,
            val score: Int,
            val desc: String,
            val text: String,
            val id: String,
            val y: Int
        )

        val screenR = Rect(); root.getBoundsInScreen(screenR)
        val screenArea = screenR.width() * screenR.height()

        val candidates = mutableListOf<Candidate>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            val desc = n.contentDescription?.toString()?.trim() ?: ""
            val text = n.text?.toString()?.trim() ?: ""
            val id = n.viewIdResourceName ?: ""

            val descL = desc.lowercase()
            val textL = text.lowercase()
            val idL = id.lowercase()

            val saysAirplane =
                descL.contains("airplane") || descL.contains("flight mode") ||
                textL.contains("airplane") || textL.contains("flight mode") ||
                idL.contains("airplane")

            if (saysAirplane && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)

                var score = 0

                // 1. Size check
                val nodeArea = r.width() * r.height()
                if (nodeArea > (screenArea * 0.6).toInt()) score += 30

                // 2. Status bar position
                if (r.top < 150) score += 20

                // 3. Match quality
                when {
                    descL == "airplane mode" || descL == "flight mode" -> score += 0
                    textL == "airplane mode" || textL == "flight mode" -> score += 2
                    descL.startsWith("airplane mode") || descL.startsWith("flight mode") -> score += 3
                    textL.startsWith("airplane mode") || textL.startsWith("flight mode") -> score += 5
                    else -> score += 8
                }

                // 4. Conflicting context
                val siblingTexts = getParentAndSiblingTexts(n)
                val combined = (descL + " " + textL + " " + siblingTexts).lowercase()

                if (combined.contains("mobile data")) score += 15
                if (combined.contains("wi-fi") || combined.contains("wifi")) score += 15
                if (combined.contains("cellular")) score += 10
                if (combined.contains("bluetooth")) score += 10
                if (combined.contains("hotspot")) score += 10

                val clickable = if (n.isClickable) n else (findClickableAncestor(n) ?: n)
                candidates.add(Candidate(clickable, score, desc, text, id, r.top))

                // Don't descend — we matched this one
                continue
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        if (candidates.isEmpty()) {
            AutomationState.log("✘ No airplane candidates found")
            return null
        }

        // Sort by score ascending
        val sorted = candidates.sortedBy { it.score }

        AutomationState.log("═══ TILE CANDIDATES (${sorted.size}) ═══")
        sorted.forEachIndexed { i, c ->
            val d = c.desc.take(45).ifEmpty { "—" }
            val t = c.text.take(25).ifEmpty { "—" }
            val idShort = c.id.substringAfterLast("/").take(25).ifEmpty { "—" }
            AutomationState.log("  [${i}] score=${c.score} y=${c.y} id='$idShort' T='$t' D='$d'")
        }

        val picked = sorted.first()
        AutomationState.log("✔ PICKED: score=${picked.score} y=${picked.y} D='${picked.desc.take(40)}'")
        return picked.node
    }

    private fun getParentAndSiblingTexts(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        try {
            val parent = node.parent ?: return ""
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                sibling.text?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
                sibling.contentDescription?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur = node.parent
        var d = 0
        while (cur != null && d < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent; d++
        }
        return null
    }

    private fun goHome(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(500)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) { false }
}
