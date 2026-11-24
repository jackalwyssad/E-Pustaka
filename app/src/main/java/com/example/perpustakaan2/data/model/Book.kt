package com.example.perpustakaan2.data.model

import com.google.firebase.firestore.DocumentSnapshot

data class Book(
    val id: String = "",
    val judul: String = "",
    val penulis: String = "",
    val sinopsis: String = "",
    val kategoriId: String = "",
    val namaKategori: String = "",
    val urlSampul: String = "",
    val urlPdf: String = "",
    val jumlahHalaman: Int = 0,
    val stokAwal: Int = 5,
    val stokTersedia: Int = 5,
    val stokMaksimal: Int = 15,
    val stokSesiKilat: Int = 3,
    val jumlahPeminjam: Int = 0,
    val jumlahAntrian: Int = 0,
    val rataRating: Float = 0f,
    val jumlahRating: Int = 0,
    val tanggalUpload: Long = 0L
) {
    val title: String get() = judul
    val author: String get() = penulis
    val synopsis: String get() = sinopsis
    val category: String get() = namaKategori
    val coverUrl: String get() = urlSampul
    val pdfUrl: String get() = urlPdf
    val totalPages: Int get() = jumlahHalaman
    val availableStock: Int get() = stokTersedia
    val totalStock: Int get() = stokMaksimal
    val quickReadSlots: Int get() = stokSesiKilat
    val borrowCount: Int get() = jumlahPeminjam
    val queueCount: Int get() = jumlahAntrian
    val rating: Float get() = rataRating
    val ratingCount: Int get() = jumlahRating

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): Book {
            val data = doc.data ?: return Book(id = doc.id)

            fun toIntSafe(value: Any?, default: Int): Int {
                return when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull() ?: default
                    else -> default
                }
            }

            fun toFloatSafe(value: Any?, default: Float): Float {
                return when (value) {
                    is Number -> value.toFloat()
                    is String -> value.toFloatOrNull() ?: default
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

            return Book(
                id = doc.id,
                judul = data["judul"]?.toString() ?: "",
                penulis = data["penulis"]?.toString() ?: "",
                sinopsis = data["sinopsis"]?.toString() ?: "",
                kategoriId = data["kategoriId"]?.toString() ?: "",
                namaKategori = data["namaKategori"]?.toString() ?: "",
                urlSampul = data["urlSampul"]?.toString() ?: "",
                urlPdf = data["urlPdf"]?.toString() ?: "",
                jumlahHalaman = toIntSafe(data["jumlahHalaman"], 100),
                stokAwal = toIntSafe(data["stokAwal"], 5),
                stokTersedia = toIntSafe(data["stokTersedia"], 5),
                stokMaksimal = toIntSafe(data["stokMaksimal"], 15),
                stokSesiKilat = toIntSafe(data["stokSesiKilat"], 3),
                jumlahPeminjam = toIntSafe(data["jumlahPeminjam"], 0),
                jumlahAntrian = toIntSafe(data["jumlahAntrian"], 0),
                rataRating = toFloatSafe(data["rataRating"], 0f),
                jumlahRating = toIntSafe(data["jumlahRating"], 0),
                tanggalUpload = toLongSafe(data["tanggalUpload"], System.currentTimeMillis())
            )
        }
    }
}
