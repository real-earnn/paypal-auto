package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.runBlocking

/**
 * 🟢 Flight Mode — 5× cycles
 *
 * Strategy:
 *   1) Shizuku available → `cmd connectivity airplane-mode enable/disable` × 5
 *   2) Shizuku not available → QS tile tap:
 *      - Icon recognition via contentDescription (best)
 *      - Tap **10-12dp ABOVE the text center** so panel doesn't close
 *      - Reopen panel if closed
 */
object AirplaneModeController {

    private val AIRPLANE_KEYWORDS = arrayOf(
        "airplane", "aeroplane", "air plane", "air-plane",
        "flight mode", "flight-mode", "in-flight", "inflight", "flight",
        "ایروپلین", "ایرو پلین", "فلائٹ", "ہوائی جہاز", "ہوائی", "طیارہ",
        "एयरप्लेन", "एयरोप्लेन", "फ्लाइट", "हवाई जहाज", "विमान",
        "طيران", "الطيران", "وضع الطيران",
        "uçak", "ucak", "uçuş",
        "самолет", "самолёт", "авиарежим",
        "mode avion", "avion",
        "modo avión", "modo avion",
        "flugmodus", "flug",
        "modalità aereo", "modalita aereo",
    )

    /**
     * 5 بار on/off cycle — پہلے Shizuku کوشش، پھر accessibility fallback
     */
    fun strictToggle(service: AccessibilityService): Boolean {
        val prefs = AppPrefs(service)
        val cycles = prefs.flightCycles

        // ═══════════════════════════════════════════════════════════
        // 🟢 PATH 1: Shizuku — fast, device-agnostic, zero UI
        // ═══════════════════════════════════════════════════════════
        if (prefs.shizukuModeEnabled && ShizukuShell.isAvailable() && ShizukuShell.hasPermission()) {
            AutomationState.log("✈ Shizuku flight cycle ×$cycles")
            val ok = runBlocking {
                var success = true
                for (i in 1..cycles) {
                    if (!ShizukuShell.setAirplaneMode(true)) { success = false; break }
                    Thread.sleep(900)
                    if (!ShizukuShell.setAirplaneMode(false)) { success = false; break }
                    Thread.sleep(900)
                    AutomationState.log("  ↳ cycle $i/$cycles done")
                }
                success
            }
            if (ok) {
                AutomationState.log("✔ Shizuku flight cycle complete")
                return true
            }
            AutomationState.log("⚠ Shizuku flight failed — falling back to accessibility")
        }

        // ═══════════════════════════════════════════════════════════
        // PATH 2: Accessibility Quick Settings tile tap
        // ═══════════════════════════════════════════════════════════
        return try {
            // Normalize: اگر پہلے سے ON ہے تو OFF کر دو
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                tapTileForTarget(service, targetOn = false)
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1500)
            }

            // 5 بار on/off
            var allOk = true
            for (i in 1..cycles) {
                AutomationState.log("✈ Cycle $i/$cycles — ON")
                if (!tapTileForTarget(service, targetOn = true)) {
                    AutomationState.log("✘ Cycle $i ON failed")
                    allOk = false
                    break
                }
                StepUtils.sleepMs(700)

                AutomationState.log("✈ Cycle $i/$cycles — OFF")
                if (!tapTileForTarget(service, targetOn = false)) {
                    AutomationState.log("✘ Cycle $i OFF failed")
                    allOk = false
                    break
                }
                StepUtils.sleepMs(700)
            }

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.waitForPackageExit(service, "systemui", 1500)

