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

    fun saveLogin(userId: Int, name: String, token: String = "") {
        preferences.edit()
            .putBoolean("is_logged_in", true)
            .putInt("user_id", userId)
            .putString("user_name", name)
            .putString("session_token", token)
            .apply()
    }

    fun logout() {
        preferences.edit().clear().apply()
    }
}
