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

    private fun requestCode(input: String) {
        setLoading(true)

        val body = if (input.all { it.isDigit() }) {
            RequestCodeBody(telegramId = input.toLongOrNull(), username = null)
        } else {
            RequestCodeBody(telegramId = null, username = input)
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.service(authManager).requestCode(body)

                if (response.isSuccessful) {
                    val data = response.body()
                    if (data?.success == true && data.userId > 0) {
                        // Move to OTP screen
                        val intent = Intent(this@LoginActivity, CodeVerifyActivity::class.java)
                        intent.putExtra("user_id", data.userId)
                        intent.putExtra("identifier", input)
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
