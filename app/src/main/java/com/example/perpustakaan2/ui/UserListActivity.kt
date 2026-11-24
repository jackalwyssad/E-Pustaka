package com.example.perpustakaan2.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.UserAdapter
import com.example.perpustakaan2.data.model.User
import com.example.perpustakaan2.data.repository.UserRepository
import com.example.perpustakaan2.databinding.ActivityUserListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding
    private val userRepository = UserRepository()
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeUsers()
    }

    private fun setupToolbar() {
        binding.toolbarUsers.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter { user ->
            toggleUserRole(user)
        }
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@UserListActivity)
            this.adapter = this@UserListActivity.adapter
        }
    }

    private fun observeUsers() {
        lifecycleScope.launch {
            userRepository.getAllUsersFlow().collectLatest { users ->
                adapter.submitList(users)
                binding.tvEmptyUsers.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun toggleUserRole(user: User) {
        val newRole = if (user.role.equals("admin", ignoreCase = true)) "member" else "admin"
        val actionText = if (newRole == "admin") "menaikkan menjadi Admin" else "menurunkan menjadi Member"

        AlertDialog.Builder(this)
            .setTitle("Ubah Peran Anggota")
            .setMessage("Apakah Anda yakin ingin $actionText untuk ${user.displayName}?")
            .setPositiveButton("Ubah Peran") { _, _ ->
                lifecycleScope.launch {
                    val res = userRepository.updateUserRole(user.id, newRole)
                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@UserListActivity, "Peran berhasil diubah", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@UserListActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
