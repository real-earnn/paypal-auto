package com.real.earn.paypal.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkSharer {

    fun shareApkToWorkProfile(context: Context, packageName: String, workProfileUserHandle: android.os.UserHandle) {
        try {
            // 1. APK کا راستہ حاصل کریں
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val sourceApkPath = appInfo.sourceDir
            val sourceApkFile = File(sourceApkPath)

            // 2. اسے کیش ڈائریکٹری میں کاپی کریں
            val cacheDir = File(context.cacheDir, "shared_apks")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val sharedApkFile = File(cacheDir, "${packageName}.apk")
            
            FileInputStream(sourceApkFile).use { input ->
                FileOutputStream(sharedApkFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 3. FileProvider URI بنائیں
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedApkFile
            )

            // 4. ایک انٹینٹ بنائیں جو ورک پروفائل کی ایپ کو بھیجا جائے گا
            val intent = Intent("com.real.earn.paypal.ACTION_INSTALL_APK").apply {
                putExtra("apk_uri", apkUri.toString())
                putExtra("package_name", packageName)
                // 🔴 اہم: ورک پروفائل کے پیکیج کو ہدف بنائیں
                setPackage(context.packageName)
            }

            // 5. CrossProfileApps کے ذریعے انٹینٹ ورک پروفائل کو بھیجیں
            val crossProfileApps = context.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE) as android.content.pm.CrossProfileApps
            crossProfileApps.startActivity(
                workProfileUserHandle,
                intent,
                null // Activity options
            )

        } catch (e: Exception) {
            e.printStackTrace()
            // یوزر کو دکھائیں کہ APK شیئر کرنے میں مسئلہ ہوا
        }
    }
}
