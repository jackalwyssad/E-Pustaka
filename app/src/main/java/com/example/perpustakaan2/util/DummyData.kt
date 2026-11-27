package com.example.perpustakaan2.util

import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.Category

object DummyData {

    val sampleCategories = listOf(
        Category(id = "cat_tech", namaKategori = "Teknologi", iconEmoji = ""),
        Category(id = "cat_science", namaKategori = "Sains & Alam", iconEmoji = ""),
        Category(id = "cat_literature", namaKategori = "Sastra & Fiksi", iconEmoji = ""),
        Category(id = "cat_business", namaKategori = "Bisnis & Karir", iconEmoji = "")
    )

    val sampleBooks = listOf(
        Book(
            id = "book_1",
            judul = "Pemrograman Android Modern dengan XML & Kotlin",
            penulis = "Google Android Team",
            sinopsis = "Panduan lengkap mempelajari pembuatan aplikasi Android dari nol menggunakan Kotlin dan Layout XML. Membahas UI, ViewBinding, RecyclerView, dan arsitektur MVVM.",
            kategoriId = "cat_tech",
            namaKategori = "Teknologi",
            urlSampul = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500",
            urlPdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
            jumlahHalaman = 120,
            stokAwal = 5,
            stokTersedia = 3,
            stokMaksimal = 15,
            stokSesiKilat = 3,
            jumlahPeminjam = 14,
            jumlahAntrian = 0,
            rataRating = 4.8f,
            jumlahRating = 18,
            tanggalUpload = System.currentTimeMillis() - 86400000L * 3
        ),
        Book(
            id = "book_2",
            judul = "Struktur Data & Algoritma untuk Pemula",
            penulis = "Robert Sedgewick",
            sinopsis = "Dasar-dasar logika algoritma, pencarian, pengurutan, array, tree, dan graph yang wajib dikuasai setiap programmer dan mahasiswa ilmu komputer.",
            kategoriId = "cat_tech",
            namaKategori = "Teknologi",
            urlSampul = "https://images.unsplash.com/photo-1532012164546-f432f2e3777a?w=500",
            urlPdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
            jumlahHalaman = 95,
            stokAwal = 4,
            stokTersedia = 0,
            stokMaksimal = 12,
            stokSesiKilat = 2,
            jumlahPeminjam = 25,
            jumlahAntrian = 4,
            rataRating = 4.7f,
            jumlahRating = 29,
            tanggalUpload = System.currentTimeMillis() - 86400000L * 5
        ),
        Book(
            id = "book_3",
            judul = "Kosmos: Menjelajah Misteri Alam Semesta",
            penulis = "Carl Sagan",
            sinopsis = "Perjalanan menakjubkan menjelajahi miliaran galaksi, asal-usul kehidupan, bintang-bintang, dan masa depan peradaban manusia di alam semesta yang luas.",
            kategoriId = "cat_science",
            namaKategori = "Sains & Alam",
            urlSampul = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500",
            urlPdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
            jumlahHalaman = 140,
            stokAwal = 6,
            stokTersedia = 4,
            stokMaksimal = 15,
            stokSesiKilat = 3,
            jumlahPeminjam = 19,
            jumlahAntrian = 1,
            rataRating = 4.9f,
            jumlahRating = 34,
            tanggalUpload = System.currentTimeMillis() - 86400000L * 2
        ),
        Book(
            id = "book_4",
            judul = "Filosofi Teras: Hidup Tenang & Bebas Cemas",
            penulis = "Henry Manampiring",
            sinopsis = "Penerapan filosofi Stoisisme kuno untuk menghadapi kekhawatiran zaman modern, mengatasi overthinking, dan melatih kendali pikiran agar hidup lebih tenang.",
            kategoriId = "cat_literature",
            namaKategori = "Sastra & Fiksi",
            urlSampul = "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=500",
            urlPdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
            jumlahHalaman = 80,
            stokAwal = 5,
            stokTersedia = 2,
            stokMaksimal = 15,
            stokSesiKilat = 3,
            jumlahPeminjam = 32,
            jumlahAntrian = 0,
            rataRating = 4.9f,
            jumlahRating = 45,
            tanggalUpload = System.currentTimeMillis() - 86400000L * 1
        ),
        Book(
            id = "book_5",
            judul = "Strategi Membangun Startup Digital",
            penulis = "Eric Ries",
            sinopsis = "Panduan praktis metodologi Lean Startup untuk merancang produk digital yang disukai pasar dengan iterasi cepat dan risiko seminimal mungkin.",
            kategoriId = "cat_business",
            namaKategori = "Bisnis & Karir",
            urlSampul = "https://images.unsplash.com/photo-1553729459-efe14ef6055d?w=500",
            urlPdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
            jumlahHalaman = 110,
            stokAwal = 3,
            stokTersedia = 1,
            stokMaksimal = 10,
            stokSesiKilat = 2,
            jumlahPeminjam = 11,
            jumlahAntrian = 0,
            rataRating = 4.6f,
            jumlahRating = 15,
            tanggalUpload = System.currentTimeMillis() - 86400000L * 4
        )
    )
}
