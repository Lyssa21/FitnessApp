package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Startup screen that gives the app a predictable entry point before authentication. */
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 700)
    }
}
