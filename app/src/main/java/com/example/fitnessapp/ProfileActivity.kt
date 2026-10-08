package com.example.fitnessapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.fitnessapp.data.SessionManager

class ProfileActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val session = SessionManager(this)
        findViewById<TextView>(R.id.profileNameTextView).text = session.userName
        findViewById<TextView>(R.id.profileEmailTextView).text = session.email
        findViewById<Button>(R.id.profileBackButton).setOnClickListener { finish() }
    }
}
