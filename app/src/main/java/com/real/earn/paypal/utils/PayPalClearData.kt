package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.real.earn.paypal.service.AutomationState

object PayPalClearData {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    fun strictClear(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("🧹 Clear data…")

            // ① Force HOME first — clear any stale Settings/PayPal
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(1000)

            // ② Try up to 4 times to open App Info cleanly
            var appInfoReady = false
            for (attempt in 1..4) {
                if (StepUtils.shouldStop()) return false

                AutomationState.log("ℹ App Info open attempt #$attempt")

                // Force a clean open
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$PAYPAL_PKG")
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }
                    service.startActivity(intent)
                } catch (e: Exception) {
                    AutomationState.log("⚠ startActivity err: ${e.message}")
                }

                // Wait for real App Info signal (not just Settings package)
                // Look for "Storage" / "Force stop" / "Uninstall" — those are unique to App Info
                if (waitFor(service, 4000,
                        "Storage", "Storage & cache", "Memory",
                        "Force stop", "Uninstall", "App info")) {
                    appInfoReady = true
                    AutomationState.log("✔ App Info ready")
                    break
                }

                // Not ready — HOME and retry
                AutomationState.log("⚠ App Info not fully loaded — retrying")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.sleepMs(1200)
            }

            if (!appInfoReady) {
                AutomationState.log("✘ App Info failed after 4 attempts")
                exitHome(service)
                return false
            }

            StepUtils.sleepMs(600)

            // ③ Tap Storage (retry 3x)
            if (!StepUtils.retryStep("Storage row") {
                    tapWhen(service, 3000,
                        "Storage & cache", "Storage and cache", "Storage", "Memory")
                }) {
                AutomationState.log("✘ Storage row not found")
                exitHome(service); return false
            }
            AutomationState.log("✔ Storage tapped")
            StepUtils.sleepMs(1000)

            // ④ Tap Clear storage (retry 3x)
            if (!StepUtils.retryStep("Clear row") {
                    tapWhen(service, 3000,
                        "Clear storage", "Clear data", "Clear all data", "Delete data")
                }) {
                AutomationState.log("✘ Clear row not found")
                exitHome(service); return false
            }
            AutomationState.log("✔ Clear tapped")
            StepUtils.sleepMs(1000)

            // ⑤ Confirm popup (optional)
            if (findWithin(service, 1800, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                tapWhen(service, 1200, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                AutomationState.log("ℹ Confirm popup handled")
                StepUtils.sleepMs(1500)
            } else {
                AutomationState.log("ℹ No popup — already cleared")
            }

            // ⑥ Exit + Home
            exitHome(service)
            AutomationState.log("✔ Clear data done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Clear error: ${e.message}")
            exitHome(service)
            false
        }
    }

    private fun waitFor(
        service: AccessibilityService,
        timeoutMs: Long,
        vararg texts: String
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString() ?: ""
                // Make sure we're in Settings-like package
                if (pkg.contains("settings") || pkg.contains("android") || pkg.contains("com.android")) {
                    val node = NodeFinder.findByText(root, *texts)
                        ?: NodeFinder.findByTextContains(root, *texts)
                    if (node != null) return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    private fun tapWhen(
        service: AccessibilityService,
        timeoutMs: Long,
        vararg texts: String
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, *texts)
                    ?: NodeFinder.findByTextContains(root, *texts)
                if (node != null) {
                    if (NodeFinder.click(node)) return true
                }
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    private fun findWithin(service: AccessibilityService, timeoutMs: Long, vararg texts: String): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, *texts)
                    ?: NodeFinder.findByTextContains(root, *texts)
                if (node != null) return true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    private fun exitHome(service: AccessibilityService) {
        try {
            repeat(2) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(300)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(700)
        } catch (_: Exception) {}
    }
}
