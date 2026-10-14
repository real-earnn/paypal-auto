package com.real.earn.paypal

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.databinding.ActivityPermissionBinding
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
            if (allGranted()) {
                Toast.makeText(this, "Phase 5 mein automation start hoga", Toast.LENGTH_LONG).show()
                // TODO: Phase 5 — AutomationActivity start karna
            } else {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
            }
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
        // Assistant check is fuzzy — treat as granted so we don't block the user forever
        val a11y = PermissionHelper.isAccessibilityEnabled(this)
        val overlay = PermissionHelper.isOverlayGranted(this)
        val battery = PermissionHelper.isBatteryOptimizationIgnored(this)
        return a11y && overlay && battery
    }
}
