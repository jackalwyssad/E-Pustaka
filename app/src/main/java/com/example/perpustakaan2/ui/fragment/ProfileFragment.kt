package com.example.perpustakaan2.ui.fragment

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
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
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private var currentUserData: User? = null

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            handlePickedAvatar(it)
        }
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
            pickAvatarLauncher.launch(arrayOf("image/*"))
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
        val ctx = context ?: return

        binding.btnChangeAvatar.isEnabled = false
        Toast.makeText(ctx, "Mengunggah foto profil...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val localFile = File(ctx.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
            try {
                withContext(Dispatchers.IO) {
                    compressAvatarImage(ctx, uri, localFile)
                }

                val uploadRes = CloudUploader.uploadFile(localFile, "image/jpeg")
                var firebaseErr: String? = null
                val finalUrl = uploadRes.getOrElse {
                    try {
                        val fileName = "avatar_${uid}_${UUID.randomUUID()}.jpg"
                        val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$fileName")
                        storageRef.putFile(Uri.fromFile(localFile)).await()
                        storageRef.downloadUrl.await().toString()
                    } catch (fbEx: Exception) {
                        firebaseErr = fbEx.message ?: fbEx.javaClass.simpleName
                        null
                    }
                }

                if (!isAdded || _binding == null) return@launch

                if (finalUrl != null && (finalUrl.startsWith("http://") || finalUrl.startsWith("https://"))) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(finalUrl).build()
                    )
                    val updateRes = authRepository.updateProfilePhoto(uid, finalUrl)
                    if (!isAdded || _binding == null) return@launch
                    updateRes.fold(
                        onSuccess = {
                            binding.ivAvatar.load(finalUrl) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                                placeholder(R.drawable.ic_person)
                                error(R.drawable.ic_person)
                            }
                            Toast.makeText(ctx, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                            observeUserData()
                        },
                        onFailure = { e ->
                            Toast.makeText(ctx, "Gagal menyimpan profil: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    val cloudErr = uploadRes.exceptionOrNull()?.message ?: "Tidak diketahui"
                    val fbDetail = firebaseErr ?: "Tidak tersedia"
                    AlertDialog.Builder(ctx)
                        .setTitle("Gagal Mengunggah Foto Profil")
                        .setMessage("Foto tidak dapat diunggah ke Cloud.\n\n• Cloud: $cloudErr\n• Firebase Storage: $fbDetail\n\nPeriksa koneksi internet Anda lalu coba lagi.")
                        .setPositiveButton("Mengerti", null)
                        .show()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(ctx, "Gagal memproses foto: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                localFile.delete()
                if (_binding != null) {
                    binding.btnChangeAvatar.isEnabled = true
                }
            }
        }
    }

    private fun copyUriToFile(ctx: android.content.Context, uri: Uri, targetFile: File) {
        val inputStream = ctx.contentResolver.openInputStream(uri)
            ?: throw IOException("Tidak dapat membuka foto dari penyedia file (InputStream null). Coba pilih foto dari penyimpanan lokal.")
        var totalBytes = 0L
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalBytes += read
                }
                output.flush()
            }
        }
        if (totalBytes <= 0L || !targetFile.exists() || targetFile.length() <= 0L) {
            targetFile.delete()
            throw IOException("Berkas foto yang dipilih kosong (0 byte).")
        }
    }

    private fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim * 2 || boundsOpts.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        return try {
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun compressAvatarImage(ctx: android.content.Context, uri: Uri, targetFile: File) {
        val rawTemp = File(ctx.cacheDir, "raw_avatar_${System.currentTimeMillis()}.tmp")
        try {
            copyUriToFile(ctx, uri, rawTemp)

            val orientation = try {
                ExifInterface(rawTemp.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val maxDim = 400
            var bitmap = decodeSampledBitmap(rawTemp, maxDim)
            if (bitmap != null) {
                bitmap = applyExifOrientation(bitmap, orientation)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(rawTemp)
                bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    if (w > maxDim || h > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                        decoder.setTargetSize(
                            (w * ratio).toInt().coerceAtLeast(1),
                            (h * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }

            val decoded = bitmap
                ?: throw IOException("Format gambar tidak didukung atau berkas rusak. Gunakan JPG, PNG, atau WebP.")

            val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
            val scaled = if (ratio < 1f) {
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            try {
                FileOutputStream(targetFile).use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)) {
                        throw IOException("Gagal mengompresi gambar ke JPEG.")
                    }
                }
            } finally {
                scaled.recycle()
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                throw IOException("Hasil kompresi foto kosong.")
            }
        } finally {
            rawTemp.delete()
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
