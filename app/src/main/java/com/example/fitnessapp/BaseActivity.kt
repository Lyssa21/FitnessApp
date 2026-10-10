package com.example.fitnessapp

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/** Shared base for screens; demonstrates inheritance and centralises common UI behavior. */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val dark = getSharedPreferences("profile_preferences", MODE_PRIVATE).getBoolean("dark_theme", false)
        AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
    }
}
