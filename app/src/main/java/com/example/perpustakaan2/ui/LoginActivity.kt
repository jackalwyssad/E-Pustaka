package com.example.perpustakaan2.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Harap isi email dan kata sandi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            lifecycleScope.launch {
                val result = authRepository.login(email, password)
                binding.btnLogin.isEnabled = true
                result.fold(
                    onSuccess = {
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@LoginActivity, "Gagal masuk: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.btnQuickAdmin.setOnClickListener {
            binding.etEmail.setText("admin@epustaka.com")
            binding.etPassword.setText("admin123")
            binding.btnLogin.performClick()
        }

        binding.btnGuest.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
