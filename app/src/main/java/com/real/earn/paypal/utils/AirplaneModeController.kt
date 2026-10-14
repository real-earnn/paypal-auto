package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

/**
 * Toggle airplane mode by position in Quick Settings panel.
 *
 * User tells us: "airplane icon is at position N (1-6) in the top row".
 * We open QS, click that position, wait, click again (ON → OFF).
 */
object AirplaneModeController {

    private const val TAG = "AirplaneMode"

    /**
     * Toggle airplane mode ON then OFF — one full cycle.
     * This is what we call during automation after clearing data.
     */
    fun toggleFlightCycle(service: AccessibilityService, position: Int): Boolean {
        if (position !in 1..6) {
            AutomationState.log("✘ Flight position invalid: $position")
            return false
        }

        return try {
            AutomationState.log("✈ Flight cycle (pos $position)...")

            // 1. Open Quick Settings
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            Thread.sleep(1800)

            // 2. Find tiles
            val root = service.rootInActiveWindow
            val tiles = findTiles(root)

            AutomationState.log("ℹ Found ${tiles.size} tiles")

            if (tiles.size < position) {
                AutomationState.log("✘ Position $position > tiles (${tiles.size})")
                closeQuickSettings(service)
                return false
            }

            val target = tiles[position - 1]

            // 3. Click ON
            if (!NodeFinder.click(target)) {
                AutomationState.log("✘ Tile click fail")
                closeQuickSettings(service)
                return false
            }
            AutomationState.log("✈ Flight ON")
            Thread.sleep(3500)

            // 4. Re-fetch tiles (panel may scroll/reset)
            val root2 = service.rootInActiveWindow
            val tiles2 = findTiles(root2)

            // 5. Click same position → OFF
            if (tiles2.size >= position) {
                NodeFinder.click(tiles2[position - 1])
                AutomationState.log("✈ Flight OFF")
                Thread.sleep(2500)
            } else {
                AutomationState.log("⚠ Reopen skip — closing QS")
            }

            // 6. Close Quick Settings
            closeQuickSettings(service)
            true

        } catch (e: Exception) {
            Log.e(TAG, "toggleFlightCycle error: ${e.message}")
            AutomationState.log("✘ Flight error: ${e.message}")
            false
        }
    }

    /**
     * Test — click ON then OFF once from a UI button.
     */
    fun testPosition(service: AccessibilityService, position: Int): Boolean {
        return toggleFlightCycle(service, position)
    }

    private fun closeQuickSettings(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(600)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        } catch (e: Exception) { false }
    }

    /**
     * Find all clickable tiles in QS panel, sorted by (row, column).
     * Filters out status bar, QS panel, giant containers.
     */
    private fun findTiles(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()

        val screenRect = Rect()
        root.getBoundsInScreen(screenRect)
        val screenHeight = screenRect.height()

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            if (n.isClickable && n.isVisibleToUser) {
                val r = Rect()
                n.getBoundsInScreen(r)

                val w = r.width()
                val h = r.height()

                // Tile heuristic:
                // - Reasonable size (not giant panel, not tiny icon)
                // - In the tile area (below status bar, above bottom 40%)
                val inTileArea = r.top > 100 && r.top < screenHeight * 0.6
                val sizeOk = w in 100..550 && h in 100..550

                if (inTileArea && sizeOk) {
                    candidates.add(n)
                }
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }

        // Deduplicate by bounds — prefer the deepest (smallest) node
        val byBounds = mutableMapOf<String, AccessibilityNodeInfo>()
        for (c in candidates) {
            val r = Rect(); c.getBoundsInScreen(r)
            val key = "${r.left},${r.top},${r.right},${r.bottom}"
            val existing = byBounds[key]
            if (existing == null) {
                byBounds[key] = c
            }
        }

        val unique = byBounds.values.toList()

        // Group into rows by top Y (tolerance 60px), sort each row by X
        val withBounds = unique.map { n ->
            val r = Rect(); n.getBoundsInScreen(r); n to r
        }.sortedBy { it.second.top }

        val rows = mutableListOf<MutableList<Pair<AccessibilityNodeInfo, Rect>>>()
        for ((n, r) in withBounds) {
            val existingRow = rows.find { row ->
                row.isNotEmpty() && Math.abs(row[0].second.top - r.top) < 60
            }
            if (existingRow != null) {
                existingRow.add(n to r)
            } else {
                rows.add(mutableListOf(n to r))
            }
        }

        rows.forEach { it.sortBy { p -> p.second.left } }

        return rows.flatMap { row -> row.map { it.first } }
    }
}
