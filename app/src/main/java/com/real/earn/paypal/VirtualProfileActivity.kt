package com.real.earn.paypal

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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
import java.io.File

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

        binding.btnAllowAdmin.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Virtual Android banane ke liye is permission ki zaroorat hai.")
                }
                startActivityForResult(intent, 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkProfileState()
    }

    private fun checkProfileState() {
        val isAdmin = dpm.isAdminActive(adminComponent)
        
        // 🟢 SAB SE STRONG DETECTION: LauncherApps se profiles check karein
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val myHandle = Process.myUserHandle()
        workProfileUser = launcherApps.profiles.firstOrNull { it != myHandle }

        val isProfileOwner = dpm.isProfileOwnerApp(packageName)
        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        val prefCreated = prefs.getBoolean("profile_created", false)

        // Agar Admin active nahi hai, to pehle wo mangwayein
        if (!isAdmin) {
            binding.cardPermission.visibility = View.VISIBLE
            binding.btnCreateProfile.visibility = View.GONE
            binding.btnAddApps.visibility = View.GONE
            binding.btnDeleteProfile.visibility = View.GONE
            binding.recyclerApps.visibility = View.GONE
            binding.tvStatus.text = "Please allow Device Admin permission first."
            return
        }

        binding.cardPermission.visibility = View.GONE

        // Agar profile mil gayi (ya pref mein hai), to Manager state dikhayein
        if (workProfileUser != null || isProfileOwner || prefCreated) {
            if (workProfileUser == null) {
                // Fallback agar LauncherApps fail ho jaye
                val userManager = getSystemService(Context.USER_SERVICE) as UserManager
                workProfileUser = userManager.userProfiles.firstOrNull { it != myHandle }
            }

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
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, 100)
            } else {
                Toast.makeText(this, "Device doesn't support Work Profile.", Toast.LENGTH_LONG).show()
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
                        
                        // Profile delete karne ka system command (agar API allow kare)
                        if (workProfileUser != null) {
                            dpm.wipeData(0) // Profile data wipe
                        }
                        
                        Toast.makeText(this, "Profile deletion initiated. Please restart device.", Toast.LENGTH_LONG).show()
                        checkProfileState()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 🟢 NAYA: Add Apps Functionality
        binding.btnAddApps.setOnClickListener {
            showAppPickerDialog()
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
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to open app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load apps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 🟢 NAYA: Main Phone ki Apps dikhane ka Dialog
    private fun showAppPickerDialog() {
        val pm = packageManager
        val mainApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val userApps = mainApps.filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
        
        val appNames = userApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select App to Add")
            .setItems(appNames) { _, which ->
                val selectedApp = userApps[which]
                installAppToWorkProfile(selectedApp.packageName)
            }
            .show()
    }

    // 🟢 NAYA: App ko Work Profile mein install karna
    private fun installAppToWorkProfile(packageName: String) {
        val workUser = workProfileUser ?: return
        Toast.makeText(this, "Installing $packageName...", Toast.LENGTH_SHORT).show()
        
        try {
            // Yahan hum LauncherApps ya PackageInstaller use kar sakte hain
            // Asal mein direct install ke liye humein APK ki zaroorat hoti hai.
            // Filhal hum system ko bata rahe hain ke is app ko work profile mein launch kare.
            // Real installation ke liye APK file extract karni padti hai, jo ke complex hai.
            
            // Filhal ke liye hum user ko bata dete hain ke Play Store se install karein
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("market://details?id=$packageName")
            // Note: Work profile mein Play Store alag hota hai.
            Toast.makeText(this, "Feature pending: Please open Play Store in Virtual Space to install apps.", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Device Admin Activated!", Toast.LENGTH_SHORT).show()
                checkProfileState()
            }
        }
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("profile_created", true).apply()
                Toast.makeText(this, "Setting up Virtual Android... Please wait.", Toast.LENGTH_SHORT).show()
                // Kuch devices pe profile banne mein time lagta hai, isliye thoda wait karein
                binding.root.postDelayed({ checkProfileState() }, 3000)
            }
        }
    }
}
