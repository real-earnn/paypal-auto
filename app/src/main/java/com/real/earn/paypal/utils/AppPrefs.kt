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

    companion object {
        private const val KEY_AUTO_FLIGHT = "auto_flight_mode"
        private const val KEY_FLIGHT_POS = "flight_position"
    }
}
