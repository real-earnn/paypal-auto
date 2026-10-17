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
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.real.earn.paypal.admin.MyDeviceAdminReceiver
import com.real.earn.paypal.admin.WorkProfileProvisioningManager
import com.real.earn.paypal.admin.WorkProfileSetupState
import com.real.earn.paypal.databinding.ActivityVirtualProfileBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VirtualProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVirtualProfileBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var workProfileUser: UserHandle? = null
    private var timeoutJob: Job? = null

    // 🟢 سسٹم لیول براڈکاسٹ (جب پروفائل واقعی بن جائے)
    private val profileAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_MANAGED_PROFILE_ADDED) {
                val newProfileHandle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_USER)
                }
                WorkProfileProvisioningManager.markSuccess(newProfileHandle)
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

        // 🟢 مینو بٹن سیٹ اپ
        binding.btnMenu.setOnClickListener { showPopupMenu(it) }

        // 🟢 سٹیٹ فلوز کو سنیں
        observeProvisioningState()
    }

    override fun onResume() {
        super.onResume()
        if (WorkProfileProvisioningManager.setupState.value !is WorkProfileSetupState.InProgress) {
            checkProfileState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(profileAddedReceiver) } catch (_: Exception) {}
        timeoutJob?.cancel()
    }

    private fun observeProvisioningState() {
        lifecycleScope.launch {
            WorkProfileProvisioningManager.setupState.collect { state ->
                when (state) {
                    is WorkProfileSetupState.InProgress -> showLoader()
                    is WorkProfileSetupState.Success -> {
                        timeoutJob?.cancel()
                        WorkProfileProvisioningManager.reset()
                        Toast.makeText(this@VirtualProfileActivity, "Virtual Android Setup Complete!", Toast.LENGTH_SHORT).show()
                        
                        // 🟢 SharedPreferences کو فورس اپ ڈیٹ کریں
                        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("profile_created", true).apply()

                        // ہینڈل سیٹ کریں (اگر ملا ہے تو)
                        if (state.profileUserHandle != null) {
                            workProfileUser = state.profileUserHandle
                        }
                        
                        // UI کو فورس اپ ڈیٹ کریں
                        showManagerState()
                    }
                    is WorkProfileSetupState.Failed -> {
                        timeoutJob?.cancel()
                        WorkProfileProvisioningManager.reset()
                        hideLoader()
                        Toast.makeText(this@VirtualProfileActivity, state.reason, Toast.LENGTH_LONG).show()
                        checkProfileState()
                    }
                    is WorkProfileSetupState.Idle -> hideLoader()
                }
            }
        }
    }

    private fun checkProfileState() {
        val isAdmin = dpm.isAdminActive(adminComponent)
        val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        val prefCreated = prefs.getBoolean("profile_created", false)

        if (!isAdmin) {
            binding.cardPermission.visibility = View.VISIBLE
            binding.btnCreateProfile.visibility = View.GONE
            binding.btnAddApps.visibility = View.GONE
            binding.btnMenu.visibility = View.GONE
            binding.recyclerApps.visibility = View.GONE
            binding.layoutLoader.visibility = View.GONE
            binding.tvStatus.text = "Please allow Device Admin permission first."
            return
        }

        binding.cardPermission.visibility = View.GONE

        // 🟢 چیک کریں کہ پروفائل موجود ہے یا نہیں
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val myHandle = Process.myUserHandle()
        var hasProfile = launcherApps.profiles.any { it != myHandle }

        if (!hasProfile) {
            val userManager = getSystemService(Context.USER_SERVICE) as UserManager
            hasProfile = userManager.userProfiles.any { it != myHandle }
        }

        if (hasProfile || prefCreated) {
            // Handle ڈھونڈیں
            if (workProfileUser == null) {
                workProfileUser = launcherApps.profiles.firstOrNull { it != myHandle }
            }
            
            if (workProfileUser != null) {
                showManagerState()
            } else {
                // اگر پروفائل بن گئی ہے لیکن Handle نہیں ملا، پھر بھی UI مینیجر والا دکھائیں
                showManagerState() 
            }
        } else {
            showCreateState()
        }
    }

    private fun showCreateState() {
        binding.tvStatus.text = "Device Admin is active ✅\nNow you can create Virtual Android."
        binding.btnCreateProfile.visibility = View.VISIBLE
        binding.btnAddApps.visibility = View.GONE
        binding.btnMenu.visibility = View.GONE
        binding.recyclerApps.visibility = View.GONE
        binding.layoutLoader.visibility = View.GONE

        binding.btnCreateProfile.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
            if (intent.resolveActivity(packageManager) != null) {
                WorkProfileProvisioningManager.markStarted()
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
        binding.btnMenu.visibility = View.VISIBLE
        binding.recyclerApps.visibility = View.VISIBLE
        binding.layoutLoader.visibility = View.GONE

        loadAppsFromWorkProfile()

        binding.btnAddApps.setOnClickListener {
            showAppPickerDialog()
        }
    }

    private fun showLoader() {
        binding.layoutLoader.visibility = View.VISIBLE
        binding.btnCreateProfile.visibility = View.GONE
        binding.btnAddApps.visibility = View.GONE
        binding.btnMenu.visibility = View.GONE
        binding.recyclerApps.visibility = View.GONE
        binding.tvStatus.text = "Setting up your Virtual Android...\nThis may take up to 90 seconds."

        // 🟢 ڈائنامک ریسیور رجسٹر کریں (ACTION_MANAGED_PROFILE_ADDED)
        val filter = IntentFilter(Intent.ACTION_MANAGED_PROFILE_ADDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(profileAddedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(profileAddedReceiver, filter)
        }

        // 🟢 90 سیکنڈ کا حقیقی ٹائم آؤٹ (جمینائی کی تجویز کے مطابق)
        timeoutJob?.cancel()
        timeoutJob = lifecycleScope.launch {
            delay(90_000)
            if (WorkProfileProvisioningManager.setupState.value is WorkProfileSetupState.InProgress) {
                WorkProfileProvisioningManager.markFailed("Setup timed out. Please try again or restart device.")
            }
        }
    }

    private fun hideLoader() {
        binding.layoutLoader.visibility = View.GONE
        try { unregisterReceiver(profileAddedReceiver) } catch (_: Exception) {}
    }

    // 🟢 تھری ڈاٹ مینو
    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(Menu.NONE, 1, 1, "Delete Virtual Android")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                showDeleteConfirmation()
                true
            } else false
        }
        popup.show()
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Virtual Android?")
            .setMessage("Ye poora virtual space aur iske andar ki saari apps delete ho jayengi. Kya aap sure hain?")
            .setPositiveButton("Delete") { _, _ ->
                val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("profile_created", false).apply()
                Toast.makeText(this, "Please restart your device to complete deletion.", Toast.LENGTH_LONG).show()
                checkProfileState()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
    // صرف وہ ایپس دکھائیں جو سسٹم نہیں ہیں (User-installed)
    val mainApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val userApps = mainApps.filter { 
        (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 
    }
    
    val appNames = userApps.map { it.loadLabel(pm).toString() }.toTypedArray()
    
    AlertDialog.Builder(this)
        .setTitle("Select App to Add to Virtual Android")
        .setItems(appNames) { _, which ->
            val selectedApp = userApps[which]
            val workUser = workProfileUser
            
            if (workUser != null) {
                Toast.makeText(this, "Installing ${selectedApp.loadLabel(pm)}...", Toast.LENGTH_SHORT).show()
                ApkSharer.shareApkToWorkProfile(this, selectedApp.packageName, workUser)
            } else {
                Toast.makeText(this, "Virtual Android not ready.", Toast.LENGTH_SHORT).show()
            }
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
            if (resultCode == RESULT_OK) {
                // سسٹم ونڈو بند ہو گئی، لوڈر دکھائیں اور اصل سگنل کا انتظار کریں
                showLoader()
            } else {
                WorkProfileProvisioningManager.markFailed("Provisioning cancelled by user.")
            }
        }
    }
}
