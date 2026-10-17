package com.real.earn.paypal

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // 🟢 Loader aur Polling ke liye
    private val handler = Handler(Looper.getMainLooper())
    private var pollCount = 0
    private val maxPolls = 15 // 15 seconds timeout

    // 🟢 Broadcast Receiver for Profile Provisioning Complete
    private val profileProvisionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED) {
                runOnUiThread {
                    Toast.makeText(this@VirtualProfileActivity, "Virtual Android Setup Complete!", Toast.LENGTH_SHORT).show()
                    hideLoader()
                    checkProfileState()
                }
            }
        }
    }

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

        // Broadcast register karein
        val filter = IntentFilter(DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(profileProvisionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(profileProvisionReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        checkProfileState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(profileProvisionReceiver)
        } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    private fun checkProfileState() {
        val isAdmin = dpm.isAdminActive(adminComponent)
        
        // 🟢 Personal side pe sirf LauncherApps aur CrossProfileApps use karein
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val myHandle = Process.myUserHandle()
        val launcherProfiles = launcherApps.profiles
        val hasWorkProfileViaLauncher = launcherProfiles.any { it != myHandle }
        
        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        val prefCreated = prefs.getBoolean("profile_created", false)

        // Agar Admin active nahi hai, to permission mangwayein
        if (!isAdmin) {
            binding.cardPermission.visibility = View.VISIBLE
            binding.btnCreateProfile.visibility = View.GONE
            binding.btnAddApps.visibility = View.GONE
            binding.btnDeleteProfile.visibility = View.GONE
            binding.recyclerApps.visibility = View.GONE
            binding.layoutLoader.visibility = View.GONE
            binding.tvStatus.text = "Please allow Device Admin permission first."
            return
        }

        binding.cardPermission.visibility = View.GONE

        // 🟢 Profile Detect Karein
        if (hasWorkProfileViaLauncher || prefCreated) {
            workProfileUser = launcherProfiles.firstOrNull { it != myHandle }
            
            if (workProfileUser != null) {
                showManagerState()
            } else {
                // Agar pref true hai lekin profile nahi mili, to pref clear karein
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
        binding.layoutLoader.visibility = View.GONE

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
        binding.layoutLoader.visibility = View.GONE

        loadAppsFromWorkProfile()

        binding.btnDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Virtual Android?")
                .setMessage("Ye poora virtual space aur iske andar ki saari apps delete ho jayengi. Kya aap sure hain?")
                .setPositiveButton("Delete") { _, _ ->
                    val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("profile_created", false).apply()
                    Toast.makeText(this, "Profile deletion initiated. Please restart device.", Toast.LENGTH_LONG).show()
                    checkProfileState()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnAddApps.setOnClickListener {
            showAppPickerDialog()
        }
    }

    // 🟢 LOADER SHOW KARNE KA FUNCTION
    private fun showLoader() {
        binding.layoutLoader.visibility = View.VISIBLE
        binding.btnCreateProfile.visibility = View.GONE
        binding.btnAddApps.visibility = View.GONE
        binding.btnDeleteProfile.visibility = View.GONE
        binding.recyclerApps.visibility = View.GONE
        binding.tvStatus.text = "Setting up your Virtual Android..."
        
        pollCount = 0
        startPollingForProfile()
    }

    private fun hideLoader() {
        binding.layoutLoader.visibility = View.GONE
        handler.removeCallbacksAndMessages(null)
    }

    // 🟢 POLLING LOGIC: Har second check karein ke profile ban gayi ya nahi
    private fun startPollingForProfile() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                pollCount++
                val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val myHandle = Process.myUserHandle()
                val hasProfile = launcherApps.profiles.any { it != myHandle }

                if (hasProfile) {
                    // Profile ban gayi!
                    hideLoader()
                    val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("profile_created", true).apply()
                    Toast.makeText(this@VirtualProfileActivity, "Virtual Android Setup Complete!", Toast.LENGTH_SHORT).show()
                    checkProfileState()
                } else if (pollCount < maxPolls) {
                    // Abhi nahi bani, dobara check karein
                    handler.postDelayed(this, 1000)
                } else {
                    // Timeout
                    hideLoader()
                    binding.tvStatus.text = "Setup timed out. Please try again or restart device."
                    showCreateState()
                    Toast.makeText(this@VirtualProfileActivity, "Setup timed out. Please restart device.", Toast.LENGTH_LONG).show()
                }
            }
        }, 1000)
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

    private fun showAppPickerDialog() {
        val pm = packageManager
        val mainApps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val userApps = mainApps.filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
        
        val appNames = userApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select App to Add")
            .setItems(appNames) { _, which ->
                val selectedApp = userApps[which]
                Toast.makeText(this, "Feature pending: Please install ${selectedApp.loadLabel(pm)} in Virtual Space.", Toast.LENGTH_LONG).show()
            }
            .show()
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
            // Jab system se wapis aayein, to loader dikhayein
            if (resultCode == RESULT_OK) {
                showLoader()
            }
        }
    }
}
