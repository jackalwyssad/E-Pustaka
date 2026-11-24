package com.example.perpustakaan2.data.model

import com.google.firebase.firestore.DocumentSnapshot

data class QueueEntry(
    val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val judulBuku: String = "",
    val urlSampul: String = "",
    val nomorUrut: Int = 0,
    val tanggalDaftar: Long = System.currentTimeMillis(),
    val status: String = "menunggu"
) {
    val bookTitle: String get() = judulBuku
    val bookCoverUrl: String get() = urlSampul
    val position: Int get() = nomorUrut

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): QueueEntry {
            val data = doc.data ?: return QueueEntry(id = doc.id)
            val nomor = when (val n = data["nomorUrut"]) {
                is Number -> n.toInt()
                is String -> n.toIntOrNull() ?: 1
                else -> 1
            }
            val tgl = when (val t = data["tanggalDaftar"]) {
                is Number -> t.toLong()
                is String -> t.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
            return QueueEntry(
                id = doc.id,
                userId = data["userId"]?.toString() ?: "",
                bookId = data["bookId"]?.toString() ?: "",
                judulBuku = data["judulBuku"]?.toString() ?: "",
                urlSampul = data["urlSampul"]?.toString() ?: "",
                nomorUrut = nomor,
                tanggalDaftar = tgl,
                status = data["status"]?.toString() ?: "menunggu"
            )
        }
    }
}
