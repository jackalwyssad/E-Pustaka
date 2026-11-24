package com.example.perpustakaan2.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.User
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.databinding.FragmentProfileBinding
import com.example.perpustakaan2.ui.AdminDashboardActivity
import com.example.perpustakaan2.ui.FavoritesActivity
import com.example.perpustakaan2.ui.LoginActivity
import com.example.perpustakaan2.ui.ReadingHistoryActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private var currentUserData: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUserData()
    }

    private fun setupListeners() {
        binding.btnChangeAvatar.setOnClickListener {
            showChangeAvatarDialog()
        }

        binding.btnEditName.setOnClickListener {
            showChangeNameDialog()
        }

        binding.menuFavorites.setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }

        binding.menuHistory.setOnClickListener {
            startActivity(Intent(requireContext(), ReadingHistoryActivity::class.java))
        }

        binding.menuAdmin.setOnClickListener {
            startActivity(Intent(requireContext(), AdminDashboardActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun observeUserData() {
        val fbUser = authRepository.currentUser
        if (fbUser == null) {
            binding.tvProfileName.text = "Tamu"
            binding.tvProfileEmail.text = "Masuk untuk menyimpan riwayat"
            binding.tvRoleBadge.text = "TAMU"
            binding.menuAdmin.visibility = View.GONE
            binding.btnLogout.text = "Masuk / Buat Akun"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val res = authRepository.getUserProfile(fbUser.uid)
            res.fold(
                onSuccess = { user ->
                    currentUserData = user
                    val name = user.nama.ifBlank { fbUser.displayName } ?: "Pembaca"
                    val email = user.email.ifBlank { fbUser.email } ?: ""
                    val photoUrl = user.fotoProfil?.ifBlank { fbUser.photoUrl?.toString() } ?: ""
                    val isAdmin = user.role.equals("admin", ignoreCase = true)

                    binding.tvProfileName.text = name
                    binding.tvProfileEmail.text = email

                    if (photoUrl.isNotBlank()) {
                        binding.ivAvatar.load(photoUrl) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            placeholder(R.drawable.ic_person)
                            error(R.drawable.ic_person)
                        }
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.ic_person)
                    }

                    binding.tvRoleBadge.text = if (isAdmin) "ADMIN" else "MEMBER"
                    binding.tvRoleBadge.setBackgroundResource(
                        if (isAdmin) R.drawable.bg_badge_gold else R.drawable.bg_badge_primary
                    )
                    binding.menuAdmin.visibility = if (isAdmin) View.VISIBLE else View.GONE

                    binding.tvStreakStat.text = "${user.streakHari} Hari"
                    binding.tvPagesRead.text = "${user.totalHalamanDibaca}"
                    binding.tvBooksFinished.text = "${user.totalBukuDibaca} Buku"
                },
                onFailure = {
                    binding.tvProfileName.text = fbUser.displayName ?: "Pembaca"
                    binding.tvProfileEmail.text = fbUser.email ?: ""
                }
            )
        }
    }

    private fun showChangeNameDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Nama Lengkap Baru"
            setText(currentUserData?.nama ?: "")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Ubah Nama Profil")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    val uid = authRepository.currentUser?.uid ?: return@setPositiveButton
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = authRepository.updateProfileName(uid, newName)
                        result.fold(
                            onSuccess = {
                                Toast.makeText(requireContext(), "Nama berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                observeUserData()
                            },
                            onFailure = { e ->
                                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showChangeAvatarDialog() {
        val options = arrayOf(
            "Avatar Pustakawan (Pria)",
            "Avatar Pustakawan (Wanita)",
            "Avatar Kucing Pembaca",
            "Avatar Robot Pintar",
            "Masukkan URL Foto Kustom"
        )
        val urls = arrayOf(
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300",
            "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=300",
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=300"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Pilih Foto Profil Baru")
            .setItems(options) { _, which ->
                val uid = authRepository.currentUser?.uid ?: return@setItems
                if (which < 4) {
                    val selectedUrl = urls[which]
                    updateAvatarUrl(uid, selectedUrl)
                } else {
                    val input = EditText(requireContext()).apply {
                        hint = "https://example.com/avatar.jpg"
                    }
                    AlertDialog.Builder(requireContext())
                        .setTitle("URL Foto Profil")
                        .setView(input)
                        .setPositiveButton("Simpan") { _, _ ->
                            val url = input.text.toString().trim()
                            if (url.isNotBlank()) {
                                updateAvatarUrl(uid, url)
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            .show()
    }

    private fun updateAvatarUrl(uid: String, url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepository.updateProfilePhoto(uid, url)
            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    observeUserData()
                },
                onFailure = { e ->
                    Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Keluar Akun")
            .setMessage("Apakah Anda yakin ingin keluar?")
            .setPositiveButton("Keluar") { _, _ ->
                authRepository.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
