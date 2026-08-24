package com.klarl.accessibility.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.klarl.accessibility.R
import com.klarl.accessibility.ScreenReaderAccessibilityService
import com.klarl.accessibility.ai.ClaudeConfig
import com.klarl.accessibility.databinding.ActivityMainBinding

/**
 * Status/settings screen. Shows whether the three prerequisites for the pipeline to work are
 * met (accessibility service enabled, mic permission granted, Claude API key configured) and
 * lets the user jump to system settings to fix whichever isn't - per the spec's "minimal UI for
 * inställningar/status".
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestMicPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openAccessibilitySettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.requestMicPermissionButton.setOnClickListener {
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        val prefs = SettingsStore(this)
        binding.readAloudSwitch.isChecked = prefs.readAloudAiResponses
        binding.readAloudSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.readAloudAiResponses = checked
        }
        binding.requireConfirmationSwitch.isChecked = prefs.requireConfirmationForSensitiveActions
        binding.requireConfirmationSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.requireConfirmationForSensitiveActions = checked
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val serviceEnabled = isAccessibilityServiceEnabled()
        binding.serviceStatusText.text = getString(
            if (serviceEnabled) R.string.status_service_enabled else R.string.status_service_disabled
        )

        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        binding.micPermissionStatusText.text = getString(
            if (micGranted) R.string.status_mic_permission_granted else R.string.status_mic_permission_missing
        )
        binding.requestMicPermissionButton.visibility =
            if (micGranted) android.view.View.GONE else android.view.View.VISIBLE

        binding.apiKeyStatusText.text = getString(
            if (ClaudeConfig.isConfigured) R.string.status_api_key_present else R.string.status_api_key_missing
        )
    }

    /** Checks Settings.Secure directly rather than AccessibilityManager, which only reports
     *  services that have already had at least one event delivered - Secure settings reflect
     *  the toggle state immediately. */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, ScreenReaderAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServicesSetting)
        while (splitter.hasNext()) {
            val enabledComponent = ComponentName.unflattenFromString(splitter.next())
            if (enabledComponent == expectedComponent) return true
        }
        return false
    }
}
