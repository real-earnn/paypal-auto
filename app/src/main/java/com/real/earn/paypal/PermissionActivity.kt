package com.real.earn.paypal

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.real.earn.paypal.databinding.ActivityPermissionBinding
import com.real.earn.paypal.service.AutomationService
import com.real.earn.paypal.utils.AdbPairingManager
import com.real.earn.paypal.utils.AppPrefs
import com.real.earn.paypal.utils.PermissionHelper
import kotlinx.coroutines.launch

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private lateinit var prefs: AppPrefs

    private var mode: String = "service"
    private var serviceName: String = "PayPal"
    private var countryName: String = ""
    private var countryCode: String = ""
    private var dialingCode: String = ""
    private var price: Double = 0.0
    private var prefix: String = ""
    private var file: String = ""
    private var engineType: String = "paypal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPrefs(this)

        mode = intent.getStringExtra("mode") ?: "service"
        serviceName = intent.getStringExtra("service") ?: "PayPal"
        countryName = intent.getStringExtra("country") ?: ""
        countryCode = intent.getStringExtra("country_code") ?: ""
        dialingCode = intent.getStringExtra("dialing_code") ?: ""
        price = intent.getDoubleExtra("price", 0.0)
        prefix = intent.getStringExtra("prefix") ?: ""
        file = intent.getStringExtra("file") ?: ""
        engineType = intent.getStringExtra("engine_type") ?: "paypal"

        binding.tvTitle.text = when (mode) {
            "auto" -> "$serviceName — Auto Prefix"
            "prefix" -> "$serviceName — $prefix ($file)"
            else -> "$serviceName — $countryName"
        }

        if (engineType == "tiktok") {
            binding.rowOldAccountMethod.visibility = View.VISIBLE
        } else {
            binding.rowOldAccountMethod.visibility = View.GONE
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.rowAccessibility.setOnClickListener { PermissionHelper.openAccessibilitySettings(this) }
        binding.rowOverlay.setOnClickListener { PermissionHelper.openOverlaySettings(this) }
        binding.rowBattery.setOnClickListener { PermissionHelper.openBatterySettings(this) }

        binding.switchAutoFlight.isChecked = prefs.autoFlightMode
        binding.switchAutoFlight.setOnCheckedChangeListener { _, checked -> prefs.autoFlightMode = checked }

        binding.switchCaptchaAlarm.isChecked = prefs.captchaAlarmEnabled
        binding.switchCaptchaAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.captchaAlarmEnabled = checked
            updateSoundLabel()
        }

        binding.btnChooseSound.setOnClickListener { openRingtonePicker() }
        updateSoundLabel()

        // 🟢 ADB toggle setup
        setupAdbToggle()

        // 🟢 Start button
        binding.btnStart.setOnClickListener {
            if (!allGranted()) {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val adbToggleOn = binding.switchAdbPairing.isChecked
                if (adbToggleOn) {
                    val adbConnected = AdbPairingManager.isConnected(this@PermissionActivity)
                    if (!adbConnected) {
                        Toast.makeText(
                            this@PermissionActivity,
                            "ADB connect nahi hai — pehle pair karo (toggle ke neeche)",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }
                startAutomation()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 🟢 ADB Toggle Setup + Pairing Flow
    // ══════════════════════════════════════════════════════════════════════
    private fun setupAdbToggle() {
        // Restore saved host
        prefs.adbHost?.let { binding.etAdbHost.setText(it) }

        // Restore previous state
        val wasPaired = prefs.adbPaired
        binding.switchAdbPairing.isChecked = wasPaired

        // Set initial section visibility
        binding.layoutAdbSection.visibility = if (wasPaired) View.VISIBLE else View.GONE

        binding.switchAdbPairing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.layoutAdbSection.visibility = View.VISIBLE
                checkAdbConnectionAndUpdate()
            } else {
                binding.layoutAdbSection.visibility = View.GONE
            }
        }

        binding.btnAdbPair.setOnClickListener { onPairButtonClick() }

        // 🟢 Auto-check on activity open if toggle already ON
        if (wasPaired) {
            lifecycleScope.launch {
                val connected = AdbPairingManager.isConnected(this@PermissionActivity)
                if (connected) {
                    showConnectedUi(prefs.adbHost ?: "", prefs.adbConnectPort)
                } else {
                    // Connection lost — show pair UI so user can re-pair
                    showPairUi("⚠ Connection lost — pair again")
                }
            }
        }
    }

    /**
     * Called when toggle is turned ON.
     * - If already connected → show "✅ Connected" + Disconnect button
     * - If not connected → show pairing fields
     */
    private fun checkAdbConnectionAndUpdate() {
        lifecycleScope.launch {
            binding.tvAdbStatus.text = "Checking connection…"
            binding.tvAdbStatus.setTextColor(getColor(R.color.text_dim))

            val connected = AdbPairingManager.isConnected(this@PermissionActivity)

            if (connected) {
                showConnectedUi(prefs.adbHost ?: "", prefs.adbConnectPort)
            } else {
                showPairUi("⚠ Not connected — pair below")
            }
        }
    }

    private fun showConnectedUi(host: String, port: Int) {
        binding.tvAdbStatus.text = "✅ Connected: $host:$port"
        binding.tvAdbStatus.setTextColor(getColor(R.color.success_green))
        binding.btnAdbPair.text = "Disconnect"
        binding.btnAdbPair.backgroundTintList = android.content.res.ColorStateList.valueOf(
            getColor(R.color.error_red)
        )
        binding.etAdbHost.isEnabled = false
        binding.etCode.isEnabled = false
        binding.etCode.text?.clear()
    }

    private fun showPairUi(statusMsg: String) {
        binding.tvAdbStatus.text = statusMsg
        binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
        binding.btnAdbPair.text = "Pair Now"
        binding.btnAdbPair.backgroundTintList = android.content.res.ColorStateList.valueOf(
            getColor(R.color.paypal_blue)
        )
        binding.etAdbHost.isEnabled = true
        binding.etCode.isEnabled = true
    }

    private fun onPairButtonClick() {
        // If currently connected → Disconnect
        if (binding.btnAdbPair.text == "Disconnect") {
            AdbPairingManager.clearPairing(this)
            binding.switchAdbPairing.isChecked = false
            binding.layoutAdbSection.visibility = View.GONE
            Toast.makeText(this, "ADB disconnected", Toast.LENGTH_SHORT).show()
            return
        }

        // Otherwise, attempt pairing
        val hostPort = binding.etAdbHost.text.toString().trim()
        val code = binding.etCode.text.toString().trim()

        if (hostPort.isEmpty() || code.length != 6) {
            binding.tvAdbStatus.text = "⚠ IP:Port aur 6-digit code sahi likho"
            binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
            return
        }

        val parts = hostPort.split(":")
        if (parts.size != 2) {
            binding.tvAdbStatus.text = "⚠ Format: IP:Port (e.g. 192.168.1.5:37755)"
            binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
            return
        }

        val host = parts[0].trim()
        val pairingPort = parts[1].trim().toIntOrNull()
        if (pairingPort == null || pairingPort <= 0) {
            binding.tvAdbStatus.text = "⚠ Port number sahi nahi hai"
            binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
            return
        }

        binding.btnAdbPair.isEnabled = false
        binding.tvAdbStatus.text = "🔗 Pairing ho rahi hai…"
        binding.tvAdbStatus.setTextColor(getColor(R.color.text_dim))

        lifecycleScope.launch {
            val (success, msg) = AdbPairingManager.pair(
                this@PermissionActivity, host, pairingPort, code
            )

            if (!success) {
                binding.tvAdbStatus.text = "✘ $msg"
                binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
                binding.btnAdbPair.isEnabled = true
                return@launch
            }

            binding.tvAdbStatus.text = "✔ Paired! Connect port dhoond raha hun…"

            val connectPort = AdbPairingManager.discoverConnectPort(
                this@PermissionActivity, host
            )

            if (connectPort != null) {
                AdbPairingManager.savePairing(
                    this@PermissionActivity, host, pairingPort, connectPort
                )
                showConnectedUi(host, connectPort)
                Toast.makeText(
                    this@PermissionActivity,
                    "ADB connected — ab Flight Mode aur Clear Data fast honge",
                    Toast.LENGTH_SHORT
                ).show()
                binding.btnAdbPair.isEnabled = true
            } else {
                binding.tvAdbStatus.text =
                    "⚠ mDNS se connect port nahi mila — manually enter karo"
                binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
                binding.btnAdbPair.isEnabled = true
                showConnectPortDialog(host, pairingPort)
            }
        }
    }

    private fun showConnectPortDialog(host: String, pairingPort: Int) {
        val input = EditText(this).apply {
            hint = "Connect Port (e.g. 42567)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Connect Port")
            .setMessage("Wireless Debugging screen pe 'IP address & Port' mein jo port hai woh enter karo:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val cp = input.text.toString().trim().toIntOrNull()
                if (cp != null && cp > 0) {
                    AdbPairingManager.savePairing(this, host, pairingPort, cp)
                    showConnectedUi(host, cp)
                    Toast.makeText(this, "ADB connected!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvAdbStatus.text = "✘ Port invalid"
                    binding.tvAdbStatus.setTextColor(getColor(R.color.error_red))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startAutomation() {
        val actIntent = Intent(this, AutomationActivity::class.java).apply {
            putExtra("mode", mode)
            putExtra("service", serviceName)
            putExtra("country", countryName)
            putExtra("prefix", prefix)
            putExtra("file", file)
        }
        startActivity(actIntent)

        val isOldAccount = binding.switchOldAccountMethod.isChecked

        AutomationService.start(
            context = this,
            mode = mode,
            service = serviceName,
            country = countryName,
            countryCode = countryCode,
            dialingCode = dialingCode,
            prefix = prefix,
            file = file,
            engineType = engineType,
            oldAccountMethod = isOldAccount
        )
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        setRowState(binding.tvAccessibilityState, PermissionHelper.isAccessibilityEnabled(this))
        setRowState(binding.tvOverlayState, PermissionHelper.isOverlayGranted(this))
        setRowState(binding.tvBatteryState, PermissionHelper.isBatteryOptimizationIgnored(this))
        refreshStartButton()
    }

    private fun refreshStartButton() {
        val ok = allGranted()
        binding.btnStart.isEnabled = ok
        binding.btnStart.alpha = if (ok) 1f else 0.5f
    }

    private fun setRowState(view: TextView, granted: Boolean) {
        if (granted) {
            view.text = "✔ Enabled"
            view.setTextColor(getColor(R.color.success_green))
        } else {
            view.text = "✘ Tap to enable"
            view.setTextColor(getColor(R.color.error_red))
        }
    }

    private fun allGranted(): Boolean {
        return PermissionHelper.isAccessibilityEnabled(this) &&
                PermissionHelper.isOverlayGranted(this) &&
                PermissionHelper.isBatteryOptimizationIgnored(this)
    }

    private fun updateSoundLabel() {
        if (!prefs.captchaAlarmEnabled) {
            binding.tvSelectedSound.text = "Alarm disabled"
            binding.btnChooseSound.isEnabled = false
            binding.btnChooseSound.alpha = 0.5f
            return
        }
        binding.btnChooseSound.isEnabled = true
        binding.btnChooseSound.alpha = 1f

        val uri = prefs.captchaAlarmUri
        if (uri.isNullOrEmpty()) {
            binding.tvSelectedSound.text = "Default system alarm"
        } else {
            val name = try {
                val r = RingtoneManager.getRingtone(this, Uri.parse(uri))
                r?.getTitle(this) ?: "Custom sound"
            } catch (_: Exception) { "Custom sound" }
            binding.tvSelectedSound.text = "Selected: $name"
        }
    }

    private fun openRingtonePicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Captcha Alarm Sound")

                val existing = prefs.captchaAlarmUri
                if (!existing.isNullOrEmpty()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
                } else {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                }
            }
            startActivityForResult(intent, 5001)
        } catch (e: Exception) {
            Toast.makeText(this, "Ringtone picker not available: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 5001 && resultCode == Activity.RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            }

            if (uri != null) {
                prefs.captchaAlarmUri = uri.toString()
                Toast.makeText(this, "Alarm sound saved", Toast.LENGTH_SHORT).show()
            } else {
                prefs.captchaAlarmUri = null
                Toast.makeText(this, "Default alarm set", Toast.LENGTH_SHORT).show()
            }
            updateSoundLabel()
        }
    }
}
