package com.example.fitnessapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.network.ApiClient

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
        findViewById<TextView>(R.id.privacyRow).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Privacy policy")
                .setMessage("BlushFit uses your account, workout, location, and route data to provide fitness tracking. Route data is saved with your workout when you choose to save it.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun refreshHeader() {
        nameText.text = session.userName
        val prefs = getSharedPreferences("profile_preferences", MODE_PRIVATE)
        val notifications = if (prefs.getBoolean("notifications", true)) "ON" else "OFF"
        val theme = if (prefs.getBoolean("dark_theme", false)) "Dark mode" else "Light mode"
        notificationText.text = notifications
        themeText.text = theme
        findViewById<TextView>(R.id.notificationsRow).text = "♧   Notifications                              $notifications"
        findViewById<TextView>(R.id.themeRow).text = "◉   Theme                                      $theme"
    }

    private fun editProfile() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 0, 42, 0) }
        val name = EditText(this).apply { setText(session.userName); hint = "Name" }
        val email = EditText(this).apply { setText(session.email); hint = "Email"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        box.addView(name); box.addView(email)
        AlertDialog.Builder(this).setTitle("Edit profile information").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create().also { dialog ->
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newName = name.text.toString().trim(); val newEmail = email.text.toString().trim()
                    if (newName.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) { Toast.makeText(this, "Enter a valid name and email", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    ApiClient.post("update_profile.php", mapOf("name" to newName, "email" to newEmail)) { result ->
                        if (result.success) { session.updateProfile(newName, newEmail); findViewById<TextView>(R.id.profileEmailTextView).text = newEmail; refreshHeader(); dialog.dismiss() }
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                } }
                dialog.show()
            }
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

}
