package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.real.earn.paypal.service.AutomationState

object PayPalClearData {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    /**
     * App Info → Storage → Clear → (popup?) → HOME.
     * Aggressive: har step ko 2-3s tak poll karta hai, milta hi click.
     * Na mile to reason log karke abort.
     */
    fun strictClear(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("🧹 Clear data…")

            // Open app info
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$PAYPAL_PKG")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)

            // Wait app info (Settings foreground)
            if (!StepUtils.waitForPackage(service, "settings", 2500) &&
                !StepUtils.waitForPackage(service, "android", 1200)) {
                AutomationState.log("✘ App Info not opened")
                exitHome(service); return false
            }

            // Tap Storage
            if (!tapWhen(service, 3000,
                    "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                AutomationState.log("✘ Storage row not found")
                exitHome(service); return false
            }

            // Tap Clear storage
            if (!tapWhen(service, 2500,
                    "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                AutomationState.log("✘ Clear row not found")
                exitHome(service); return false
            }

            // Popup optional — if visible within 1.5s tap confirm; else assume done
            if (findWithin(service, 1500, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                tapWhen(service, 1000, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                AutomationState.log("ℹ Confirm popup handled")
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

    /** Poll aggressively; as soon as a matching text appears → click. Returns false on timeout. */
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
            repeat(2) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(250)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(400)
        } catch (_: Exception) {}
    }
}
