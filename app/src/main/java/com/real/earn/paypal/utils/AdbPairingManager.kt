package com.real.earn.paypal.utils

import android.content.Context
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.KadbInitializer
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🟢 Direct ADB client (Kadb) — pairing + shell.
 *
 * No external app (no Shizuku). User pairs once via Wireless Debugging,
 * then we run shell commands like `cmd connectivity airplane-mode enable`
 * and `pm clear <package>` directly.
 */
object AdbPairingManager {

    private const val CONNECT_TIMEOUT_MS = 3000
    private const val MDNS_TIMEOUT_MS = 10_000L

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

    /** Wireless debugging pairing needs Android 11+ (API 30). */
    fun isPairingSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Pair with a device using the 6-digit code from
     * Settings → Developer options → Wireless debugging → Pair device with pairing code.
     *
     * @return Pair(success, message)
     */
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
     * After pairing, the connect port is different from the pairing port.
     * Discover it via mDNS (_adb-tls-connect._tcp).
     */
    suspend fun discoverConnectPort(
        context: Context,
        host: String,
        timeoutMs: Long = MDNS_TIMEOUT_MS
    ): Int? {
        initialize(context)
        return withContext(Dispatchers.IO) {
            var mdns: KadbMdnsAndroid? = null
            try {
                mdns = KadbMdnsAndroid(context)
                mdns.start()
                val port = withTimeoutOrNull(timeoutMs) {
                    mdns.state.first { state ->
                        state.connectDevices.any { it.host == host }
                    }.connectDevices.firstOrNull { it.host == host }?.port
                }
                if (port != null) {
                    AutomationState.log("✔ mDNS found connect port $port")
                } else {
                    AutomationState.log("⚠ mDNS could not find connect port")
                }
                port
            } catch (e: Exception) {
                AutomationState.log("✘ mDNS discovery failed: ${e.message}")
                null
            } finally {
                try { mdns?.stop() } catch (_: Exception) {}
                try { mdns?.close() } catch (_: Exception) {}
            }
        }
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

    /**
     * Quick connectivity check: create a session and run `echo ok`.
     * Returns false if the port changed after reboot or device is unreachable.
     */
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

    /**
     * Execute a shell command on the paired device.
     * Returns null if ADB is not paired / not connected (caller should fallback).
     */
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

    /** Try to rediscover the connect port (e.g. after reboot). */
    suspend fun refreshConnectPort(context: Context): Boolean {
        val prefs = AppPrefs(context)
        val host = prefs.adbHost ?: return false
        val newPort = discoverConnectPort(context, host) ?: return false
        prefs.adbConnectPort = newPort
        AutomationState.log("🔁 ADB connect port refreshed: $newPort")
        return true
    }

    data class ShellOutcome(val exitCode: Int, val output: String)
}
