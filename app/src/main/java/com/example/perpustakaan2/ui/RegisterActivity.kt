package com.example.perpustakaan2.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etNama.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirm = binding.etPasswordConfirm.text.toString().trim()
            val agreed = binding.cbAgreeTerms.isChecked

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Harap lengkapi semua kolom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Kata sandi minimal 6 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirm) {
                Toast.makeText(this, "Konfirmasi kata sandi tidak cocok", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!agreed) {
                Toast.makeText(this, "Harap setujui syarat & ketentuan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false
            lifecycleScope.launch {
                val result = authRepository.register(name, email, password)
                binding.btnRegister.isEnabled = true
                result.fold(
                    onSuccess = {
                        AlertDialog.Builder(this@RegisterActivity)
                            .setTitle("Pendaftaran Berhasil")
                            .setMessage("Akun Anda telah dibuat! Tautan verifikasi telah dikirimkan ke email Anda. Silakan masuk.")
                            .setPositiveButton("Ke Halaman Masuk") { _, _ ->
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@RegisterActivity, "Pendaftaran gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvLoginLink.setOnClickListener {
            finish()
        }
    }
}
