package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.real.earn.paypal.adapter.CountryAdapter
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityCountrySelectBinding
import kotlinx.coroutines.launch

class CountrySelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCountrySelectBinding
    private lateinit var authManager: AuthManager
    private var serviceName: String = ""
    private var isUnpaid: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountrySelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        serviceName = intent.getStringExtra("service") ?: ""
        isUnpaid = intent.getBooleanExtra("is_unpaid", false)

        binding.tvTitle.text = serviceName
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }

        if (serviceName.isEmpty()) {
            Toast.makeText(this, "Service missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadCountries()
    }

    private fun loadCountries() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val res = ApiClient.service(authManager).getCountries(serviceName)
                if (res.isSuccessful && res.body()?.success == true) {

                    val body = res.body()!!
                    val countries = body.countries ?: emptyList()      // 🟢 safe
                    val unpaid = body.isUnpaid || isUnpaid

                    if (countries.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvEmpty.text = "Is service ke liye koi country available nahi"
                    } else {
                        binding.recycler.adapter = CountryAdapter(countries, unpaid) { c ->
                            val i = Intent(this@CountrySelectActivity, PermissionActivity::class.java)
                            i.putExtra("service", serviceName)
                            i.putExtra("country", c.name)
                            i.putExtra("country_code", c.code)
                            i.putExtra("dialing_code", c.dialingCode)
                            i.putExtra("price", c.price)
                            i.putExtra("is_unpaid", unpaid)
                            startActivity(i)
                        }
                    }

                } else if (res.code() == 401) {
                    Toast.makeText(this@CountrySelectActivity, "Session khatam", Toast.LENGTH_LONG).show()
                    authManager.clear()
                    startActivity(
                        Intent(this@CountrySelectActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                } else {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "Countries load nahi hui (code ${res.code()})"
                }
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Network error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
