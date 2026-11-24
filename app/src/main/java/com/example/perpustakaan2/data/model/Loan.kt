package com.example.perpustakaan2.data.model

import com.google.firebase.firestore.DocumentSnapshot

data class Loan(
    val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val judulBuku: String = "",
    val urlSampul: String = "",
    val tipe: String = "pinjam",
    val tanggalPinjam: Long = System.currentTimeMillis(),
    val tanggalJatuhTempo: Long = 0L,
    val terakhirDibaca: Long = System.currentTimeMillis(),
    val halamanTerakhir: Int = 1,
    val jumlahHalaman: Int = 0,
    val status: String = "aktif",
    val urlPdf: String = ""
) {
    val bookTitle: String get() = judulBuku
    val bookCoverUrl: String get() = urlSampul
    val pdfUrl: String get() = urlPdf
    val currentPage: Int get() = halamanTerakhir
    val totalPages: Int get() = jumlahHalaman

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): Loan {
            val data = doc.data ?: return Loan(id = doc.id)

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

            return Loan(
                id = doc.id,
                userId = data["userId"]?.toString() ?: "",
                bookId = data["bookId"]?.toString() ?: "",
                judulBuku = data["judulBuku"]?.toString() ?: "",
                urlSampul = data["urlSampul"]?.toString() ?: "",
                tipe = data["tipe"]?.toString() ?: "pinjam",
                tanggalPinjam = toLongSafe(data["tanggalPinjam"], System.currentTimeMillis()),
                tanggalJatuhTempo = toLongSafe(data["tanggalJatuhTempo"], 0L),
                terakhirDibaca = toLongSafe(data["terakhirDibaca"], System.currentTimeMillis()),
                halamanTerakhir = toIntSafe(data["halamanTerakhir"], 1),
                jumlahHalaman = toIntSafe(data["jumlahHalaman"], 0),
                status = data["status"]?.toString() ?: "aktif",
                urlPdf = data["urlPdf"]?.toString() ?: ""
            )
        }
    }
}
