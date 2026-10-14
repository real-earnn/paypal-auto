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

    /**
     * Fast flight cycle:
     *  QS open → wait tile (max 1.5s) → click ON → wait state (max 2s)
     *  → 800ms → click OFF → wait state (max 2s) → HOME.
     * No signal wait — signal will come back during next steps.
     */
    fun strictToggle(service: AccessibilityService): Boolean {
        return try {
            // Normalize if already ON
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                if (openQsAndWaitTile(service, 1500)) {
                    clickTile(service)
                    waitAirplaneState(service, false, 2000)
                }
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1200)
            }

            AutomationState.log("✈ Flight ON")
            if (!openQsAndWaitTile(service, 1500)) {
                AutomationState.log("✘ QS tile not found")
                goHome(service); return false
            }

            if (!clickTile(service)) {
                AutomationState.log("✘ ON click fail")
                goHome(service); return false
            }
            if (!waitAirplaneState(service, true, 2500)) {
                AutomationState.log("✘ ON state not confirmed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight ON")

            // 800ms ON
            StepUtils.sleepMs(800)

            // Ensure QS still open
            if (!isQsOpen(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(300)
            }

            if (!clickTile(service)) {
                // retry once
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(300)
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
            StepUtils.waitForPackageExit(service, "systemui", 1200)

            AutomationState.log("✔ Flight cycle done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            goHome(service)
            false
        }
    }

    // ══ helpers ══

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
     * Smart picker:
     *  - Collect all "airplane" matches
     *  - Skip status bar (top 150px)
     *  - 1 → it, 2 → 2nd, 3+ → middle by Y
     */
    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        val all = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            val isMatch =
                desc.contains("airplane") || desc.contains("flight mode") ||
                text.contains("airplane mode") || text.contains("flight mode") ||
                id.contains("airplane")

            if (isMatch) {
                val clickable = if (n.isClickable) n else (findClickableAncestor(n) ?: n)
                all.add(clickable)
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        if (all.isEmpty()) return null

        val seen = mutableSetOf<String>()
        val unique = all.filter { node ->
            val r = Rect(); node.getBoundsInScreen(r)
            val key = "${r.left},${r.top},${r.right},${r.bottom}"
            if (seen.contains(key)) false else { seen.add(key); true }
        }

        val nonStatusBar = unique.filter {
            val r = Rect(); it.getBoundsInScreen(r); r.top > 150
        }

        val pool = if (nonStatusBar.isNotEmpty()) nonStatusBar else unique
        val sorted = pool.sortedBy {
            val r = Rect(); it.getBoundsInScreen(r); r.top
        }

        val picked = when {
            sorted.size == 1 -> sorted[0]
            sorted.size == 2 -> sorted[1]
            else -> sorted[sorted.size / 2]
        }

        val pr = Rect(); picked.getBoundsInScreen(pr)
        AutomationState.log("ℹ Tile: ${sorted.size} matches → picked y=${pr.top}")
        return picked
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
            StepUtils.sleepMs(400)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) { false }
}
