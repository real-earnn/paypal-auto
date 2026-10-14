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

            // HOME first — let system settle
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(1500)

            // ═══ Open App Info — with proper wait ═══
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$PAYPAL_PKG")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try { service.startActivity(intent) } catch (_: Exception) {}

            // Wait for Settings package
            if (!StepUtils.waitForPackage(service, "settings", 5000) &&
                !StepUtils.waitForPackage(service, "android", 2000)) {
                AutomationState.log("✘ App Info not opened")
                exitHome(service); return false
            }

            // Wait for "Storage" text
            if (!waitFor(service, 4000, "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                AutomationState.log("✘ Storage row not found")
                exitHome(service); return false
            }

            // Tap Storage
            if (!tap(service, "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                AutomationState.log("✘ Storage tap fail")
                exitHome(service); return false
            }
            StepUtils.sleepMs(1000)

            // Wait for "Clear storage"
            if (!waitFor(service, 3000, "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                AutomationState.log("✘ Clear row not found")
                exitHome(service); return false
            }

            // Tap Clear
            if (!tap(service, "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                AutomationState.log("✘ Clear tap fail")
                exitHome(service); return false
            }
            StepUtils.sleepMs(600)

            // Optional confirm popup
            if (waitFor(service, 1500, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                tap(service, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                AutomationState.log("ℹ Confirm popup handled")
                StepUtils.sleepMs(1500)
            } else {
                AutomationState.log("ℹ No popup — already cleared")
            }

            // ═══ Quick 2x BACK → HOME ═══
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(300)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(400)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(500)

            AutomationState.log("✔ Clear data done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Clear error: ${e.message}")
            exitHome(service)
            false
        }
    }

    private fun waitFor(service: AccessibilityService, timeoutMs: Long, vararg texts: String): Boolean {
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

    private fun tap(service: AccessibilityService, vararg texts: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, *texts)
            ?: NodeFinder.findByTextContains(root, *texts)
            ?: return false
        return NodeFinder.click(node)
    }

    private fun exitHome(service: AccessibilityService) {
        try {
            repeat(3) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(300)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(600)
        } catch (_: Exception) {}
    }
}
