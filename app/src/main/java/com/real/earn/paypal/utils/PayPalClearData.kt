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

            // ① Go HOME first and settle
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(1200)

            // ② Open App Info (with retry inside a loop — actually try twice)
            var appInfoOpened = false
            repeat(3) { attempt ->
                if (StepUtils.shouldStop()) return false
                if (attempt > 0) {
                    AutomationState.log("ℹ App Info retry #$attempt")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    StepUtils.sleepMs(1500)
                }
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$PAYPAL_PKG")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { service.startActivity(intent) } catch (_: Exception) {}

                if (StepUtils.waitForPackage(service, "settings", 3000) ||
                    StepUtils.waitForPackage(service, "android", 1500)) {
                    // Verify we're actually on App Info page — check for "Storage" or package name
                    StepUtils.sleepMs(1000)
                    val root = service.rootInActiveWindow
                    if (root != null &&
                        (NodeFinder.containsAny(root, "Storage", "Force stop", "Uninstall", "App info") ||
                         NodeFinder.containsAny(root, PAYPAL_PKG))
                    ) {
                        appInfoOpened = true
                    }
                }
                if (appInfoOpened) return@repeat
            }

            if (!appInfoOpened) {
                AutomationState.log("✘ App Info not opened after retries")
                exitHome(service); return false
            }
            AutomationState.log("✔ App Info open")
            StepUtils.sleepMs(800)

            // ③ Tap Storage (retry)
            if (!StepUtils.retryStep("Storage row",
                    { tapWhen(service, 2500,
                        "Storage & cache", "Storage and cache", "Storage", "Memory") })) {
                AutomationState.log("✘ Storage row not found")
                exitHome(service); return false
            }
            AutomationState.log("✔ Storage tapped")
            StepUtils.sleepMs(1000)

            // ④ Tap Clear storage (retry)
            if (!StepUtils.retryStep("Clear row",
                    { tapWhen(service, 2500,
                        "Clear storage", "Clear data", "Clear all data", "Delete data") })) {
                AutomationState.log("✘ Clear row not found")
                exitHome(service); return false
            }
            AutomationState.log("✔ Clear tapped")
            StepUtils.sleepMs(1000)

            // ⑤ Confirm popup (optional)
            if (findWithin(service, 1500, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                tapWhen(service, 1000, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                AutomationState.log("ℹ Confirm popup handled")
                StepUtils.sleepMs(1500)
            } else {
                AutomationState.log("ℹ No popup — already cleared")
            }

            exitHome(service)
            AutomationState.log("✔ Clear data done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Clear error: ${e.message}")
            exitHome(service)
            false
        }
    }

    private fun tapWhen(service: AccessibilityService, timeoutMs: Long, vararg texts: String): Boolean {
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
            repeat(3) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(400)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(800)
        } catch (_: Exception) {}
    }
}
