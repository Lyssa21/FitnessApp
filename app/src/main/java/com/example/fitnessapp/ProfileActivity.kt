package com.example.fitnessapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.example.fitnessapp.data.SessionManager

class ProfileActivity : BaseActivity() {
    private lateinit var session: SessionManager
    private lateinit var nameText: TextView
    private lateinit var themeText: TextView
    private lateinit var notificationText: TextView

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
        findViewById<TextView>(R.id.editProfileRow).setOnClickListener { editProfile() }
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
        findViewById<TextView>(R.id.contactRow).setOnClickListener { contactSupport() }
        findViewById<TextView>(R.id.privacyRow).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Privacy policy")
                .setMessage("BlushFit uses your account, workout, location, and route data to provide fitness tracking. Route data is saved with your workout when you choose to save it.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun refreshHeader() {
        nameText.text = session.userName
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        notificationText.text = if (prefs.getBoolean("notifications", true)) "ON" else "OFF"
        themeText.text = if (prefs.getBoolean("dark_theme", false)) "Dark mode" else "Light mode"
    }

    private fun editProfile() {
        val input = EditText(this).apply { setText(session.userName); hint = "Your name"; setPadding(48, 8, 48, 0) }
        AlertDialog.Builder(this).setTitle("Edit profile information").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) { session.updateName(name); refreshHeader(); Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show() }
            }.show()
    }

    private fun toggleNotifications() {
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        val enabled = !prefs.getBoolean("notifications", true)
        prefs.edit().putBoolean("notifications", enabled).apply()
        refreshHeader()
        Toast.makeText(this, if (enabled) "Notifications enabled" else "Notifications disabled", Toast.LENGTH_SHORT).show()
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        val dark = !prefs.getBoolean("dark_theme", false)
        prefs.edit().putBoolean("dark_theme", dark).apply()
        AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        refreshHeader()
    }

    private fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "BlushFit support")
        }
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else Toast.makeText(this, "No email app is installed", Toast.LENGTH_SHORT).show()
    }
}
