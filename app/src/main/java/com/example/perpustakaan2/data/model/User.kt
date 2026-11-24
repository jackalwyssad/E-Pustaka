package com.example.perpustakaan2.data.model

import com.google.firebase.firestore.DocumentSnapshot

data class User(
    val id: String = "",
    val nama: String = "",
    val email: String = "",
    val role: String = "member",
    val fotoProfil: String? = null,
    val tanggalDaftar: Long = System.currentTimeMillis(),
    val totalBukuDibaca: Int = 0,
    val totalHalamanDibaca: Int = 0,
    val streakHari: Int = 0,
    val terakhirBaca: Long = 0L
) {
    val displayName: String get() = nama
    val photoUrl: String get() = fotoProfil ?: ""
    val booksCompleted: Int get() = totalBukuDibaca
    val totalPagesRead: Int get() = totalHalamanDibaca
    val streakDays: Int get() = streakHari

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): User {
            val data = doc.data ?: return User(id = doc.id)

            fun toIntSafe(value: Any?, default: Int): Int {
                return when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull() ?: default
                    else -> default
                }
            }

            fun toLongSafe(value: Any?, default: Long): Long {
                return when (value) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull() ?: default
                    else -> default
                }
            }

            return User(
                id = doc.id,
                nama = data["nama"]?.toString() ?: "",
                email = data["email"]?.toString() ?: "",
                role = data["role"]?.toString() ?: "member",
                fotoProfil = data["fotoProfil"]?.toString(),
                tanggalDaftar = toLongSafe(data["tanggalDaftar"], System.currentTimeMillis()),
                totalBukuDibaca = toIntSafe(data["totalBukuDibaca"], 0),
                totalHalamanDibaca = toIntSafe(data["totalHalamanDibaca"], 0),
                streakHari = toIntSafe(data["streakHari"], 0),
                terakhirBaca = toLongSafe(data["terakhirBaca"], 0L)
            )
        }
    }
}
