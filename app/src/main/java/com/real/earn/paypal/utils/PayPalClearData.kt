package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.real.earn.paypal.service.AutomationState

object PayPalClearData {

    private const val TAG = "ClearData"
    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    fun clear(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("🧹 Clearing PayPal data...")

            // 1. Open app details
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$PAYPAL_PKG")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)
            Thread.sleep(2000)

            // 2. Tap "Storage & cache"
            tapByTexts(service, "Storage & cache", "Storage and cache", "Storage", "Memory")
            Thread.sleep(1500)

            // 3. Tap "Clear storage" / "Clear data"
            tapByTexts(service, "Clear storage", "Clear data", "Clear all data", "Delete data")
            Thread.sleep(1500)

            // 4. Confirm dialog
            tapByTexts(service, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
            Thread.sleep(1800)

            // 🟢 5. Kill Settings from background — press BACK multiple times
            exitSettingsCompletely(service)

            AutomationState.log("✔ Data cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "clear failed: ${e.message}")
            AutomationState.log("✘ Clear data error: ${e.message}")
            false
        }
    }

    /**
     * Exit Settings completely so next time it opens fresh at App Info.
     * Without this, Settings stays in background and "Clear data" button
     * may not appear correctly.
     */
    private fun exitSettingsCompletely(service: AccessibilityService) {
        try {
            // Press BACK 4 times to unwind: storage-confirm → storage → app-info → settings-main
            repeat(4) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                Thread.sleep(500)
            }
            // Then go Home
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Thread.sleep(700)
            AutomationState.log("ℹ Settings exited from background")
        } catch (e: Exception) {
            Log.e(TAG, "exitSettings failed: ${e.message}")
        }
    }

    private fun tapByTexts(service: AccessibilityService, vararg texts: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        NodeFinder.findByText(root, *texts)?.let {
            if (NodeFinder.click(it)) return true
        }
        NodeFinder.findByTextContains(root, *texts)?.let {
            if (NodeFinder.click(it)) return true
        }
        return false
    }
}
