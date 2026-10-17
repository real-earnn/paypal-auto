package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.real.earn.paypal.adapter.PrefixFileAdapter
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityPrefixInputBinding
import kotlinx.coroutines.launch

class PrefixInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrefixInputBinding
    private lateinit var authManager: AuthManager
    private var engineType: String = "paypal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrefixInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        engineType = intent.getStringExtra("engine_type") ?: "paypal"
        
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { performSearch() }
        binding.etPrefix.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
    }

    private fun performSearch() {
        val prefix = binding.etPrefix.text.toString().trim().removePrefix("+")
        if (prefix.length < 2) {
            Toast.makeText(this, "Prefix kam az kam 2 characters ka ho", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.recycler.adapter = null

        lifecycleScope.launch {
            try {
                val res = ApiClient.service(authManager).searchPrefix(prefix)
                binding.progressBar.visibility = View.GONE

                if (res.isSuccessful && res.body()?.success == true) {
                    val files = res.body()?.filesSafe ?: emptyList()
                    if (files.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvEmpty.text = "Is prefix ke liye koi file nahi mili"
                    } else {
                        binding.recycler.adapter = PrefixFileAdapter(prefix = prefix, items = files) { fileInfo ->
                            val intent = Intent(this@PrefixInputActivity, PermissionActivity::class.java).apply {
                                putExtra("mode", "prefix")
                                putExtra("service", "PayPal") // Prefix mode backend service name
                                putExtra("prefix", prefix)
                                putExtra("file", fileInfo.name)
                                putExtra("engine_type", engineType) // 🟢 Pass engine
                            }
                            startActivity(intent)
                        }
                    }
                } else if (res.code() == 401) {
                    Toast.makeText(this@PrefixInputActivity, "Session khatam", Toast.LENGTH_LONG).show()
                    authManager.clear()
                    startActivity(Intent(this@PrefixInputActivity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                } else {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "Search fail (code ${res.code()})"
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Network error: ${e.message}"
            }
        }
    }
}
