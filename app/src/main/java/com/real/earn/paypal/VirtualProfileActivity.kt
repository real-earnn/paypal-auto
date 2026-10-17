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

    // Request code for Device Admin activation
    private val REQUEST_CODE_ENABLE_ADMIN = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVirtualProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        binding.btnBack.setOnClickListener { finish() }

        // 🟢 Device Admin Allow Button ka Listener
        binding.btnAllowAdmin.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Virtual Android banane ke liye is permission ki zaroorat hai.")
                }
                startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkProfileState()
    }

    private fun checkProfileState() {
        val isAdmin = dpm.isAdminActive(adminComponent)
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        
        // Profile ka status check karein
        val isProfileOwner = dpm.isProfileOwnerApp(packageName)
        val prefCreated = prefs.getBoolean("profile_created", false)

        // 🟢 Step 1: Agar Admin active nahi hai, to sirf Permission Card dikhayein
        if (!isAdmin) {
            binding.cardPermission.visibility = View.VISIBLE
            binding.btnCreateProfile.visibility = View.GONE
            binding.btnAddApps.visibility = View.GONE
            binding.btnDeleteProfile.visibility = View.GONE
            binding.recyclerApps.visibility = View.GONE
            binding.tvStatus.text = "Please allow Device Admin permission first."
            return
        }

        // 🟢 Step 2: Admin active hai, ab check karein ke profile bani hai ya nahi
        binding.cardPermission.visibility = View.GONE

        if (isProfileOwner || prefCreated) {
            workProfileUser = userManager.userProfiles.find { it != Process.myUserHandle() }
            if (workProfileUser != null) {
                showManagerState()
            } else {
                prefs.edit().putBoolean("profile_created", false).apply()
                showCreateState()
            }
        } else {
            showCreateState()
        }
    }

    private fun showCreateState() {
        binding.tvStatus.text = "Device Admin is active ✅\nNow you can create Virtual Android."
        binding.btnCreateProfile.visibility = View.VISIBLE
        binding.btnAddApps.visibility = View.GONE
        binding.btnDeleteProfile.visibility = View.GONE
        binding.recyclerApps.visibility = View.GONE

        binding.btnCreateProfile.setOnClickListener {
            // Ab seedha profile create karein kyunke admin pehle se active hai
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
                        Toast.makeText(this, "Profile deleted! (Restart device if apps still show)", Toast.LENGTH_LONG).show()
                        checkProfileState()
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
            val activities = launcherApps.getActivityList(null, workUser)
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
                try {
                    val activitiesList = launcherApps.getActivityList(app.packageName, workUser)
                    val targetActivity = activitiesList.firstOrNull()
                    
                    if (targetActivity != null) {
                        launcherApps.startMainActivity(targetActivity.componentName, workUser, null, null)
                        Toast.makeText(this, "Opening ${app.name} in Virtual Space...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "App not found in Virtual Space.", Toast.LENGTH_SHORT).show()
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
        
        // 🟢 Device Admin activation ka result
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Device Admin Activated!", Toast.LENGTH_SHORT).show()
                checkProfileState() // UI refresh karein, ab Create button nazar aayega
            } else {
                Toast.makeText(this, "Device Admin permission denied.", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Work Profile creation ka result
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("profile_created", true).apply()
                Toast.makeText(this, "Setting up Virtual Android... Please wait.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Profile creation cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
