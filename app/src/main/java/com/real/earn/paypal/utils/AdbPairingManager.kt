package com.real.earn.paypal.utils

import android.content.Context
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.KadbInitializer
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdbPairingManager {

    private const val CONNECT_TIMEOUT_MS = 3000

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        try {
            KadbInitializer.initialize(context.applicationContext)
            initialized = true
            AutomationState.log("🔌 ADB (Kadb) initialized")
        } catch (e: Exception) {
            AutomationState.log("✘ ADB init failed: ${e.message}")
        }
    }

    fun isPairingSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    suspend fun pair(
        context: Context,
        host: String,
        pairingPort: Int,
        code: String
    ): Pair<Boolean, String> {
        if (!isPairingSupported()) {
            return false to "Wireless debugging pairing requires Android 11+"
        }
        initialize(context)
        return withContext(Dispatchers.IO) {
            try {
                AutomationState.log("🔗 Pairing $host:$pairingPort ...")
                Kadb.pair(host, pairingPort, code)
                AutomationState.log("✔ Pairing successful")
                true to "Pairing successful"
            } catch (e: Exception) {
                AutomationState.log("✘ Pairing failed: ${e.message}")
                false to (e.message ?: "Pairing failed")
            }
        }
    }

    /**
     * mDNS کے بغیر connect port خودکار طور پر دریافت نہیں ہو سکتا，
     * اس لیے یہاں صرف null واپس ہوتا ہے۔
     * PermissionActivity میں user سے manual port لیا جائے گا۔
     */
    suspend fun discoverConnectPort(
        context: Context,
        host: String,
        timeoutMs: Long = 0L
    ): Int? {
        // mDNS module ہٹا دیا گیا ہے — manual port استعمال کریں
        AutomationState.log("⚠ mDNS unavailable — connect port manually enter karein")
        return null
    }

    fun savePairing(context: Context, host: String, pairingPort: Int, connectPort: Int) {
        val prefs = AppPrefs(context)
        prefs.adbHost = host
        prefs.adbPairingPort = pairingPort
        prefs.adbConnectPort = connectPort
        prefs.adbPaired = true
        AutomationState.log("💾 ADB target saved: $host:$connectPort")
    }

    fun clearPairing(context: Context) {
        val prefs = AppPrefs(context)
        prefs.adbPaired = false
        prefs.adbHost = null
        prefs.adbConnectPort = 0
        AutomationState.log("🗑 ADB pairing cleared")
    }

    suspend fun isConnected(context: Context): Boolean {
        val prefs = AppPrefs(context)
        if (!prefs.adbPaired) return false
        val host = prefs.adbHost ?: return false
        val port = prefs.adbConnectPort
        if (port <= 0) return false
        initialize(context)
        return withContext(Dispatchers.IO) {
            try {
                Kadb.create(host, port, connectTimeout = CONNECT_TIMEOUT_MS).use { kadb ->
                    val res = kadb.shell("echo ok")
                    res.exitCode == 0 && res.output.trim() == "ok"
                }
            } catch (e: Exception) {
                AutomationState.log("⚠ ADB check failed: ${e.message}")
                false
            }
        }
    }

    suspend fun executeShell(context: Context, command: String): ShellOutcome? {
        val prefs = AppPrefs(context)
        if (!prefs.adbPaired) return null
        val host = prefs.adbHost ?: return null
        val port = prefs.adbConnectPort
        if (port <= 0) return null
        initialize(context)
        return withContext(Dispatchers.IO) {
            try {
                Kadb.create(host, port, connectTimeout = CONNECT_TIMEOUT_MS).use { kadb ->
                    val res = kadb.shell(command)
                    ShellOutcome(exitCode = res.exitCode, output = res.output)
                }
            } catch (e: Exception) {
                AutomationState.log("⚠ ADB shell error: ${e.message}")
                null
            }
        }
    }

    data class ShellOutcome(val exitCode: Int, val output: String)
}
