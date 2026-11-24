package com.example.perpustakaan2.util

object Constants {
    const val MASA_PINJAM_HARI = 7
    const val MASA_PERPANJANG_HARI = 3
    const val MASA_SESI_KILAT_JAM = 2
    const val BATAS_PINJAM_AKTIF = 3
    const val BATAS_KONFIRMASI_ANTRIAN_JAM = 2
    const val AUTO_RECALL_JAM = 48
    const val PERINGATAN_RECALL_JAM = 24
    const val PERSENTASE_CUPLIKAN = 0.20
    const val MULTIPLIER_ANTRIAN_UNTUK_TAMBAH_STOK = 2
    const val PEMBAGI_STOK_DINAMIS = 3
    const val COLLECTION_USERS = "users"
    const val COLLECTION_BOOKS = "books"
    const val COLLECTION_CATEGORIES = "categories"
    const val COLLECTION_LOANS = "loans"
    const val COLLECTION_QUEUE = "queue"
    const val COLLECTION_FAVORITES = "favorites"
    const val COLLECTION_REVIEWS = "reviews"
    const val STATUS_AKTIF = "aktif"
    const val STATUS_DIKEMBALIKAN = "dikembalikan"
    const val STATUS_DIPERPANJANG = "diperpanjang"
    const val STATUS_AUTO_RECALL = "auto-recall"
    const val STATUS_MENUNGGU = "menunggu"
    const val STATUS_TERSEDIA = "tersedia"
    const val STATUS_EXPIRED = "expired"
    const val TIPE_PINJAM = "pinjam"
    const val TIPE_KILAT = "kilat"
    const val ROLE_MEMBER = "member"
    const val ROLE_ADMIN = "admin"
    const val MILLIS_PER_HOUR = 3_600_000L
    const val MILLIS_PER_DAY = 86_400_000L

    fun translateError(e: Throwable?): String {
        if (e == null) return "Terjadi kesalahan yang tidak diketahui."
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("transactions require all reads to be executed before all writes", ignoreCase = true) ->
                "Gagal memproses transaksi basis data. Silakan coba kembali."
            msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("insufficient permissions", ignoreCase = true) ->
                "Akses ditolak oleh izin keamanan database. Pastikan koneksi dan akun Anda valid."
            msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("network error", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ->
                "Koneksi internet bermasalah. Pastikan perangkat Anda terhubung ke internet."
            msg.contains("Invalid document reference", ignoreCase = true) ->
                "Identitas dokumen buku tidak valid atau belum tersimpan."
            msg.contains("Task is not yet complete", ignoreCase = true) ->
                "Operasi masih berjalan di latar belakang. Silakan coba kembali sesaat lagi."
            msg.contains("already in use", ignoreCase = true) ->
                "Alamat email ini sudah digunakan oleh akun lain."
            msg.contains("badly formatted", ignoreCase = true) ->
                "Format alamat email tidak valid."
            msg.contains("wrong password", ignoreCase = true) || msg.contains("invalid credential", ignoreCase = true) ->
                "Email atau kata sandi yang Anda masukkan salah."
            msg.contains("user not found", ignoreCase = true) ->
                "Akun dengan email tersebut tidak ditemukan."
            msg.contains("user-disabled", ignoreCase = true) ->
                "Akun ini telah dinonaktifkan."
            msg.contains("requires Blaze", ignoreCase = true) ->
                "Penyimpanan awan memerlukan aktivasi paket berbayar."
            msg.isNotBlank() && !msg.startsWith("com.google") && !msg.startsWith("java.") ->
                msg
            else ->
                "Terjadi kendala sistem. Silakan coba kembali sesaat lagi."
        }
    }
}
