package com.example.perpustakaan2.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.Category
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.CategoryRepository
import com.example.perpustakaan2.databinding.ActivityAddEditBookBinding
import com.example.perpustakaan2.util.CloudUploader
import com.example.perpustakaan2.util.PdfRendererHelper
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class AddEditBookActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }

    private lateinit var binding: ActivityAddEditBookBinding

    private val bookRepository = BookRepository()
    private val categoryRepository = CategoryRepository()
    private lateinit var pdfHelper: PdfRendererHelper

    private var editingBookId: String? = null
    private var existingBook: Book? = null
    private var categoryList: List<Category> = emptyList()

    private var coverPathOrUrl: String = ""
    private var pdfPathOrUrl: String = ""
    private var detectPagesJob: Job? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            handlePickedImage(it)
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            handlePickedPdf(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfHelper = PdfRendererHelper(this)
        editingBookId = intent.getStringExtra(EXTRA_BOOK_ID)

        setupToolbar()
        setupListeners()
        loadCategoriesAndBook()
    }

    private fun setupToolbar() {
        val isEdit = !editingBookId.isNullOrBlank()
        binding.toolbarAddEdit.title = if (isEdit) "Edit Buku" else "Tambah Buku Baru"
        binding.toolbarAddEdit.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.cardPickCover.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        binding.cardPickPdf.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        binding.btnChoosePdf.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        binding.etCoverUrl.doAfterTextChanged { text ->
            val raw = text?.toString()?.trim() ?: ""
            val url = CloudUploader.convertToDirectDownloadUrl(raw)
            if (url.isNotBlank() && url != coverPathOrUrl) {
                coverPathOrUrl = url
                binding.ivCoverPreview.visibility = View.VISIBLE
                binding.layoutCoverPlaceholder.visibility = View.GONE
                binding.ivCoverPreview.load(url)
            }
        }

        binding.etPdfUrl.doAfterTextChanged { text ->
            val raw = text?.toString()?.trim() ?: ""
            val url = CloudUploader.convertToDirectDownloadUrl(raw)
            if (url.isNotBlank() && url != pdfPathOrUrl) {
                pdfPathOrUrl = url
                detectPdfPages(url)
            }
        }

        binding.btnSaveBook.setOnClickListener {
            saveBook()
        }
    }

    private fun loadCategoriesAndBook() {
        lifecycleScope.launch {
            val cats = categoryRepository.getCategoriesFlow().firstOrNull() ?: emptyList()
            categoryList = cats
            val names = cats.map { it.namaKategori }
            val displayNames = if (names.isEmpty()) {
                listOf("Teknologi", "Bisnis", "Sains", "Fiksi", "Pengembangan Diri")
            } else {
                names
            }

            val adapter = ArrayAdapter(this@AddEditBookActivity, android.R.layout.simple_spinner_dropdown_item, displayNames)
            binding.spCategory.adapter = adapter

            if (!editingBookId.isNullOrBlank()) {
                val bookRes = bookRepository.getBookById(editingBookId!!)
                val book = bookRes.getOrNull()
                if (book != null) {
                    existingBook = book
                    bindExistingBook(book)
                }
            }
        }
    }

    private fun bindExistingBook(book: Book) {
        binding.etBookTitle.setText(book.judul)
        binding.etBookAuthor.setText(book.penulis)
        binding.etBookSynopsis.setText(book.sinopsis)
        binding.etBookPages.setText(book.jumlahHalaman.toString())
        binding.etBookInitialStock.setText(book.stokTersedia.toString())
        binding.etBookMaxStock.setText(book.stokMaksimal.toString())
        binding.etBookKilatStock.setText(book.stokSesiKilat.toString())

        val catIndex = categoryList.indexOfFirst { it.id == book.kategoriId || it.namaKategori == book.namaKategori }
        if (catIndex >= 0) binding.spCategory.setSelection(catIndex)

        if (book.urlSampul.isNotBlank()) {
            coverPathOrUrl = book.urlSampul
            binding.etCoverUrl.setText(book.urlSampul)
            binding.ivCoverPreview.visibility = View.VISIBLE
            binding.layoutCoverPlaceholder.visibility = View.GONE
            binding.ivCoverPreview.load(book.urlSampul)
        }

        if (book.urlPdf.isNotBlank()) {
            pdfPathOrUrl = book.urlPdf
            binding.etPdfUrl.setText(book.urlPdf)
            binding.tvPdfStatus.text = "Dokumen PDF terpasang"
        }
    }

    private fun setUploadProgress(isLoading: Boolean, message: String = "") {
        binding.layoutUploadProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.tvUploadProgress.text = message
        binding.btnSaveBook.isEnabled = !isLoading
        binding.cardPickCover.isEnabled = !isLoading
        binding.btnChoosePdf.isEnabled = !isLoading
        binding.cardPickPdf.isEnabled = !isLoading
    }

    private fun handlePickedImage(uri: Uri) {
        setUploadProgress(true, "Mengompresi dan mengunggah sampul...")
        lifecycleScope.launch {
            val localFile = File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
            try {
                withContext(Dispatchers.IO) {
                    compressCoverImage(uri, localFile)
                }

                // Tampilkan preview lokal sementara proses unggah berlangsung
                binding.ivCoverPreview.visibility = View.VISIBLE
                binding.layoutCoverPlaceholder.visibility = View.GONE
                binding.ivCoverPreview.load(localFile)

                val uploadRes = CloudUploader.uploadFile(localFile, "image/jpeg")
                var firebaseErr: String? = null
                val finalUrl = uploadRes.getOrElse {
                    try {
                        val fileName = "cover_${UUID.randomUUID()}.jpg"
                        val storageRef = FirebaseStorage.getInstance().reference.child("covers/$fileName")
                        storageRef.putFile(Uri.fromFile(localFile)).await()
                        storageRef.downloadUrl.await().toString()
                    } catch (fbEx: Exception) {
                        firebaseErr = fbEx.message ?: fbEx.javaClass.simpleName
                        null
                    }
                }

                if (finalUrl != null && (finalUrl.startsWith("http://") || finalUrl.startsWith("https://"))) {
                    coverPathOrUrl = finalUrl
                    binding.etCoverUrl.setText(finalUrl)
                    imageLoader.enqueue(
                        ImageRequest.Builder(this@AddEditBookActivity)
                            .data(finalUrl)
                            .build()
                    )
                    Toast.makeText(this@AddEditBookActivity, "Foto sampul berhasil diunggah!", Toast.LENGTH_SHORT).show()
                } else {
                    restoreCoverPreview()
                    val cloudErr = uploadRes.exceptionOrNull()?.message ?: "Tidak diketahui"
                    val fbDetail = firebaseErr ?: "Tidak tersedia"
                    AlertDialog.Builder(this@AddEditBookActivity)
                        .setTitle("Gagal Mengunggah Sampul")
                        .setMessage("Sampul tidak dapat diunggah ke Cloud.\n\n• Cloud: $cloudErr\n• Firebase Storage: $fbDetail\n\nPeriksa koneksi internet Anda atau tempelkan URL gambar online (https://) langsung pada kolom URL.")
                        .setPositiveButton("Mengerti", null)
                        .show()
                }
            } catch (e: Exception) {
                restoreCoverPreview()
                Toast.makeText(this@AddEditBookActivity, "Gagal memproses gambar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                localFile.delete()
                setUploadProgress(false)
            }
        }
    }

    private fun restoreCoverPreview() {
        if (coverPathOrUrl.isNotBlank()) {
            binding.ivCoverPreview.visibility = View.VISIBLE
            binding.layoutCoverPlaceholder.visibility = View.GONE
            binding.ivCoverPreview.load(coverPathOrUrl)
        } else {
            binding.ivCoverPreview.visibility = View.GONE
            binding.layoutCoverPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun copyUriToFile(uri: Uri, targetFile: File) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Tidak dapat membuka berkas dari penyedia file (InputStream null). Coba pilih berkas dari penyimpanan lokal.")
        var totalBytes = 0L
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalBytes += read
                }
                output.flush()
            }
        }
        if (totalBytes <= 0L || !targetFile.exists() || targetFile.length() <= 0L) {
            targetFile.delete()
            throw IOException("Berkas yang dipilih kosong (0 byte).")
        }
    }

    private fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim * 2 || boundsOpts.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        return try {
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun compressCoverImage(uri: Uri, targetFile: File) {
        val rawTemp = File(cacheDir, "raw_cover_${System.currentTimeMillis()}.tmp")
        try {
            copyUriToFile(uri, rawTemp)

            val orientation = try {
                ExifInterface(rawTemp.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val maxDim = 600
            var bitmap = decodeSampledBitmap(rawTemp, maxDim)
            if (bitmap != null) {
                bitmap = applyExifOrientation(bitmap, orientation)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(rawTemp)
                bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    if (w > maxDim || h > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                        decoder.setTargetSize(
                            (w * ratio).toInt().coerceAtLeast(1),
                            (h * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }

            val decoded = bitmap
                ?: throw IOException("Format gambar tidak didukung atau berkas rusak. Gunakan JPG, PNG, atau WebP.")

            val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
            val scaled = if (ratio < 1f) {
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            try {
                FileOutputStream(targetFile).use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)) {
                        throw IOException("Gagal mengompresi gambar ke JPEG.")
                    }
                }
            } finally {
                scaled.recycle()
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                throw IOException("Hasil kompresi gambar kosong.")
            }
        } finally {
            rawTemp.delete()
        }
    }

    private fun isValidPdfHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 5) return false
        return try {
            val buf = ByteArray(1024)
            val read = FileInputStream(file).use { it.read(buf) }
            if (read <= 0) false else String(buf, 0, read, Charsets.ISO_8859_1).contains("%PDF-")
        } catch (_: Exception) {
            false
        }
    }

    private fun handlePickedPdf(uri: Uri) {
        setUploadProgress(true, "Menyiapkan dan mengunggah berkas PDF ke Cloud...")
        lifecycleScope.launch {
            val localFile = File(cacheDir, "doc_${System.currentTimeMillis()}.pdf")
            try {
                val detectedPages = withContext(Dispatchers.IO) {
                    copyUriToFile(uri, localFile)
                    if (!isValidPdfHeader(localFile)) {
                        throw IOException("Berkas yang dipilih bukan dokumen PDF yang valid.")
                    }
                    PdfRendererHelper.countPagesInLocalFile(localFile)
                }

                if (detectedPages > 0) {
                    binding.etBookPages.setText(detectedPages.toString())
                    binding.tvPdfPageNotice.visibility = View.VISIBLE
                    binding.tvPdfPageNotice.text = "Otomatis terdeteksi: $detectedPages halaman"
                }

                val uploadRes = CloudUploader.uploadFile(localFile, "application/pdf")
                var firebaseErr: String? = null
                val finalUrl = uploadRes.getOrElse {
                    try {
                        val fileName = "doc_${UUID.randomUUID()}.pdf"
                        val storageRef = FirebaseStorage.getInstance().reference.child("books/$fileName")
                        storageRef.putFile(Uri.fromFile(localFile)).await()
                        storageRef.downloadUrl.await().toString()
                    } catch (fbEx: Exception) {
                        firebaseErr = fbEx.message ?: fbEx.javaClass.simpleName
                        null
                    }
                }

                if (finalUrl != null && (finalUrl.startsWith("http://") || finalUrl.startsWith("https://"))) {
                    withContext(Dispatchers.IO) {
                        PdfRendererHelper.saveToCache(this@AddEditBookActivity, finalUrl, localFile)
                    }
                    pdfPathOrUrl = finalUrl
                    binding.etPdfUrl.setText(finalUrl)
                    binding.tvPdfStatus.text = "Dokumen Cloud: Berhasil terpasang"
                    Toast.makeText(this@AddEditBookActivity, "File PDF berhasil diunggah ke Cloud!", Toast.LENGTH_SHORT).show()
                } else {
                    val cloudErr = uploadRes.exceptionOrNull()?.message ?: "Tidak diketahui"
                    val fbDetail = firebaseErr ?: "Tidak tersedia"
                    AlertDialog.Builder(this@AddEditBookActivity)
                        .setTitle("Gagal Mengunggah PDF")
                        .setMessage("Berkas PDF tidak dapat diunggah ke Cloud.\n\n• Cloud: $cloudErr\n• Firebase Storage: $fbDetail\n\nPeriksa koneksi internet Anda atau tempelkan link PDF online (https://) / Google Drive langsung pada kolom URL.")
                        .setPositiveButton("Mengerti", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditBookActivity, "Gagal memproses PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                localFile.delete()
                setUploadProgress(false)
            }
        }
    }

    private fun detectPdfPages(source: String) {
        if (source.isBlank()) return
        detectPagesJob?.cancel()
        binding.tvPdfPageNotice.visibility = View.VISIBLE
        binding.tvPdfPageNotice.text = "Memeriksa jumlah halaman PDF..."
        detectPagesJob = lifecycleScope.launch {
            delay(400)
            val res = pdfHelper.openPdfFromUrl(source)
            res.fold(
                onSuccess = { count ->
                    binding.etBookPages.setText(count.toString())
                    binding.tvPdfPageNotice.text = "Otomatis terdeteksi: $count halaman"
                },
                onFailure = {
                    binding.tvPdfPageNotice.text = "Gagal mendeteksi halaman otomatis. Masukkan manual."
                }
            )
        }
    }

    private fun saveBook() {
        val title = binding.etBookTitle.text.toString().trim()
        val author = binding.etBookAuthor.text.toString().trim()
        val synopsis = binding.etBookSynopsis.text.toString().trim()
        val pagesStr = binding.etBookPages.text.toString().trim()
        val stockStr = binding.etBookInitialStock.text.toString().trim()
        val maxStockStr = binding.etBookMaxStock.text.toString().trim()
        val kilatStr = binding.etBookKilatStock.text.toString().trim()

        if (title.isEmpty() || author.isEmpty()) {
            Toast.makeText(this, "Judul dan Penulis wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val pages = pagesStr.toIntOrNull() ?: 100
        val stock = stockStr.toIntOrNull() ?: 5
        val maxStock = maxStockStr.toIntOrNull() ?: stock
        val kilat = kilatStr.toIntOrNull() ?: 3

        val selectedCatName = binding.spCategory.selectedItem?.toString() ?: "Teknologi"
        val selectedCat = categoryList.find { it.namaKategori == selectedCatName }
        val categoryId = selectedCat?.id ?: ""

        val inputPdf = binding.etPdfUrl.text.toString().trim()
        val finalPdfUrl = if (inputPdf.isNotEmpty()) inputPdf else pdfPathOrUrl

        val inputCover = binding.etCoverUrl.text.toString().trim()
        val finalCoverUrl = if (inputCover.isNotEmpty()) inputCover else coverPathOrUrl

        val bookToSave = (existingBook ?: Book()).copy(
            id = editingBookId ?: existingBook?.id ?: "",
            judul = title,
            penulis = author,
            sinopsis = synopsis,
            kategoriId = categoryId,
            namaKategori = selectedCatName,
            urlSampul = finalCoverUrl,
            urlPdf = finalPdfUrl,
            jumlahHalaman = pages,
            stokAwal = stock,
            stokTersedia = stock,
            stokMaksimal = maxStock,
            stokSesiKilat = kilat
        )

        binding.btnSaveBook.isEnabled = false
        lifecycleScope.launch {
            val res = if (editingBookId.isNullOrBlank()) {
                bookRepository.addBook(bookToSave)
            } else {
                bookRepository.updateBook(bookToSave)
            }

            binding.btnSaveBook.isEnabled = true
            res.fold(
                onSuccess = {
                    Toast.makeText(this@AddEditBookActivity, "Buku berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { e ->
                    Toast.makeText(this@AddEditBookActivity, "Gagal menyimpan buku: ${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfHelper.close()
    }
}
