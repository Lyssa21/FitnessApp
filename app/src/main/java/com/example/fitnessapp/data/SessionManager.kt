package com.example.fitnessapp.data

import android.content.Context

class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences("fitness_session", Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = preferences.getBoolean("is_logged_in", false)

    val userId: Int
        get() = preferences.getInt("user_id", 0)

    val userName: String
        get() = preferences.getString("user_name", "Beautiful") ?: "Beautiful"

    val token: String
        get() = preferences.getString("session_token", "") ?: ""

    val email: String
        get() = preferences.getString("user_email", "") ?: ""

    fun saveLogin(userId: Int, name: String, token: String = "", email: String = "") {
        preferences.edit()
            .putBoolean("is_logged_in", true)
            .putInt("user_id", userId)
            .putString("user_name", name)
            .putString("session_token", token)
            .putString("user_email", email)
            .apply()
    }

    fun logout() {
        preferences.edit().clear().apply()
        com.example.fitnessapp.network.ApiClient.authToken = ""
    }

    fun updateName(name: String) {
        preferences.edit().putString("user_name", name).apply()
    }

    fun updateProfile(name: String, email: String) {
        preferences.edit().putString("user_name", name).putString("user_email", email).apply()
    }
}
