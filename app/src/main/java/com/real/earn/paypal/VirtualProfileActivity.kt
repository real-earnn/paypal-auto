package com.real.earn.paypal

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.admin.MyDeviceAdminReceiver
import com.real.earn.paypal.databinding.ActivityVirtualProfileBinding

class VirtualProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVirtualProfileBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVirtualProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        binding.btnBack.setOnClickListener { finish() }

        // Check if Work Profile already exists
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        if (userManager.userProfiles.size > 1) { // Main + Work Profile
            showManagerState()
        } else {
            showCreateState()
        }
    }

    // State A: Create Button
    private fun showCreateState() {
        binding.tvStatus.text = "Virtual Android is not active."
        binding.btnCreateProfile.visibility = android.view.View.VISIBLE
        binding.btnAddApps.visibility = android.view.View.GONE
        binding.btnDeleteProfile.visibility = android.view.View.GONE

        binding.btnCreateProfile.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_PROFILE_NAME, "VirtualSpace")
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, 100)
            } else {
                Toast.makeText(this, "Your device does not support Work Profile.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // State B: Manager (Delete / Add Apps)
    private fun showManagerState() {
        binding.tvStatus.text = "Virtual Android is Active ✅\nYou can now add apps."
        binding.btnCreateProfile.visibility = android.view.View.GONE
        binding.btnAddApps.visibility = android.view.View.VISIBLE
        binding.btnDeleteProfile.visibility = android.view.View.VISIBLE

        binding.btnDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Virtual Android?")
                .setMessage("Ye poora virtual space aur iske andar ki saari apps delete ho jayengi. Kya aap sure hain?")
                .setPositiveButton("Delete") { _, _ ->
                    try {
                        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
                        val workProfile = userManager.userProfiles[1]
                        dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE) // Simple trigger
                        // Real deletion usually requires API depending on Android version
                        Toast.makeText(this, "Deleting Profile...", Toast.LENGTH_SHORT).show()
                        finish() // System will handle the wipe
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnAddApps.setOnClickListener {
            // Next step: Hum yahan Add Apps ka intent call karenge
            Toast.makeText(this, "Add Apps screen coming next...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Profile created successfully
            showManagerState()
            Toast.makeText(this, "Virtual Android Created Successfully!", Toast.LENGTH_SHORT).show()
        }
    }
}
