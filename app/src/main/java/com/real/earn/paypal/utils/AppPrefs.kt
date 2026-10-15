package com.real.earn.paypal.utils

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("real_earn_app_prefs", Context.MODE_PRIVATE)

    var autoFlightMode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FLIGHT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_FLIGHT, v).apply()

    var flightPosition: Int
        get() = prefs.getInt(KEY_FLIGHT_POS, 0)
        set(v) = prefs.edit().putInt(KEY_FLIGHT_POS, v).apply()

    // 🟢 Captcha auto alarm toggle
    var captchaAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTCHA_ALARM, true)
        set(v) = prefs.edit().putBoolean(KEY_CAPTCHA_ALARM, v).apply()

    // 🟢 Selected alarm sound URI (null = use default system alarm)
    var captchaAlarmUri: String?
        get() = prefs.getString(KEY_CAPTCHA_URI, null)
        set(v) = prefs.edit().putString(KEY_CAPTCHA_URI, v).apply()

    companion object {
        private const val KEY_AUTO_FLIGHT = "auto_flight_mode"
        private const val KEY_FLIGHT_POS = "flight_position"
        private const val KEY_CAPTCHA_ALARM = "captcha_alarm_enabled"
        private const val KEY_CAPTCHA_URI = "captcha_alarm_uri"
    }
}
