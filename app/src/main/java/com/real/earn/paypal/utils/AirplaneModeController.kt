package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

/**
 * Simple flight cycle:
 *  QS open → ON → 1s → OFF → HOME (close QS completely)
 */
object AirplaneModeController {

    fun strictToggle(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("✈ Flight ON...")

            // ① Open QS
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            if (!waitForQs(service, 2000)) {
                AutomationState.log("✘ QS not shown")
                return false
            }

            // ② Find tile
            val tile1 = waitForTile(service, 3000)
            if (tile1 == null) {
                AutomationState.log("✘ Tile not found")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                return false
            }

            // ③ Click ON
            if (!NodeFinder.click(tile1)) {
                AutomationState.log("✘ ON click fail")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                return false
            }
            waitAirplaneState(service, true, 4000)
            AutomationState.log("✔ Flight ON")

            // ④ 1 second wait
            sleepMs(1000)

            // ⑤ Click OFF (re-find tile)
            val tile2 = findTile(service)
            if (tile2 == null || !NodeFinder.click(tile2)) {
                AutomationState.log("✘ OFF click fail")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                return false
            }
            waitAirplaneState(service, false, 4000)
            AutomationState.log("✔ Flight OFF")

            // ⑥ HOME — close QS completely
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            sleepMs(900)

            AutomationState.log("✔ Flight cycle done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } catch (_: Exception) {}
            false
        }
    }

    // ══ Helpers ══

    private fun waitForQs(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            val pkg = root?.packageName?.toString() ?: ""
            if (pkg.contains("systemui")) return true
            sleepMs(200)
        }
        return false
    }

    private fun waitForTile(service: AccessibilityService, timeoutMs: Long): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            findTile(service)?.let { return it }
            sleepMs(250)
        }
        return null
    }

    private fun waitAirplaneState(service: AccessibilityService, desired: Boolean, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (isAirplaneModeOn(service) == desired) return true
            sleepMs(250)
        }
        return false
    }

    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("airplane") || desc.contains("flight mode") ||
                text.contains("airplane mode") || text.contains("flight mode") ||
                id.contains("airplane")
            ) {
                return if (n.isClickable) n else (findClickableAncestor(n) ?: n)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
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

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) { false }

    private fun sleepMs(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
