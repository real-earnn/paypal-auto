package com.real.earn.paypal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.real.earn.paypal.utils.AdbPairingManager

class App : Application() {

    companion object {
        const val CHANNEL_AUTOMATION = "real_earn_automation"
        const val CHANNEL_ALERTS = "real_earn_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // 🟢 Kadb init — pairing/shell ke liye zaroori
        AdbPairingManager.initialize(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val automationChannel = NotificationChannel(
                CHANNEL_AUTOMATION,
                "Automation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PayPal automation progress"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Important app alerts"
            }

            nm.createNotificationChannel(automationChannel)
            nm.createNotificationChannel(alertsChannel)
        }
    }
}
