package com.real.earn.paypal.utils

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("real_earn_app_prefs", Context.MODE_PRIVATE)

    var autoFlightMode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FLIGHT, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_FLIGHT, v).apply()

    // 🟢 Flight mode extra coords (fallback)
    var flightTapX: Int
        get() = prefs.getInt(KEY_FLIGHT_X, 0)
        set(v) = prefs.edit().putInt(KEY_FLIGHT_X, v).apply()

    var flightTapY: Int
        get() = prefs.getInt(KEY_FLIGHT_Y, 0)
        set(v) = prefs.edit().putInt(KEY_FLIGHT_Y, v).apply()

    // 🟢 Shizuku mode toggle
    var shizukuModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHIZUKU_MODE, true)
        set(v) = prefs.edit().putBoolean(KEY_SHIZUKU_MODE, v).apply()

    // 🟢 Captcha alarm
    var captchaAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTCHA_ALARM, true)
        set(v) = prefs.edit().putBoolean(KEY_CAPTCHA_ALARM, v).apply()

    var captchaAlarmUri: String?
        get() = prefs.getString(KEY_CAPTCHA_URI, null)
        set(v) = prefs.edit().putString(KEY_CAPTCHA_URI, v).apply()

    // 🟢 Flight mode cycles (default 5)
    var flightCycles: Int
        get() = prefs.getInt(KEY_FLIGHT_CYCLES, 5)
        set(v) = prefs.edit().putInt(KEY_FLIGHT_CYCLES, v).apply()

    companion object {
        private const val KEY_AUTO_FLIGHT = "auto_flight_mode"
        private const val KEY_FLIGHT_X = "flight_tap_x"
        private const val KEY_FLIGHT_Y = "flight_tap_y"
        private const val KEY_SHIZUKU_MODE = "shizuku_mode_enabled"
        private const val KEY_CAPTCHA_ALARM = "captcha_alarm_enabled"
        private const val KEY_CAPTCHA_URI = "captcha_alarm_uri"
        private const val KEY_FLIGHT_CYCLES = "flight_cycles"
    }
}
