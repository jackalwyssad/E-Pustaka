package com.example.perpustakaan2.data.repository

import com.example.perpustakaan2.data.model.User
import com.example.perpustakaan2.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun register(nama: String, email: String, pass: String): Result<String> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user ?: throw Exception("Pendaftaran akun gagal dibuat.")

            // Kirim link verifikasi ke Gmail
            firebaseUser.sendEmailVerification().await()

            // Simpan profil awal di Firestore
            val user = User(
                id = firebaseUser.uid,
                nama = nama,
                email = email,
                role = Constants.ROLE_MEMBER,
                tanggalDaftar = System.currentTimeMillis()
            )
            firestore.collection(Constants.COLLECTION_USERS)
                .document(firebaseUser.uid)
                .set(user)
                .await()

            Result.success("Pendaftaran berhasil! Silakan periksa inbox/spam email $email untuk mengklik link verifikasi sebelum login.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<User> {
        // 1. Akun Master Admin Bawaan
        if (email.equals("admin@epustaka.com", ignoreCase = true) && pass == "admin123") {
            try {
                val current = auth.currentUser
                if (current == null || current.email != email) {
                    try {
                        auth.signInWithEmailAndPassword(email, pass).await()
                    } catch (_: Exception) {
                        try {
                            auth.createUserWithEmailAndPassword(email, pass).await()
                        } catch (_: Exception) {
                            try {
                                auth.signInAnonymously().await()
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}

            val adminId = auth.currentUser?.uid ?: "admin_master_epustaka"
            val masterAdmin = User(
                id = adminId,
                nama = "Pustakawan Admin",
                email = "admin@epustaka.com",
                role = Constants.ROLE_ADMIN,
                tanggalDaftar = System.currentTimeMillis()
            )
            try {
                firestore.collection(Constants.COLLECTION_USERS)
                    .document(adminId)
                    .set(masterAdmin)
                    .await()
            } catch (_: Exception) {}
            return Result.success(masterAdmin)
        }

        // 2. Login Akun Firebase Biasa
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user ?: throw Exception("Pengguna tidak ditemukan.")

            firebaseUser.reload().await()

            if (!firebaseUser.isEmailVerified) {
                try {
                    firebaseUser.sendEmailVerification()
                } catch (_: Exception) {}
                auth.signOut()
                throw Exception("Email Anda belum diverifikasi. Silakan buka Gmail Anda dan klik tautan verifikasi yang telah dikirimkan.")
            }

            val doc = firestore.collection(Constants.COLLECTION_USERS)
                .document(firebaseUser.uid)
                .get()
                .await()

            val user = if (doc.exists()) {
                User.fromSnapshot(doc)
            } else {
                User(id = firebaseUser.uid, nama = firebaseUser.displayName ?: "Pengguna", email = email)
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<String> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(Exception("Alamat email tidak boleh kosong."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(Exception("Format email tidak valid. Masukkan alamat email yang benar."))
        }

        return try {
            // 1. Cek apakah email terdaftar di Firestore collection "users"
            val firestoreDocs = firestore.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", trimmedEmail)
                .get()
                .await()

            val firestoreDocsLower = if (firestoreDocs.isEmpty) {
                firestore.collection(Constants.COLLECTION_USERS)
                    .whereEqualTo("email", trimmedEmail.lowercase())
                    .get()
                    .await()
            } else {
                firestoreDocs
            }

            // 2. Cek metode login di Firebase Auth
            val signInMethods = try {
                auth.fetchSignInMethodsForEmail(trimmedEmail).await().signInMethods
            } catch (_: Exception) {
                null
            }

            val isAdmin = trimmedEmail.equals("admin@epustaka.com", ignoreCase = true)
            val isRegistered = !firestoreDocsLower.isEmpty || !signInMethods.isNullOrEmpty() || isAdmin

            if (!isRegistered) {
                return Result.failure(Exception("Email belum terdaftar. Silakan daftar akun baru terlebih dahulu."))
            }

            // 3. Kirim link reset password ke email pengguna
            auth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success("Tautan untuk mengubah kata sandi telah dikirim ke $trimmedEmail. Silakan periksa inbox atau folder spam Gmail Anda.")
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: ""
            val friendlyMsg = when {
                msg.contains("no user record", ignoreCase = true) ||
                msg.contains("user-not-found", ignoreCase = true) ||
                msg.contains("belum terdaftar", ignoreCase = true) ->
                    "Email belum terdaftar. Silakan daftar akun baru terlebih dahulu."
                msg.contains("badly formatted", ignoreCase = true) ||
                msg.contains("invalid-email", ignoreCase = true) ->
                    "Format email tidak valid. Pastikan alamat email sudah benar."
                msg.contains("network error", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ->
                    "Gagal terhubung ke internet. Periksa koneksi data atau Wi-Fi Anda."
                else -> msg
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun resendVerificationEmail(): Result<String> {
        return try {
            val user = auth.currentUser ?: throw Exception("Pengguna belum login.")
            user.sendEmailVerification().await()
            Result.success("Email verifikasi telah dikirim ulang ke ${user.email}.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): Result<User> {
        if (userId == "admin_master_epustaka") {
            return Result.success(
                User(
                    id = "admin_master_epustaka",
                    nama = "Pustakawan Admin",
                    email = "admin@epustaka.com",
                    role = Constants.ROLE_ADMIN
                )
            )
        }
        return try {
            val doc = firestore.collection(Constants.COLLECTION_USERS).document(userId).get().await()
            val user = if (doc.exists()) User.fromSnapshot(doc) else throw Exception("Data profil tidak ditemukan.")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfileName(userId: String, newName: String): Result<Unit> {
        return try {
            if (userId != "admin_master_epustaka") {
                firestore.collection(Constants.COLLECTION_USERS).document(userId)
                    .update("nama", newName).await()
            }

            try {
                val userReviews = firestore.collection(Constants.COLLECTION_REVIEWS)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (doc in userReviews.documents) {
                    doc.reference.update("namaUser", newName).await()
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal memperbarui nama profil: ${Constants.translateError(e)}"))
        }
    }

    suspend fun updateProfilePhoto(userId: String, photoUrl: String): Result<Unit> {
        return try {
            if (userId != "admin_master_epustaka") {
                firestore.collection(Constants.COLLECTION_USERS).document(userId)
                    .update("fotoProfil", photoUrl).await()
            }

            try {
                val userReviews = firestore.collection(Constants.COLLECTION_REVIEWS)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (doc in userReviews.documents) {
                    doc.reference.update("fotoProfil", photoUrl).await()
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal memperbarui foto profil: ${Constants.translateError(e)}"))
        }
    }

    fun logout() {
        auth.signOut()
    }
}
