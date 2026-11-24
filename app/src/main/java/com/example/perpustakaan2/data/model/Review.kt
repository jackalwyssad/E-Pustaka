package com.example.perpustakaan2.data.model

import com.google.firebase.firestore.DocumentSnapshot

data class Review(
    val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val namaUser: String = "",
    val fotoProfil: String? = null,
    val rating: Int = 0,
    val ulasan: String = "",
    val tanggal: Long = System.currentTimeMillis()
) {
    val userName: String get() = namaUser
    val userPhotoUrl: String get() = fotoProfil ?: ""
    val comment: String get() = ulasan
    val createdAt: Long get() = tanggal

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): Review {
            val data = doc.data ?: return Review(id = doc.id)

            val ratingVal = when (val r = data["rating"]) {
                is Number -> r.toInt()
                is String -> r.toIntOrNull() ?: 5
                else -> 5
            }

            val tanggalVal = when (val t = data["tanggal"]) {
                is Number -> t.toLong()
                is String -> t.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }

            return Review(
                id = doc.id,
                userId = data["userId"]?.toString() ?: "",
                bookId = data["bookId"]?.toString() ?: "",
                namaUser = data["namaUser"]?.toString() ?: "Anggota Perpustakaan",
                fotoProfil = data["fotoProfil"]?.toString(),
                rating = ratingVal,
                ulasan = data["ulasan"]?.toString() ?: "",
                tanggal = tanggalVal
            )
        }
    }
}
