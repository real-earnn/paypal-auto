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
        try {
            AutomationState.log("🧹 Clear data…")

            // HOME first, let screen settle
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(900)

            // ═══ Up to 3 attempts to reach App Info ═══
            for (attempt in 1..3) {
                if (StepUtils.shouldStop()) return false

                if (attempt > 1) {
                    AutomationState.log("ℹ App Info retry #$attempt")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    StepUtils.sleepMs(1000)
                }

                // Fire intent
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
                } catch (_: Exception) {}

                // Wait for Settings package
                if (!StepUtils.waitForPackage(service, "settings", 3500) &&
                    !StepUtils.waitForPackage(service, "android", 1500)) {
                    AutomationState.log("  ⚠ Settings not foreground")
                    continue
                }

                // Let content render
                StepUtils.sleepMs(1300)

                // Verify App Info content visible
                val root = service.rootInActiveWindow
                if (root == null) {
                    AutomationState.log("  ⚠ root null")
                    continue
                }

                val hasAppInfoContent = NodeFinder.containsAny(
                    root,
                    "Storage", "Storage & cache", "Memory",
                    "Force stop", "Uninstall", "App info"
                )
                if (!hasAppInfoContent) {
                    AutomationState.log("  ⚠ App Info content not ready")
                    continue
                }

                AutomationState.log("✔ App Info ready (attempt $attempt)")
                StepUtils.sleepMs(500)

                // ── Tap Storage ──
                if (!tapWhen(service, 3000,
                        "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                    AutomationState.log("  ⚠ Storage row not found")
                    continue
                }
                StepUtils.sleepMs(1000)

                // ── Tap Clear data ──
                if (!tapWhen(service, 3000,
                        "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                    AutomationState.log("  ⚠ Clear row not found")
                    continue
                }
                StepUtils.sleepMs(800)

                // ── Confirm popup (optional) ──
                if (findWithin(service, 1800, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                    tapWhen(service, 1200, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                    AutomationState.log("  ℹ Confirm popup handled")
                    StepUtils.sleepMs(1500)
                } else {
                    AutomationState.log("  ℹ No popup — already cleared")
                }

                exitHome(service)
                AutomationState.log("✔ Clear data done")
                return true
            }

            AutomationState.log("✘ Clear data failed after 3 attempts")
            exitHome(service)
            return false
        } catch (e: Exception) {
            AutomationState.log("✘ Clear error: ${e.message}")
            exitHome(service)
            return false
        }
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
                if (node != null && NodeFinder.click(node)) return true
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
