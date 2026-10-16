package com.real.earn.paypal.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveUserId(userId: Long) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, 0L)

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
    }

    fun getUserName(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    // 🟢 Client board ID (0 = main board)
    fun saveClientId(clientId: Long) {
        prefs.edit().putLong(KEY_CLIENT_ID, clientId).apply()
    }

    fun getClientId(): Long = prefs.getLong(KEY_CLIENT_ID, 0L)

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "real_earn_secure_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "user_name"
        private const val KEY_CLIENT_ID = "client_id"  // 🟢 NEW
    }
}
