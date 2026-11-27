package com.example.perpustakaan2.ui.fragment

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.User
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.databinding.FragmentProfileBinding
import com.example.perpustakaan2.ui.AdminDashboardActivity
import com.example.perpustakaan2.ui.FavoritesActivity
import com.example.perpustakaan2.ui.LoginActivity
import com.example.perpustakaan2.ui.ReadingHistoryActivity
import com.example.perpustakaan2.util.CloudUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private var currentUserData: User? = null

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handlePickedAvatar(it) }
    }

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
        val openGallery = View.OnClickListener {
            if (authRepository.currentUser == null) {
                Toast.makeText(requireContext(), "Silakan masuk akun untuk mengubah foto profil", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            pickAvatarLauncher.launch("image/*")
        }

        binding.btnChangeAvatar.setOnClickListener(openGallery)
        binding.ivAvatar.setOnClickListener(openGallery)

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

    private fun handlePickedAvatar(uri: Uri) {
        val uid = authRepository.currentUser?.uid ?: return
        val ctx = requireContext()

        binding.btnChangeAvatar.isEnabled = false
        Toast.makeText(ctx, "Mengunggah foto profil...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val localFile = File(ctx.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    compressAvatarImage(uri, localFile)
                }

                // Tampilkan langsung foto baru secara instan
                if (_binding != null) {
                    binding.ivAvatar.load(localFile) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                    }
                }

                // Unggah ke Cloud gratis agar tampil di semua perangkat & ulasan
                val uploadRes = CloudUploader.uploadFile(localFile, "image/jpeg")
                uploadRes.fold(
                    onSuccess = { finalUrl ->
                        ctx.imageLoader.enqueue(
                            ImageRequest.Builder(ctx).data(finalUrl).build()
                        )
                        val updateRes = authRepository.updateProfilePhoto(uid, finalUrl)
                        updateRes.fold(
                            onSuccess = {
                                Toast.makeText(ctx, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                observeUserData()
                            },
                            onFailure = { e ->
                                Toast.makeText(ctx, "Gagal menyimpan profil: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onFailure = { e ->
                        Toast.makeText(ctx, "Gagal mengunggah foto: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(ctx, "Gagal memproses foto: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) {
                    binding.btnChangeAvatar.isEnabled = true
                }
            }
        }
    }

    private fun compressAvatarImage(uri: Uri, targetFile: File) {
        val resolver = requireContext().contentResolver
        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

            val maxDim = 400
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim * 2 || boundsOpts.outHeight / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }

            if (decoded != null) {
                val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                val scaled = if (ratio < 1f) {
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }
                FileOutputStream(targetFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                scaled.recycle()
                return
            }
        } catch (_: Exception) {}

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output -> input.copyTo(output) }
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
