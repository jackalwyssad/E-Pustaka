package com.example.perpustakaan2.data.model

import com.google.firebase.firestore.DocumentSnapshot

data class Category(
    val id: String = "",
    val namaKategori: String = "",
    val iconEmoji: String = "📚"
) {
    val name: String get() = namaKategori
    val icon: String get() = iconEmoji

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): Category {
            val data = doc.data ?: return Category(id = doc.id)
            return Category(
                id = doc.id,
                namaKategori = data["namaKategori"]?.toString() ?: "",
                iconEmoji = data["iconEmoji"]?.toString()?.ifBlank { "📚" } ?: "📚"
            )
        }
    }
}
