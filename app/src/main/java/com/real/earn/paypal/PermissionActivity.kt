package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.databinding.ActivityPermissionBinding
import com.real.earn.paypal.service.AutomationService
import com.real.earn.paypal.utils.PermissionHelper

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private var serviceName: String = ""
    private var countryName: String = ""
    private var countryCode: String = ""
    private var dialingCode: String = ""
    private var price: Double = 0.0
    private var isUnpaid: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serviceName = intent.getStringExtra("service") ?: ""
        countryName = intent.getStringExtra("country") ?: ""
        countryCode = intent.getStringExtra("country_code") ?: ""
        dialingCode = intent.getStringExtra("dialing_code") ?: ""
        price = intent.getDoubleExtra("price", 0.0)
        isUnpaid = intent.getBooleanExtra("is_unpaid", false)

        binding.tvTitle.text = "$serviceName — $countryName"

        binding.btnBack.setOnClickListener { finish() }
        binding.rowAccessibility.setOnClickListener { PermissionHelper.openAccessibilitySettings(this) }
        binding.rowOverlay.setOnClickListener { PermissionHelper.openOverlaySettings(this) }
        binding.rowBattery.setOnClickListener { PermissionHelper.openBatterySettings(this) }
        binding.rowAssistant.setOnClickListener { PermissionHelper.openAssistantSettings(this) }

        binding.btnStart.setOnClickListener {
            if (!allGranted()) {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🟢 Launch Automation Activity (shows logs)
            val actIntent = Intent(this, AutomationActivity::class.java).apply {
                putExtra("service", serviceName)
                putExtra("country", countryName)
                putExtra("country_code", countryCode)
                putExtra("dialing_code", dialingCode)
                putExtra("price", price)
            }
            startActivity(actIntent)

            // 🟢 Start Automation Service (foreground + overlay)
            AutomationService.start(
                this,
                service = serviceName,
                country = countryName,
                countryCode = countryCode,
                dialingCode = dialingCode,
                price = price
            )

            Toast.makeText(this, "Automation start ho rahi hai…", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val a11y = PermissionHelper.isAccessibilityEnabled(this)
        val overlay = PermissionHelper.isOverlayGranted(this)
        val battery = PermissionHelper.isBatteryOptimizationIgnored(this)
        val assistant = PermissionHelper.isAssistantDefault(this)

        setRowState(binding.tvAccessibilityState, a11y)
        setRowState(binding.tvOverlayState, overlay)
        setRowState(binding.tvBatteryState, battery)
        setRowState(binding.tvAssistantState, assistant)

        binding.btnStart.isEnabled = allGranted()
        binding.btnStart.alpha = if (allGranted()) 1f else 0.5f
    }

    private fun setRowState(view: android.widget.TextView, granted: Boolean) {
        if (granted) {
            view.text = "✔ Enabled"
            view.setTextColor(getColor(R.color.success_green))
        } else {
            view.text = "✘ Tap to enable"
            view.setTextColor(getColor(R.color.error_red))
        }
    }

    private fun allGranted(): Boolean {
        val a11y = PermissionHelper.isAccessibilityEnabled(this)
        val overlay = PermissionHelper.isOverlayGranted(this)
        val battery = PermissionHelper.isBatteryOptimizationIgnored(this)
        return a11y && overlay && battery
    }
}
