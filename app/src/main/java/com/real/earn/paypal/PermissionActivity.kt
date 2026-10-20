package com.real.earn.paypal

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

        // 🟢 START: check ADB connection first; if not connected → pairing dialog
        binding.btnStart.setOnClickListener {
            if (!allGranted()) {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val adbConnected = AdbPairingManager.isConnected(this@PermissionActivity)
                if (adbConnected) {
                    startAutomation()
                } else {
                    // 🟢 Toggle effectively OFF — show pairing setup
                    Toast.makeText(
                        this@PermissionActivity,
                        "ADB connect nahi hai — dobara pair karo",
                        Toast.LENGTH_LONG
                    ).show()
                    showAdbPairingDialog()
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 🟢 ADB Pairing Dialog (programmatic — no XML change needed)
    // ══════════════════════════════════════════════════════════════════════
    private fun showAdbPairingDialog() {
        if (!AdbPairingManager.isPairingSupported()) {
            Toast.makeText(
                this,
                "Wireless debugging pairing requires Android 11+",
                Toast.LENGTH_LONG
            ).show()
            // Fallback: start automation with accessibility anyway
            startAutomation()
            return
        }

        val prefs = AppPrefs(this)
        val savedHost = prefs.adbHost ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val tvHelp = TextView(this).apply {
            text = "1) Settings → Developer options → Wireless debugging ON\n" +
                    "2) Tap 'Pair device with pairing code'\n" +
                    "3) Enter IP:Port and the 6-digit code below"
            textSize = 13f
            setPadding(0, 0, 0, 20)
        }

        val etHostPort = EditText(this).apply {
            hint = "IP:Pairing Port  (e.g. 192.168.1.5:37755)"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(savedHost)
        }

        val etCode = EditText(this).apply {
            hint = "6-digit pairing code"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val tvStatus = TextView(this).apply {
            text = ""
            textSize = 13f
            setPadding(0, 15, 0, 0)
        }

        layout.addView(tvHelp)
        layout.addView(etHostPort)
        layout.addView(etCode)
        layout.addView(tvStatus)

        val dialog = AlertDialog.Builder(this)
            .setTitle("ADB Pairing")
            .setView(layout)
            .setPositiveButton("Pair", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val hostPort = etHostPort.text.toString().trim()
                val code = etCode.text.toString().trim()

                if (hostPort.isEmpty() || code.length != 6) {
                    tvStatus.text = "IP:Port aur 6-digit code sahi likho"
                    return@setOnClickListener
                }

                val parts = hostPort.split(":")
                if (parts.size != 2) {
                    tvStatus.text = "Format: IP:Port (e.g. 192.168.1.5:37755)"
                    return@setOnClickListener
                }

                val host = parts[0].trim()
                val pairingPort = parts[1].trim().toIntOrNull()
                if (pairingPort == null) {
                    tvStatus.text = "Port number sahi nahi hai"
                    return@setOnClickListener
                }

                btn.isEnabled = false
                tvStatus.text = "Pairing ho rahi hai..."

                lifecycleScope.launch {
                    val (success, msg) = AdbPairingManager.pair(
                        this@PermissionActivity, host, pairingPort, code
                    )

                    if (!success) {
                        tvStatus.text = "✘ $msg"
                        btn.isEnabled = true
                        return@launch
                    }

                    tvStatus.text = "✔ Paired! Connect port dhoond raha hun..."
                    val connectPort = AdbPairingManager.discoverConnectPort(
                        this@PermissionActivity, host
                    )

                    if (connectPort != null) {
                        AdbPairingManager.savePairing(
                            this@PermissionActivity, host, pairingPort, connectPort
                        )
                        tvStatus.text = "✔ Connected on port $connectPort"
                        dialog.dismiss()
                        Toast.makeText(
                            this@PermissionActivity,
                            "ADB connected — automation start ho rahi hai",
                            Toast.LENGTH_SHORT
                        ).show()
                        startAutomation()
                    } else {
                        // mDNS could not find connect port — ask user to enter it manually
                        tvStatus.text = "⚠ Connect port nahi mila. Wireless Debugging screen pe jo port hai woh enter karo."
                        btn.isEnabled = true

                        val etConnectPort = EditText(this@PermissionActivity).apply {
                            hint = "Connect Port (e.g. 5555)"
                            inputType = InputType.TYPE_CLASS_NUMBER
                        }
                        layout.addView(etConnectPort)

                        btn.text = "Save Connect Port"
                        btn.setOnClickListener {
                            val cp = etConnectPort.text.toString().trim().toIntOrNull()
                            if (cp == null || cp <= 0) {
                                tvStatus.text = "Connect port sahi nahi hai"
                                return@setOnClickListener
                            }
                            AdbPairingManager.savePairing(
                                this@PermissionActivity, host, pairingPort, cp
                            )
                            dialog.dismiss()
                            Toast.makeText(
                                this@PermissionActivity,
                                "ADB connected — automation start ho rahi hai",
                                Toast.LENGTH_SHORT
                            ).show()
                            startAutomation()
                        }
                    }
                }
            }
        }

        dialog.show()
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
