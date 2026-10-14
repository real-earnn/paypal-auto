package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

/**
 * Icon-based Airplane mode toggle — non-rooted, no position needed.
 * Uses contentDescription "Airplane mode" which is standard on all Android.
 *
 * STEP-BY-STEP, no blind sleeps.
 */
object AirplaneModeController {

    fun toggleCycle(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("✈ Flight cycle start")

            // ── STEP 1: Open Quick Settings ─────────────────
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

            // STEP 2: Wait for tile to appear (up to 3 sec)
            if (!waitForAirplaneTile(service, 3000)) {
                AutomationState.log("✘ Airplane tile not found")
                closeQS(service)
                return false
            }
            AutomationState.log("ℹ Tile found")

            // STEP 3: Click tile → ON
            val tile = findAirplaneTile(service)
            if (tile == null || !NodeFinder.click(tile)) {
                AutomationState.log("✘ Click ON fail")
                closeQS(service)
                return false
            }

            // STEP 4: Wait until system reports airplane ON
            if (!waitForAirplaneState(service, true, 4000)) {
                AutomationState.log("⚠ ON state not confirmed")
            } else {
                AutomationState.log("✔ Flight ON")
            }

            // STEP 5: Wait 3 sec with airplane ON
            wait(3000)

            // STEP 6: Re-find tile (panel may have scrolled / state text changed)
            var tile2 = findAirplaneTile(service)
            if (tile2 == null) {
                // Reopen panel
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                wait(1200)
                tile2 = findAirplaneTile(service)
            }
            if (tile2 == null) {
                AutomationState.log("✘ Tile lost for OFF")
                closeQS(service)
                return false
            }

            // STEP 7: Click → OFF
            if (!NodeFinder.click(tile2)) {
                AutomationState.log("✘ Click OFF fail")
                closeQS(service)
                return false
            }

            // STEP 8: Wait until system reports OFF
            if (!waitForAirplaneState(service, false, 4000)) {
                AutomationState.log("⚠ OFF state not confirmed")
            } else {
                AutomationState.log("✔ Flight OFF")
            }

            // STEP 9: Close QS
            closeQS(service)
            wait(500)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) } catch (_: Exception) {}
            false
        }
    }

    // ── Wait until airplane tile appears in QS ────────────
    private fun waitForAirplaneTile(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (findAirplaneTile(service) != null) return true
            wait(300)
        }
        return false
    }

    // ── Wait until system reports the desired state ───────
    private fun waitForAirplaneState(
        service: AccessibilityService,
        desired: Boolean,
        timeoutMs: Long
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val cur = isAirplaneModeOn(service)
            if (cur == desired) return true
            wait(250)
        }
        return false
    }

    // ── Find airplane tile by contentDescription / id / text ──
    private fun findAirplaneTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            // Standard Android: contentDescription = "Airplane mode" / "Flight mode"
            if (desc.contains("airplane") ||
                desc.contains("flight mode") ||
                desc.contains("aeroplane") ||
                text.contains("airplane mode") ||
                text.contains("flight mode") ||
                id.contains("airplane")
            ) {
                // Clickable ancestor or self
                val clickable = if (n.isClickable) n else findClickableAncestor(n)
                if (clickable != null) return clickable
                return n
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur = node.parent
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun closeQS(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            wait(400)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) == 1
        } catch (e: Exception) { false }
    }

    private fun wait(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
