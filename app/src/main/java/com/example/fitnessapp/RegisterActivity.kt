package com.example.fitnessapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.example.fitnessapp.network.ApiClient

class RegisterActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val nameInput = findViewById<EditText>(R.id.nameEditText)
        val emailInput = findViewById<EditText>(R.id.registerEmailEditText)
        val passwordInput = findViewById<EditText>(R.id.registerPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val progressBar = findViewById<ProgressBar>(R.id.registerProgressBar)

        registerButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            when {
                name.isBlank() || email.isBlank() || password.isBlank() -> {
                    Toast.makeText(this, "Please complete every field", Toast.LENGTH_SHORT).show()
                }
                password.length < 6 -> {
                    Toast.makeText(this, "Password must contain at least 6 characters", Toast.LENGTH_SHORT).show()
                }
                else -> register(name, email, password, registerButton, progressBar)
            }
        }

        findViewById<Button>(R.id.backToLoginButton).setOnClickListener { finish() }
    }

    private fun register(
        name: String,
        email: String,
        password: String,
        button: Button,
        progressBar: ProgressBar
    ) {
        button.isEnabled = false
        progressBar.visibility = View.VISIBLE

        ApiClient.post(
            "register.php",
            mapOf("name" to name, "email" to email, "password" to password)
        ) { result ->
            button.isEnabled = true
            progressBar.visibility = View.GONE
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (result.success) finish()
        }
    }
}
