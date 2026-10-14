package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.api.RequestCodeBody
import com.real.earn.paypal.api.VerifyCodeBody
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityCodeVerifyBinding
import kotlinx.coroutines.launch

class CodeVerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodeVerifyBinding
    private lateinit var authManager: AuthManager
    private var userId: Long = 0L
    private var identifier: String = ""

    private val otpBoxes by lazy {
        listOf(
            binding.otp1, binding.otp2, binding.otp3,
            binding.otp4, binding.otp5, binding.otp6
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        userId = intent.getLongExtra("user_id", 0L)
        identifier = intent.getStringExtra("identifier") ?: ""

        setupOtpBoxes()

        binding.btnVerify.setOnClickListener { verifyCode() }
        binding.btnResend.setOnClickListener { resendCode() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        // Move next
                        if (index < otpBoxes.lastIndex) {
                            otpBoxes[index + 1].requestFocus()
                        } else {
                            box.clearFocus()
                        }
                    } else {
                        // Move back on delete
                        if (index > 0) {
                            otpBoxes[index - 1].requestFocus()
                        }
                    }
                }
            })

            box.setOnKeyListener { v, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL
                    && event.action == android.view.KeyEvent.ACTION_DOWN
                    && (v as EditText).text.isEmpty()
                    && index > 0
                ) {
                    otpBoxes[index - 1].requestFocus()
                    true
                } else false
            }
        }

        otpBoxes.first().requestFocus()
    }

    private fun getEnteredCode(): String =
        otpBoxes.joinToString("") { it.text.toString() }

    private fun verifyCode() {
        val code = getEnteredCode()
        if (code.length != 6) {
            Toast.makeText(this, "6-digit code likho", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.service(authManager)
                    .verifyCode(VerifyCodeBody(userId = userId, code = code))

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!
                    val token = data.token ?: ""

                    authManager.saveToken(token)
                    authManager.saveUserId(data.user?.id ?: userId)
                    authManager.saveUserName(data.user?.firstName ?: "")

                    Toast.makeText(this@CodeVerifyActivity, "Login ho gaya!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@CodeVerifyActivity, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val msg = response.body()?.message
                        ?: response.body()?.error
                        ?: "Ghalat code"
                    Toast.makeText(this@CodeVerifyActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@CodeVerifyActivity,
                    "Network error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun resendCode() {
        val body = if (identifier.all { it.isDigit() }) {
            RequestCodeBody(telegramId = identifier.toLongOrNull(), username = null)
        } else {
            RequestCodeBody(telegramId = null, username = identifier)
        }

        lifecycleScope.launch {
            try {
                ApiClient.service(authManager).requestCode(body)
                Toast.makeText(this@CodeVerifyActivity, "Naya code bhej diya", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@CodeVerifyActivity, "Resend fail: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnVerify.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerify.text = if (loading) "Verify ho raha..." else "Verify"
    }
}