            if (allOk) AutomationState.log("✔ Flight cycle complete ($cycles cycles)")
            allOk
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            goHome(service)
            false
        }
    }

    /**
     * QS panel کھولیں، tile تلاش کریں، tap کریں، panel بند ہو تو reopen.
     */
    private fun tapTileForTarget(service: AccessibilityService, targetOn: Boolean): Boolean {
        val prefs = AppPrefs(service)

        // ═══ Step 1: QS open کریں ═══
        if (!openQs(service)) {
            AutomationState.log("  ✘ QS open fail")
            return false
        }
        StepUtils.sleepMs(600)

        // ═══ Step 2: Tile تلاش کریں — icon recognition (best) → text recognition ═══
        var tileNode: AccessibilityNodeInfo? = null
        var tapX = 0
        var tapY = 0

        // پہلے saved coords چیک
        if (prefs.flightTapX > 0 && prefs.flightTapY > 0) {
            AutomationState.log("  ℹ Using saved coords (${prefs.flightTapX}, ${prefs.flightTapY})")
            tapX = prefs.flightTapX
            tapY = prefs.flightTapY
        } else {
            tileNode = findAirplaneTile(service) ?: run {
                AutomationState.log("  ✘ No airplane tile found")
                return false
            }
            val r = Rect(); tileNode.getBoundsInScreen(r)

            // 🟢 آپ کی requirement: text center سے 10-12dp اوپر tap
            // Text center is roughly tile center for QS tiles
            // 12dp اوپر جانے سے panel dismiss nahi hota
            val density = service.resources.displayMetrics.density
            val offsetPx = (12 * density).toInt()

            tapX = r.centerX()
            tapY = r.centerY() - offsetPx
            // اگر tap point tile کے top سے اوپر چلا جائے تو پھر center پر لے آئیں
            if (tapY < r.top + 4) tapY = r.centerY()

            AutomationState.log("  ℹ Tile bounds=${r.width()}×${r.height()} center=(${r.centerX()},${r.centerY()}) → tap=($tapX,$tapY)")
        }

        // ═══ Step 3: Tap — up to 4 attempts (same coords) ═══
        for (attempt in 1..4) {
            if (StepUtils.shouldStop()) return false

            AutomationState.log("  ↳ Tap #$attempt at ($tapX, $tapY)")
            tapAt(service, tapX, tapY)

            if (waitAirplaneState(service, targetOn, 2800)) {
                // ✅ success — save coords for next time
                if (prefs.flightTapX == 0) {
                    prefs.flightTapX = tapX
                    prefs.flightTapY = tapY
                }
                AutomationState.log("  ✔ Success on attempt #$attempt")
                return true
            }

            // ═══ Panel بند ہو گیا؟ ═══
            if (!isQsOpen(service)) {
                AutomationState.log("  ⚠ Panel closed after tap — reopening")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                if (!StepUtils.waitForPackage(service, "systemui", 2000)) {
                    AutomationState.log("  ✘ QS reopen fail")
                    return false
                }
                StepUtils.sleepMs(500)

                // Reopen کے بعد tile دوبارہ تلاش کریں (coords بدل سکتے ہیں)
                val newTile = findAirplaneTile(service)
                if (newTile != null) {
                    val nr = Rect(); newTile.getBoundsInScreen(nr)
                    val density = service.resources.displayMetrics.density
                    val offsetPx = (12 * density).toInt()
                    tapX = nr.centerX()
                    tapY = nr.centerY() - offsetPx
                    if (tapY < nr.top + 4) tapY = nr.centerY()
                    AutomationState.log("  ↳ New tap coords: ($tapX, $tapY)")
                }
            } else {
                StepUtils.sleepMs(400)
            }
        }

        AutomationState.log("  ✘ All 4 attempts failed — clearing saved coords")
        prefs.flightTapX = 0
        prefs.flightTapY = 0
        return false
    }

    private fun openQs(service: AccessibilityService): Boolean {
        if (isQsOpen(service)) return true
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        return StepUtils.waitForPackage(service, "systemui", 2500)
    }

    private fun isQsOpen(service: AccessibilityService): Boolean =
        StepUtils.isPackageForeground(service, "systemui")

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
        } catch (e: Exception) {
            false
        }
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
     * 🟢 Icon recognition — contentDescription اور viewId میں "airplane" ڈھونڈیں
     */
    private fun findAirplaneTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var bestMatch: AccessibilityNodeInfo? = null
        var bestScore = Int.MAX_VALUE

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) {
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
                continue
            }

            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""

            val matchesAirplane =
                matchesKeyword(desc) || matchesKeyword(text) || matchesKeyword(id)

            if (matchesAirplane) {
                // Score: کم = بہتر
                var score = 10
                if (desc.contains("airplane mode") || text.contains("airplane mode")) score = 0
                else if (desc.contains("airplane") || text.contains("airplane")) score = 2
                else if (desc.contains("flight") || text.contains("flight")) score = 4
                else score = 6

                if (score < bestScore) {
                    bestScore = score
                    bestMatch = node
                    AutomationState.log("  ℹ Candidate: desc='${desc.take(25)}' text='${text.take(20)}' score=$score")
                }
            }

            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }

        if (bestMatch != null) {
            AutomationState.log("  ✔ Picked airplane tile (score=$bestScore)")
        }
        return bestMatch
    }

    private fun matchesKeyword(input: String): Boolean {
        if (input.isEmpty()) return false
        for (kw in AIRPLANE_KEYWORDS) {
            if (input.contains(kw)) return true
        }
        return false
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
