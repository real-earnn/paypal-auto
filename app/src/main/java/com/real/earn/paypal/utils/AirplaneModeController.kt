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
                    clickTile(service)
                    waitAirplaneState(service, false, 2000)
                }
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1500)
            }

            AutomationState.log("✈ Flight ON")
            if (!openQsAndWaitTile(service, 2000)) {
                AutomationState.log("✘ QS tile not found")
                goHome(service); return false
            }

            if (!clickTile(service)) {
                AutomationState.log("✘ ON click fail")
                goHome(service); return false
            }
            if (!waitAirplaneState(service, true, 2500)) {
                AutomationState.log("✘ ON not confirmed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight ON")

            StepUtils.sleepMs(800)

            if (!isQsOpen(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(400)
            }

            if (!clickTile(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(400)
                if (!clickTile(service)) {
                    AutomationState.log("✘ OFF click fail")
                    goHome(service); return false
                }
            }
            if (!waitAirplaneState(service, false, 2500)) {
                AutomationState.log("✘ OFF not confirmed")
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

    /**
     * 🎯 SMARTER TILE FINDER
     *  - Collects all matches ("airplane"/"flight")
     *  - Filters out top 150px (status bar)
     *  - Filters out nodes that ALSO contain "mobile data"/"wifi"/"cellular" (sibling text)
     *  - 1 left → that ; 2+ → pick middle by Y
     */
    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        data class Candidate(val node: AccessibilityNodeInfo, val y: Int, val isPure: Boolean)

        val candidates = mutableListOf<Candidate>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            val airplaneSignal =
                desc.contains("airplane") || desc.contains("flight mode") ||
                text.contains("airplane") || text.contains("flight mode") ||
                id.contains("airplane")

            if (airplaneSignal && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)

                // Skip status bar (top 150px)
                if (r.top < 150) {
                    // Skip this node's children too
                    for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
                    continue
                }

                // 🟢 Check if this node contains "mobile data"/"wifi" text anywhere inside it
                val combined = (desc + " " + text + " " + getChildTexts(n)).lowercase()
                val isConflicting =
                    combined.contains("mobile data") ||
                    combined.contains("wi-fi") ||
                    combined.contains("wifi") ||
                    combined.contains("cellular") ||
                    combined.contains("bluetooth") ||
                    combined.contains("hotspot")

                // "Pure" = airplane mode alone (or as its own tile)
                val isPure = !isConflicting

                val clickable = if (n.isClickable) n else (findClickableAncestor(n) ?: n)
                candidates.add(Candidate(clickable, r.top, isPure))

                // Don't descend into children if this matched
                continue
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        if (candidates.isEmpty()) return null

        // Prefer pure matches
        val pureOnes = candidates.filter { it.isPure }
        val pool = if (pureOnes.isNotEmpty()) pureOnes else candidates

        val sorted = pool.sortedBy { it.y }
        val picked = when {
            sorted.size == 1 -> sorted[0]
            else -> sorted[sorted.size / 2]
        }

        AutomationState.log("ℹ Tile: ${candidates.size} matches (${pureOnes.size} pure) → picked y=${picked.y}")
        return picked.node
    }

    private fun getChildTexts(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < 10) {
            val n = queue.removeFirst()
            n.text?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
            n.contentDescription?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
            count++
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
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
