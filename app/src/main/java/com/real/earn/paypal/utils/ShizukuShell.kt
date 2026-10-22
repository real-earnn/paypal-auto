package com.real.earn.paypal.utils

import android.content.Context
import android.content.pm.PackageManager
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 🟢 Shizuku Shell — ADB pairing کی جگہ
 *
 * User صرف Shizuku app install کرتا ہے اور wireless debugging سے ایک بار activate کرتا ہے،
 * پھر ہم اس کے ذریعے shell commands چلا سکتے ہیں (Android 11+).
 */
object ShizukuShell {

    private const val REQUEST_CODE = 1001

    /** کیا Shizuku app فون پر انسٹال ہے اور service چل رہی ہے؟ */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    /** کیا ہمیں ADB shell کی اجازت مل چکی ہے؟ */
    fun hasPermission(): Boolean = try {
        if (!isAvailable()) return false
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /** Shizuku سے permission مانگیں (یہ system dialog دکھاتا ہے) */
    fun requestPermission() {
        try {
            if (!isAvailable()) return
            if (hasPermission()) return
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            AutomationState.log("⚠ Shizuku permission request failed: ${e.message}")
        }
    }

    /**
     * Shell command چلائیں۔
     * @return Pair(exitCode, output) — null اگر Shizuku available نہیں ہے
     */
    suspend fun executeShell(command: String): ShellResult? = withContext(Dispatchers.IO) {
        if (!isAvailable() || !hasPermission()) return@withContext null
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                AutomationState.log("⚠ Shell exit $exitCode: $command → $stderr")
            }
            ShellResult(exitCode, stdout.trim())
        } catch (e: Exception) {
            AutomationState.log("✘ Shell error: ${e.message}")
            null
        }
    }

    /**
     * ایک ساتھ multiple commands (fast — single shell session)
     */
    suspend fun executeShellMulti(commands: List<String>): List<ShellResult> {
        return commands.map { cmd -> executeShell(cmd) ?: ShellResult(-1, "") }
    }

    // ── Convenience wrappers ────────────────────────────────

    /** Flight mode on/off */
    suspend fun setAirplaneMode(enable: Boolean): Boolean {
        val cmd = if (enable)
            "cmd connectivity airplane-mode enable"
        else
            "cmd connectivity airplane-mode disable"
        val primary = executeShell(cmd)
        if (primary != null && primary.exitCode == 0) return true

        // Fallback (older APIs)
        val fallback = if (enable)
            "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
        else
            "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false"
        val fb = executeShell(fallback)
        return fb?.exitCode == 0
    }

    /** Clear app data */
    suspend fun clearAppData(packageName: String): Boolean {
        val r = executeShell("pm clear $packageName")
        return r?.exitCode == 0
    }

    /** Force stop app */
    suspend fun forceStopApp(packageName: String): Boolean {
        val r = executeShell("am force-stop $packageName")
        return r?.exitCode == 0
    }

    /** Launch app via monkey */
    suspend fun launchApp(packageName: String): Boolean {
        val r = executeShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        return r?.exitCode == 0
    }

    data class ShellResult(val exitCode: Int, val output: String)
}
