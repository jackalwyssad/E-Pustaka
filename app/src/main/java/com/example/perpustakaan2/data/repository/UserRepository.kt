package com.example.perpustakaan2.data.repository

import com.example.perpustakaan2.data.model.User
import com.example.perpustakaan2.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usersCollection = firestore.collection(Constants.COLLECTION_USERS)

    fun getAllUsersFlow(): Flow<List<User>> = callbackFlow {
        val listener = usersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                User.fromSnapshot(doc)
            } ?: emptyList()
            trySend(list.sortedByDescending { it.tanggalDaftar })
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateUserRole(userId: String, newRole: String): Result<Unit> {
        return try {
            usersCollection.document(userId).update("role", newRole).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mengubah peran pengguna: ${e.localizedMessage ?: e.message}"))
        }
    }
}
