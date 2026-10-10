package com.example.fitnessapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.fitnessapp.data.SessionManager

class ProfileActivity : BaseActivity() {
    private lateinit var session: SessionManager
    private lateinit var nameText: TextView
    private lateinit var themeText: TextView
    private lateinit var notificationText: TextView
    private val notificationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("profile_preferences", MODE_PRIVATE).edit().putBoolean("notifications", granted).apply()
        refreshHeader()
        Toast.makeText(this, if (granted) "Notifications enabled" else "Notifications need phone permission", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        session = SessionManager(this)
        nameText = findViewById(R.id.profileNameTextView)
        themeText = findViewById(R.id.themeValueTextView)
        notificationText = findViewById(R.id.notificationValueTextView)
        findViewById<TextView>(R.id.profileEmailTextView).text = session.email
        refreshHeader()

        findViewById<TextView>(R.id.profileBackButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.notificationsRow).setOnClickListener { toggleNotifications() }
        findViewById<TextView>(R.id.securityRow).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Security")
                .setMessage("Your account uses a secure session token. Log out on shared devices and keep your password private.")
                .setPositiveButton("OK", null).show()
        }
        findViewById<TextView>(R.id.themeRow).setOnClickListener { toggleTheme() }
        findViewById<TextView>(R.id.helpRow).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Help & Support")
                .setMessage("For tracking help, allow location access, keep GPS on, and start a workout outdoors.")
                .setPositiveButton("OK", null).show()
        }
        findViewById<TextView>(R.id.privacyRow).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Privacy policy")
                .setMessage("BlushFit uses your account, workout, location, and route data to provide fitness tracking. Route data is saved with your workout when you choose to save it.")
                .setPositiveButton("OK", null).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::nameText.isInitialized) refreshHeader()
    }

    private fun refreshHeader() {
        nameText.text = session.userName
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        val notifications = when {
            !prefs.getBoolean("notifications", true) -> "OFF"
            !ProgressNotifier.canNotify(this) -> "PHONE BLOCKED"
            else -> "ON"
        }
        val theme = if (prefs.getBoolean("dark_theme", false)) "Dark mode" else "Light mode"
        notificationText.text = notifications
        themeText.text = theme
        findViewById<TextView>(R.id.notificationsRow).text = "♧   Notifications  ·  $notifications"
        findViewById<TextView>(R.id.themeRow).text = "◉   Theme                                      $theme"
    }

    private fun toggleNotifications() {
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        if (prefs.getBoolean("notifications", true)) {
            if (!ProgressNotifier.canNotify(this)) {
                requestPermissionOrOpenSettings()
                return
            }
            prefs.edit().putBoolean("notifications", false).apply()
            refreshHeader()
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!ProgressNotifier.canNotify(this)) {
            openPhoneNotificationSettings()
            return
        }
        prefs.edit().putBoolean("notifications", true).apply()
        refreshHeader()
        Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
    }

    private fun openPhoneNotificationSettings() {
        val settings = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        startActivity(settings)
    }

    private fun requestPermissionOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openPhoneNotificationSettings()
        }
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        val dark = !prefs.getBoolean("dark_theme", false)
        prefs.edit().putBoolean("dark_theme", dark).apply()
        AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        refreshHeader()
    }

}
