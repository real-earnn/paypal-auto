package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

/**
 * Toggle airplane mode via Accessibility Service (non-rooted).
 *
 * Method: open Quick Settings panel → tap airplane tile → close panel.
 * Handles the confirm dialog that some OEMs (Samsung, Xiaomi) show.
 */
object AirplaneModeController {

    private const val TAG = "AirplaneMode"

    fun setAirplaneMode(
        service: AccessibilityService,
        enable: Boolean,
        waitAfterMs: Long = 3000
    ): Boolean {
        return try {
            // Skip if already in desired state
            val current = isAirplaneModeOn(service)
            if (current == enable) {
                AutomationState.log("ℹ Airplane already ${if (enable) "ON" else "OFF"}")
                return true
            }

            AutomationState.log(if (enable) "✈ Airplane ON..." else "✈ Airplane OFF...")

            // Open Quick Settings
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            Thread.sleep(1200)

            // Find and click the tile
            val root = service.rootInActiveWindow
            val tile = findAirplaneTile(root)

            if (tile == null) {
                AutomationState.log("✘ Airplane tile nahi mila")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return false
            }

            if (!NodeFinder.click(tile)) {
                AutomationState.log("✘ Airplane tile click fail")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return false
            }

            Thread.sleep(800)

            // Handle OEM confirm dialog if it appears
            handleConfirmDialogIfAny(service)

            Thread.sleep(waitAfterMs - 800)

            // Close quick settings
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(600)

            val verify = isAirplaneModeOn(service) == enable
            AutomationState.log(if (verify) "✔ Airplane ${if (enable) "ON" else "OFF"}" else "✘ Airplane verify fail")
            verify
        } catch (e: Exception) {
            Log.e(TAG, "Airplane error: ${e.message}")
            AutomationState.log("✘ Airplane error: ${e.message}")
            false
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun handleConfirmDialogIfAny(service: AccessibilityService) {
        val root = service.rootInActiveWindow ?: return
        val confirmTexts = arrayOf("Turn on", "Turn off", "OK", "Yes", "Confirm", "Allow")
        val node = NodeFinder.findByText(root, *confirmTexts)
        if (node != null) {
            Log.d(TAG, "OEM confirm dialog detected — tapping")
            NodeFinder.click(node)
            Thread.sleep(600)
        }
    }

    private fun findAirplaneTile(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        // Text based
        val texts = arrayOf(
            "Airplane mode", "Airplane", "Flight mode", "Flight",
            "✈️", "✈"
        )
        NodeFinder.findByTextContains(root, *texts)?.let { return it }

        // Common view IDs
        val ids = arrayOf(
            "com.android.systemui:id/airplane",
            "com.android.systemui:id/airplane_mode",
            "com.android.systemui:id/tile_airplane",
            "com.android.systemui:id/qs_airplane",
            "com.android.systemui:id/airplane_tile"
        )
        for (id in ids) {
            NodeFinder.findByViewId(root, id)?.let { return it }
        }

        // Deep search by desc / id
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""
            if (d.contains("airplane") || d.contains("flight") || id.contains("airplane")) {
                return NodeFinder.findClickableParent(n) ?: n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
