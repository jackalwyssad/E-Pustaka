package com.example.perpustakaan2.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.databinding.ActivityLoginBinding
import com.example.perpustakaan2.databinding.DialogForgotPasswordBinding
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

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
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

    private fun showForgotPasswordDialog() {
        val dialogBinding = DialogForgotPasswordBinding.inflate(layoutInflater)
        val currentEmailInput = binding.etEmail.text.toString().trim()
        if (currentEmailInput.isNotEmpty()) {
            dialogBinding.etForgotEmail.setText(currentEmailInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Kirim Link", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setOnClickListener {
                val email = dialogBinding.etForgotEmail.text.toString().trim()
                if (email.isEmpty()) {
                    dialogBinding.tilForgotEmail.error = "Masukkan alamat email Anda"
                    return@setOnClickListener
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    dialogBinding.tilForgotEmail.error = "Format email tidak valid"
                    return@setOnClickListener
                }
                dialogBinding.tilForgotEmail.error = null

                dialogBinding.pbForgotLoading.visibility = View.VISIBLE
                positiveBtn.isEnabled = false

                lifecycleScope.launch {
                    val result = authRepository.sendPasswordReset(email)
                    dialogBinding.pbForgotLoading.visibility = View.GONE
                    positiveBtn.isEnabled = true

                    result.fold(
                        onSuccess = { message ->
                            dialog.dismiss()
                            AlertDialog.Builder(this@LoginActivity)
                                .setTitle("Link Reset Terkirim")
                                .setMessage(message)
                                .setPositiveButton("Mengerti", null)
                                .show()
                        },
                        onFailure = { e ->
                            val errorMsg = e.localizedMessage ?: e.message ?: "Terjadi kesalahan"
                            dialogBinding.tilForgotEmail.error = errorMsg
                            Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }

        dialog.show()
    }
}
