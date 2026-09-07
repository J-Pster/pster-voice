package com.joaopster.pstervoice

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.joaopster.pstervoice.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRequestPermissions.setOnClickListener { requestMissingPermissions() }
        binding.buttonOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.buttonOpenDictionary.setOnClickListener {
            startActivity(Intent(this, DictionaryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun refreshStatus() {
        val allGranted = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        binding.textPermissionsStatus.text = getString(
            if (allGranted) R.string.status_permissions_granted else R.string.status_permissions_missing
        )

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        binding.textAccessibilityStatus.text = getString(
            if (accessibilityEnabled) R.string.status_accessibility_enabled else R.string.status_accessibility_disabled
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, BubbleAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
