package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.api.RequestCodeBody
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.btnContinue.setOnClickListener {
            val input = binding.etTelegramInput.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, "Telegram ID ya username likho", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestCode(input)
        }
    }

    /**
     * 🟢 Input parsing:
     *   "123456789"              → main board (telegramId)
     *   "378289348-921984747"    → client board (ownerId-telegramId)
     *   "@username"              → main board (username)
     */
    private fun requestCode(input: String) {
        setLoading(true)

        // 🟢 Detect "OWNERID-USERID" pattern (both sides numeric)
        val dashParts = input.split("-")
        val isClientFormat = dashParts.size == 2 &&
                dashParts[0].all { it.isDigit() } &&
                dashParts[1].all { it.isDigit() } &&
                dashParts[0].isNotEmpty() && dashParts[1].isNotEmpty()

        val body: RequestCodeBody = if (isClientFormat) {
            // Client mode
            RequestCodeBody(
                telegramId = dashParts[1].toLongOrNull(),
                username = null,
                ownerId = dashParts[0].toLongOrNull()
            )
        } else if (input.all { it.isDigit() }) {
            // Main board — telegram ID
            RequestCodeBody(
                telegramId = input.toLongOrNull(),
                username = null,
                ownerId = null
            )
        } else {
            // Main board — username
            RequestCodeBody(
                telegramId = null,
                username = input,
                ownerId = null
            )
        }

        // Identifier jise CodeVerifyActivity ko pass karenge (resend ke liye)
        val identifierForNext = if (isClientFormat) dashParts[1] else input

        lifecycleScope.launch {
            try {
                val response = ApiClient.service(authManager).requestCode(body)

                if (response.isSuccessful) {
                    val data = response.body()
                    if (data?.success == true && data.userId > 0) {
                        // 🟢 Server ne client_id diya (0 = main, else client bot id)
                        val returnedClientId = data.clientId

                        val intent = Intent(this@LoginActivity, CodeVerifyActivity::class.java)
                        intent.putExtra("user_id", data.userId)
                        intent.putExtra("identifier", identifierForNext)
                        intent.putExtra("client_id", returnedClientId)  // 🟢 NEW
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            data?.message ?: "Code bhejne mein masla hua",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else if (response.code() == 404) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Account nahi mila. Pehle Telegram bot pe /start karo.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val err = response.errorBody()?.string() ?: "Server error"
                    Toast.makeText(this@LoginActivity, err, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Network error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnContinue.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (loading) "Bhej raha hun..." else "Continue"
    }
}
