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

    fun saveLogin(userId: Int, name: String) {
        preferences.edit()
            .putBoolean("is_logged_in", true)
            .putInt("user_id", userId)
            .putString("user_name", name)
            .apply()
    }

    fun logout() {
        preferences.edit().clear().apply()
    }
}
