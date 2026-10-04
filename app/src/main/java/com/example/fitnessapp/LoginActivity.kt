package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.network.ApiClient

class LoginActivity : AppCompatActivity() {
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        if (session.isLoggedIn) {
            openMainScreen()
            return
        }

        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.emailEditText)
        val passwordInput = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.loginProgressBar)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter your email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true, loginButton, progressBar)
            ApiClient.post(
                "login.php",
                mapOf("email" to email, "password" to password)
            ) { result ->
                setLoading(false, loginButton, progressBar)
                if (result.success) {
                    session.saveLogin(
                        result.data.optInt("user_id"),
                        result.data.optString("name", "Beautiful")
                    )
                    openMainScreen()
                } else {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.openRegisterButton).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<Button>(R.id.offlineButton).setOnClickListener {
            session.saveLogin(0, "Beautiful")
            openMainScreen()
        }
    }

    private fun setLoading(loading: Boolean, button: Button, progressBar: ProgressBar) {
        button.isEnabled = !loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openMainScreen() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
