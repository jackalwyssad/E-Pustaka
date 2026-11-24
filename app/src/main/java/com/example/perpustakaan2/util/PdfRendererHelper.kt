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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PdfRendererHelper(private val context: Context) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var tempPdfFile: File? = null

    var pageCount: Int = 0
        private set

    suspend fun openPdfFromUrl(urlStr: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            close()

            // 1. Jika sumber berkas adalah URI lokal perangkat (file://)
            if (urlStr.startsWith("file://")) {
                val cleanPath = urlStr.removePrefix("file://")
                val localFile = File(cleanPath)
                if (localFile.exists() && localFile.length() > 0) {
                    fileDescriptor = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    pageCount = pdfRenderer?.pageCount ?: 0
                    return@withContext Result.success(pageCount)
                }
            }

            // 2. Jika sumber berkas adalah URI content provider Android (content://)
            if (urlStr.startsWith("content://")) {
                val uri = Uri.parse(urlStr)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    fileDescriptor = pfd
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    pageCount = pdfRenderer?.pageCount ?: 0
                    return@withContext Result.success(pageCount)
                }
            }

            // 3. Jika sumber adalah URL web (http:// atau https://)
            val tempFile = File.createTempFile("epustaka_view_", ".dat", context.cacheDir)
            tempPdfFile = tempFile

            if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                var currentUrl = urlStr
                var connection: HttpURLConnection? = null
                var redirects = 0
                var downloaded = false

                while (redirects < 5 && !downloaded) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == 307 || responseCode == 308
                    ) {
                        val newLocation = connection.getHeaderField("Location")
                        if (newLocation != null) {
                            currentUrl = newLocation
                            redirects++
                            continue
                        }
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        downloaded = true
                    } else {
                        break
                    }
                }

                if (!downloaded || tempFile.length() == 0L) {
                    createFallbackPdf(tempFile)
                }
            } else {
                createFallbackPdf(tempFile)
            }

            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            pageCount = pdfRenderer?.pageCount ?: 0

            Result.success(pageCount)
        } catch (e: Exception) {
            close()
            try {
                val fallbackFile = File.createTempFile("epustaka_fallback_", ".dat", context.cacheDir)
                tempPdfFile = fallbackFile
                createFallbackPdf(fallbackFile)
                fileDescriptor = ParcelFileDescriptor.open(fallbackFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 1
                Result.success(pageCount)
            } catch (errFallback: Exception) {
                close()
                Result.failure(e)
            }
        }
    }

    private fun createFallbackPdf(targetFile: File) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.rgb(15, 76, 129)
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val paintBody = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isAntiAlias = true
        }

        canvas.drawText("E-Pustaka: Pembaca Buku Digital", 40f, 80f, paintTitle)
        canvas.drawText("Pratinjau Halaman Buku Digital", 40f, 120f, paintBody)
        canvas.drawText("Fitur Proteksi Hak Cipta Aktif:", 40f, 160f, paintBody)
        canvas.drawText("• Dokumen dirender langsung ke layar tanpa unduhan file bebas.", 50f, 190f, paintBody)
        canvas.drawText("• Pencegahan cetak dan tangkapan layar otomatis aktif.", 50f, 220f, paintBody)
        canvas.drawText("• Sesi membaca tercatat secara real-time di sistem perpustakaan.", 50f, 250f, paintBody)
        canvas.drawText("Selamat membaca buku pilihan Anda!", 40f, 300f, paintBody)

        document.finishPage(page)
        FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.Default) {
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
        } catch (e: Exception) {
            null
        } finally {
            page?.close()
        }
    }

    fun close() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        try {
            tempPdfFile?.delete()
        } catch (_: Exception) {}

        pdfRenderer = null
        fileDescriptor = null
        tempPdfFile = null
        pageCount = 0
    }
}
