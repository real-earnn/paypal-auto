package com.real.earn.paypal

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.real.earn.paypal.admin.MyDeviceAdminReceiver
import com.real.earn.paypal.databinding.ActivityVirtualProfileBinding

class VirtualProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVirtualProfileBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var workProfileUser: UserHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVirtualProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Har dafa screen pe aane par check karega
        checkProfileState()
    }

    private fun checkProfileState() {
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        
        // Check System API
        var hasWorkProfile = userManager.userProfiles.size > 1
        
        // Fallback: SharedPreferences (Agar system API fail ho jaye)
        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("profile_created", false)) {
            hasWorkProfile = true
        }

        if (hasWorkProfile) {
            workProfileUser = userManager.userProfiles.find { it != Process.myUserHandle() }
            if (workProfileUser != null) {
                showManagerState()
            } else {
                // Agar SharedPrefs true hai lekin system mein user nahi hai (matlab delete ho chuka hai)
                prefs.edit().putBoolean("profile_created", false).apply()
                showCreateState()
            }
        } else {
            showCreateState()
        }
    }

    private fun showCreateState() {
        binding.tvStatus.text = "Virtual Android is not active.\nCreate a new profile to use apps in a separate environment."
        binding.btnCreateProfile.visibility = View.VISIBLE
        binding.btnAddApps.visibility = View.GONE
        binding.btnDeleteProfile.visibility = View.GONE
        binding.recyclerApps.visibility = View.GONE

        binding.btnCreateProfile.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, 100)
            } else {
                Toast.makeText(this, "Your device does not support Work Profile.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showManagerState() {
        binding.tvStatus.text = "Virtual Android is Active ✅\nApps installed in Virtual Space:"
        binding.btnCreateProfile.visibility = View.GONE
        binding.btnAddApps.visibility = View.VISIBLE
        binding.btnDeleteProfile.visibility = View.VISIBLE
        binding.recyclerApps.visibility = View.VISIBLE

        loadAppsFromWorkProfile()

        binding.btnDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Virtual Android?")
                .setMessage("Ye poora virtual space aur iske andar ki saari apps delete ho jayengi. Kya aap sure hain?")
                .setPositiveButton("Delete") { _, _ ->
                    try {
                        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("profile_created", false).apply()
                        Toast.makeText(this, "Profile deleted! (Please restart device if apps still show)", Toast.LENGTH_LONG).show()
                        checkProfileState() // Refresh UI
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnAddApps.setOnClickListener {
            Toast.makeText(this, "App install feature coming in next step!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAppsFromWorkProfile() {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val workUser = workProfileUser ?: return

        try {
            // Get all activities in work profile
            val activities = launcherApps.getActivities(workUser)
            val uniquePackages = mutableSetOf<String>()
            val appList = mutableListOf<VirtualAppAdapter.AppInfoData>()

            for (activity in activities) {
                if (uniquePackages.contains(activity.applicationInfo.packageName)) continue
                uniquePackages.add(activity.applicationInfo.packageName)

                val appName = activity.applicationInfo.loadLabel(packageManager).toString()
                val appIcon = activity.applicationInfo.loadIcon(packageManager)

                appList.add(VirtualAppAdapter.AppInfoData(appName, appIcon, activity.applicationInfo.packageName))
            }

            binding.recyclerApps.layoutManager = LinearLayoutManager(this)
            binding.recyclerApps.adapter = VirtualAppAdapter(appList) { app ->
                // 🟢 OPEN APP IN WORK PROFILE
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                    // To launch in work profile, we use LauncherApps
                    val activitiesList = launcherApps.getActivities(workUser)
                    val targetActivity = activitiesList.find { it.applicationInfo.packageName == app.packageName }
                    
                    if (targetActivity != null) {
                        launcherApps.startMainActivity(targetActivity.componentName, workUser, null, null)
                        Toast.makeText(this, "Opening ${app.name} in Virtual Space...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to open app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load apps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Profile created successfully
            val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("profile_created", true).apply()
            
            checkProfileState()
            Toast.makeText(this, "Virtual Android Created Successfully!", Toast.LENGTH_SHORT).show()
        }
    }
}
