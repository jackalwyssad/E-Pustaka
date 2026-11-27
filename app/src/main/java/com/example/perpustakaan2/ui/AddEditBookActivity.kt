package com.example.perpustakaan2.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.Category
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.CategoryRepository
import com.example.perpustakaan2.databinding.ActivityAddEditBookBinding
import com.example.perpustakaan2.util.PdfRendererHelper
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handlePickedImage(it) }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handlePickedPdf(it) }
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
            pickImageLauncher.launch("image/*")
        }

        binding.cardPickPdf.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.btnChoosePdf.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.etCoverUrl.doAfterTextChanged { text ->
            val url = text?.toString()?.trim() ?: ""
            if (url.isNotBlank() && url != coverPathOrUrl) {
                coverPathOrUrl = url
                binding.ivCoverPreview.visibility = View.VISIBLE
                binding.layoutCoverPlaceholder.visibility = View.GONE
                binding.ivCoverPreview.load(url)
            }
        }

        binding.etPdfUrl.doAfterTextChanged { text ->
            val url = text?.toString()?.trim() ?: ""
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
        setUploadProgress(true, "Mengunggah foto sampul ke Cloud Storage...")
        lifecycleScope.launch {
            try {
                val fileName = "cover_${UUID.randomUUID()}.jpg"
                val storageRef = FirebaseStorage.getInstance().reference.child("covers/$fileName")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                coverPathOrUrl = downloadUrl
                binding.etCoverUrl.setText(downloadUrl)
                binding.ivCoverPreview.visibility = View.VISIBLE
                binding.layoutCoverPlaceholder.visibility = View.GONE
                binding.ivCoverPreview.load(downloadUrl) { crossfade(true) }
                Toast.makeText(this@AddEditBookActivity, "Foto sampul berhasil diunggah ke Cloud!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.message ?: "Gagal upload"
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val localFile = File(filesDir, "cover_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(localFile).use { inputStream.copyTo(it) }
                        coverPathOrUrl = "file://${localFile.absolutePath}"
                        binding.etCoverUrl.setText(coverPathOrUrl)
                        binding.ivCoverPreview.visibility = View.VISIBLE
                        binding.layoutCoverPlaceholder.visibility = View.GONE
                        binding.ivCoverPreview.load(localFile)
                    }
                } catch (_: Exception) {}

                AlertDialog.Builder(this@AddEditBookActivity)
                    .setTitle("Peringatan Cloud Storage")
                    .setMessage("Gagal mengunggah ke Firebase Storage ($msg).\n\n" +
                            "💡 Agar foto sampul bisa dilihat di HP teman/user lain, pastikan fitur 'Storage' sudah diaktifkan di Firebase Console, atau masukkan URL gambar web (https://) langsung pada kolom URL.")
                    .setPositiveButton("Mengerti", null)
                    .show()
            } finally {
                setUploadProgress(false)
            }
        }
    }

    private fun handlePickedPdf(uri: Uri) {
        setUploadProgress(true, "Mengunggah berkas PDF ke Cloud Storage...")
        lifecycleScope.launch {
            try {
                val fileName = "doc_${UUID.randomUUID()}.pdf"
                val storageRef = FirebaseStorage.getInstance().reference.child("books/$fileName")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                pdfPathOrUrl = downloadUrl
                binding.etPdfUrl.setText(downloadUrl)
                binding.tvPdfStatus.text = "Dokumen Cloud: Berhasil diunggah"
                detectPdfPages(downloadUrl)
                Toast.makeText(this@AddEditBookActivity, "File PDF berhasil diunggah ke Cloud!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.message ?: "Gagal upload"
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val localFile = File(filesDir, "doc_${System.currentTimeMillis()}.pdf")
                        FileOutputStream(localFile).use { inputStream.copyTo(it) }
                        val localPath = "file://${localFile.absolutePath}"
                        pdfPathOrUrl = localPath
                        binding.etPdfUrl.setText(localPath)
                        binding.tvPdfStatus.text = "Dokumen Lokal: ${localFile.name}"
                        detectPdfPages(localPath)
                    }
                } catch (_: Exception) {}

                AlertDialog.Builder(this@AddEditBookActivity)
                    .setTitle("Peringatan Cloud Storage")
                    .setMessage("Gagal mengunggah ke Firebase Storage ($msg).\n\n" +
                            "💡 Agar file PDF bisa dibaca di HP teman/user lain, pastikan fitur 'Storage' sudah diaktifkan di Firebase Console, atau masukkan URL file PDF online (https://) langsung pada kolom URL.")
                    .setPositiveButton("Mengerti", null)
                    .show()
            } finally {
                setUploadProgress(false)
            }
        }
    }

    private fun detectPdfPages(source: String) {
        binding.tvPdfPageNotice.visibility = View.VISIBLE
        binding.tvPdfPageNotice.text = "Memeriksa jumlah halaman PDF..."
        lifecycleScope.launch {
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

        val bookToSave = (existingBook ?: Book()).copy(
            id = editingBookId ?: existingBook?.id ?: "",
            judul = title,
            penulis = author,
            sinopsis = synopsis,
            kategoriId = categoryId,
            namaKategori = selectedCatName,
            urlSampul = coverPathOrUrl,
            urlPdf = pdfPathOrUrl,
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
