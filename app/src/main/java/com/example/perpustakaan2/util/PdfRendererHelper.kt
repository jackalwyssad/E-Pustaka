package com.example.perpustakaan2.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class PdfRendererHelper(private val context: Context) {

    companion object {
        fun getCacheFileForUrl(context: Context, url: String): File {
            val cacheFolder = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val md5 = try {
                val digest = MessageDigest.getInstance("MD5")
                val hashBytes = digest.digest(url.trim().toByteArray())
                hashBytes.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                url.hashCode().toString()
            }
            return File(cacheFolder, "$md5.pdf")
        }

        fun saveToCache(context: Context, url: String, sourceFile: File) {
            try {
                if (sourceFile.exists() && sourceFile.length() > 0) {
                    val target = getCacheFileForUrl(context, url)
                    sourceFile.copyTo(target, overwrite = true)
                }
            } catch (_: Exception) {}
        }

        fun countPagesInLocalFile(file: File): Int {
            if (!file.exists() || file.length() == 0L) return 0
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            return try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } catch (_: Exception) {
                0
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }
        }
    }

    private val renderLock = Any()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var activePdfFile: File? = null

    var pageCount: Int = 0
        private set

    suspend fun openPdfFromUrl(urlStr: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            close()

            val cleanUrl = urlStr.trim()
            if (cleanUrl.isBlank()) {
                throw IOException("Alamat/URL berkas PDF kosong.")
            }

            // 1. Jika sumber berkas adalah URI lokal perangkat (file://)
            if (cleanUrl.startsWith("file://")) {
                val cleanPath = cleanUrl.removePrefix("file://")
                val localFile = File(cleanPath)
                if (localFile.exists() && localFile.length() > 0) {
                    fileDescriptor = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    pageCount = pdfRenderer?.pageCount ?: 0
                    return@withContext Result.success(pageCount)
                } else {
                    // Berkas fisik hanya ada di perangkat pengunggah (belum disinkronkan ke Cloud)
                    val noticeFile = File.createTempFile("epustaka_local_notice_", ".pdf", context.cacheDir)
                    activePdfFile = noticeFile
                    createLocalDeviceNoticePdf(noticeFile)
                    fileDescriptor = ParcelFileDescriptor.open(noticeFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    pageCount = pdfRenderer?.pageCount ?: 1
                    return@withContext Result.success(pageCount)
                }
            }

            // 2. Jika sumber berkas adalah URI content provider Android (content://)
            if (cleanUrl.startsWith("content://")) {
                val uri = Uri.parse(cleanUrl)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    fileDescriptor = pfd
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    pageCount = pdfRenderer?.pageCount ?: 0
                    return@withContext Result.success(pageCount)
                } else {
                    throw IOException("Tidak dapat membuka file content://")
                }
            }

            // 3. Jika sumber adalah URL web (http:// atau https://)
            val directUrl = CloudUploader.convertToDirectDownloadUrl(cleanUrl)
            val cachedFile = getCacheFileForUrl(context, directUrl)

            // Cek apakah berkas sudah ada di cache lokal dan merupakan PDF valid
            if (cachedFile.exists() && cachedFile.length() > 0 && validatePdfHeader(cachedFile)) {
                activePdfFile = cachedFile
                fileDescriptor = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 0
                return@withContext Result.success(pageCount)
            }

            // Jika belum ada di cache, unduh menggunakan OkHttpClient
            val request = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .build()

            CloudUploader.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server mengembalikan kode HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IOException("Konten unduhan kosong")
                val tempDownload = File.createTempFile("epustaka_dl_", ".tmp", context.cacheDir)

                tempDownload.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }

                // Validasi format file PDF
                if (!validatePdfHeader(tempDownload)) {
                    tempDownload.delete()
                    throw IOException("Berkas yang diunduh bukan PDF yang valid. Jika menggunakan Google Drive, pastikan tautan berizin publik ('Siapa saja yang memiliki link').")
                }

                // Simpan ke cache permanen
                tempDownload.copyTo(cachedFile, overwrite = true)
                tempDownload.delete()
            }

            activePdfFile = cachedFile
            fileDescriptor = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            pageCount = pdfRenderer?.pageCount ?: 0

            Result.success(pageCount)
        } catch (e: Exception) {
            close()
            // Tampilkan halaman pemberitahuan error yang jelas kepada pengguna (BUKAN dummy PDF palsu)
            try {
                val errorFile = File.createTempFile("epustaka_err_", ".pdf", context.cacheDir)
                activePdfFile = errorFile
                createErrorNoticePdf(errorFile, urlStr, e.message ?: "Kesalahan tidak diketahui")
                fileDescriptor = ParcelFileDescriptor.open(errorFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 1
                Result.success(pageCount)
            } catch (_: Exception) {
                close()
                Result.failure(e)
            }
        }
    }

    private fun validatePdfHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 &&
                        header[0] == '%'.code.toByte() &&
                        header[1] == 'P'.code.toByte() &&
                        header[2] == 'D'.code.toByte() &&
                        header[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun createErrorNoticePdf(targetFile: File, url: String, errorMessage: String) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.rgb(211, 47, 47) // Red
            textSize = 20f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val paintSubtitle = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 14f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val paintBody = Paint().apply {
            color = Color.rgb(66, 66, 66)
            textSize = 12f
            isAntiAlias = true
        }

        val paintBox = Paint().apply {
            color = Color.rgb(255, 235, 238)
            style = Paint.Style.FILL
        }

        // Error Banner
        canvas.drawRect(30f, 40f, 565f, 110f, paintBox)
        canvas.drawText("⚠️ Gagal Memuat Dokumen PDF", 45f, 75f, paintTitle)
        canvas.drawText("Terjadi kendala saat mengunduh berkas buku digital ini.", 45f, 98f, paintBody)

        var y = 140f
        canvas.drawText("Rincian Kendala:", 40f, y, paintSubtitle)
        y += 24f

        val truncatedError = if (errorMessage.length > 90) errorMessage.take(87) + "..." else errorMessage
        canvas.drawText("• Penyebab: $truncatedError", 45f, y, paintBody)
        y += 20f

        val displayUrl = if (url.length > 75) url.take(72) + "..." else url
        canvas.drawText("• Tautan/URL: $displayUrl", 45f, y, paintBody)
        y += 35f

        canvas.drawText("Langkah Penyelesaian:", 40f, y, paintSubtitle)
        y += 24f
        canvas.drawText("1. Pastikan perangkat Anda terhubung ke internet yang stabil.", 45f, y, paintBody)
        y += 20f
        canvas.drawText("2. Jika menggunakan link Google Drive, pastikan izin file disetel ke:", 45f, y, paintBody)
        y += 18f
        canvas.drawText("   'Siapa saja yang memiliki link dapat melihat (Viewer)'.", 45f, y, paintBody)
        y += 20f
        canvas.drawText("3. Jika Anda adalah Admin, Anda dapat membuka menu Edit Buku", 45f, y, paintBody)
        y += 18f
        canvas.drawText("   dan mengunggah ulang file PDF melalui penyimpanan Cloud.", 45f, y, paintBody)

        document.finishPage(page)
        FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun createLocalDeviceNoticePdf(targetFile: File) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.rgb(230, 81, 0) // Amber / Orange
            textSize = 20f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val paintSubtitle = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 14f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val paintBody = Paint().apply {
            color = Color.rgb(66, 66, 66)
            textSize = 12f
            isAntiAlias = true
        }

        val paintBox = Paint().apply {
            color = Color.rgb(255, 243, 224)
            style = Paint.Style.FILL
        }

        canvas.drawRect(30f, 40f, 565f, 110f, paintBox)
        canvas.drawText("📁 Berkas Tersimpan Offline di HP Pengunggah", 45f, 75f, paintTitle)
        canvas.drawText("File PDF ini sebelumnya tersimpan di memori lokal HP pembuat buku.", 45f, 98f, paintBody)

        var y = 145f
        canvas.drawText("Solusi agar dapat dibaca di semua perangkat:", 40f, y, paintSubtitle)
        y += 25f
        canvas.drawText("1. Buka aplikasi di HP admin yang mengunggah buku,", 45f, y, paintBody)
        y += 20f
        canvas.drawText("2. Masuk ke halaman Edit Buku, lalu tekan tombol 'Pilih Berkas PDF'", 45f, y, paintBody)
        y += 20f
        canvas.drawText("   agar file otomatis diunggah ke Cloud Storage online gratis,", 45f, y, paintBody)
        y += 20f
        canvas.drawText("3. Atau tempelkan link publik Google Drive / Cloud langsung.", 45f, y, paintBody)

        document.finishPage(page)
        FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.Default) {
        synchronized(renderLock) {
            val renderer = pdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat()
                val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (_: Exception) {
                null
            } finally {
                page?.close()
            }
        }
    }

    fun close() {
        synchronized(renderLock) {
            try {
                pdfRenderer?.close()
            } catch (_: Exception) {}
            try {
                fileDescriptor?.close()
            } catch (_: Exception) {}
            try {
                // Hapus file sementara jika bukan berkas cache permanen
                if (activePdfFile != null && activePdfFile!!.name.startsWith("epustaka_")) {
                    activePdfFile?.delete()
                }
            } catch (_: Exception) {}

            pdfRenderer = null
            fileDescriptor = null
            activePdfFile = null
            pageCount = 0
        }
    }
}
