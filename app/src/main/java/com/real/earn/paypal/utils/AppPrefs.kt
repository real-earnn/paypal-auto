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

    var flightTapX: Int
        get() = prefs.getInt(KEY_FLIGHT_X, 0)
        set(v) = prefs.edit().putInt(KEY_FLIGHT_X, v).apply()

    var flightTapY: Int
        get() = prefs.getInt(KEY_FLIGHT_Y, 0)
        set(v) = prefs.edit().putInt(KEY_FLIGHT_Y, v).apply()

    var captchaAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTCHA_ALARM, true)
        set(v) = prefs.edit().putBoolean(KEY_CAPTCHA_ALARM, v).apply()

    var captchaAlarmUri: String?
        get() = prefs.getString(KEY_CAPTCHA_URI, null)
        set(v) = prefs.edit().putString(KEY_CAPTCHA_URI, v).apply()

    // ══════════════════════════════════════════════════════════════════
    // 🟢 ADB pairing state (host + connect port + paired flag)
    // ══════════════════════════════════════════════════════════════════

    var adbPaired: Boolean
        get() = prefs.getBoolean(KEY_ADB_PAIRED, false)
        set(v) = prefs.edit().putBoolean(KEY_ADB_PAIRED, v).apply()

    var adbHost: String?
        get() = prefs.getString(KEY_ADB_HOST, null)
        set(v) = prefs.edit().putString(KEY_ADB_HOST, v).apply()

    var adbPairingPort: Int
        get() = prefs.getInt(KEY_ADB_PAIRING_PORT, 0)
        set(v) = prefs.edit().putInt(KEY_ADB_PAIRING_PORT, v).apply()

    var adbConnectPort: Int
        get() = prefs.getInt(KEY_ADB_CONNECT_PORT, 0)
        set(v) = prefs.edit().putInt(KEY_ADB_CONNECT_PORT, v).apply()

    companion object {
        private const val KEY_AUTO_FLIGHT = "auto_flight_mode"
        private const val KEY_FLIGHT_POS = "flight_position"
        private const val KEY_FLIGHT_X = "flight_tap_x"
        private const val KEY_FLIGHT_Y = "flight_tap_y"
        private const val KEY_CAPTCHA_ALARM = "captcha_alarm_enabled"
        private const val KEY_CAPTCHA_URI = "captcha_alarm_uri"

        private const val KEY_ADB_PAIRED = "adb_paired"
        private const val KEY_ADB_HOST = "adb_host"
        private const val KEY_ADB_PAIRING_PORT = "adb_pairing_port"
        private const val KEY_ADB_CONNECT_PORT = "adb_connect_port"
    }
}
