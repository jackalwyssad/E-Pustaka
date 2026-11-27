package com.example.perpustakaan2.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.LoanRepository
import com.example.perpustakaan2.databinding.ActivityPdfReaderBinding
import com.example.perpustakaan2.util.PdfRendererHelper
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class PdfReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_PDF_URL = "extra_pdf_url"
        const val EXTRA_IS_PREVIEW = "extra_is_preview"
        const val EXTRA_MAX_PREVIEW_PERCENT = "extra_max_preview_percent"
        const val EXTRA_LOAN_ID = "extra_loan_id"
        const val EXTRA_START_PAGE = "extra_start_page"
        const val EXTRA_IS_KILAT = "extra_is_kilat"
        const val EXTRA_DUE_DATE = "extra_due_date"
    }

    private lateinit var binding: ActivityPdfReaderBinding
    private lateinit var pdfHelper: PdfRendererHelper

    private val authRepository = AuthRepository()
    private val bookRepository = BookRepository()
    private val loanRepository = LoanRepository()

    private var bookId: String = ""
    private var bookTitle: String = ""
    private var pdfUrl: String = ""
    private var isPreview: Boolean = false
    private var maxPreviewPercent: Int = 100
    private var loanId: String = ""
    private var startPage: Int = 1
    private var isKilat: Boolean = false
    private var dueDate: Long = 0L
    private var kilatTimer: CountDownTimer? = null

    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0
    private var maxAllowedPageIndex: Int = 0
    private var isDarkMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
        bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: "Membaca Buku"
        pdfUrl = intent.getStringExtra(EXTRA_PDF_URL) ?: ""
        isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
        maxPreviewPercent = intent.getIntExtra(EXTRA_MAX_PREVIEW_PERCENT, 100)
        loanId = intent.getStringExtra(EXTRA_LOAN_ID) ?: ""
        startPage = intent.getIntExtra(EXTRA_START_PAGE, 1).coerceAtLeast(1)
        isKilat = intent.getBooleanExtra(EXTRA_IS_KILAT, false)
        dueDate = intent.getLongExtra(EXTRA_DUE_DATE, 0L)

        pdfHelper = PdfRendererHelper(this)

        setupToolbar()
        setupControls()
        setupKilatTimer()
        resolveAndLoadPdf()
    }

    private fun setupToolbar() {
        binding.toolbarReader.title = if (isPreview) "$bookTitle (Cuplikan 20%)" else bookTitle
        binding.toolbarReader.setNavigationOnClickListener { finish() }

        if (loanId.isNotBlank()) {
            binding.btnReturnFromReader.visibility = View.VISIBLE
            binding.btnReturnFromReader.setOnClickListener {
                val title = if (isKilat) "Akhiri Sesi Kilat?" else "Kembalikan Buku?"
                val msg = if (isKilat) {
                    "Apakah Anda ingin mengakhiri Sesi Baca Kilat sekarang dan mengembalikan buku?"
                } else {
                    "Apakah Anda ingin mengembalikan buku ini sekarang?"
                }
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton("Ya, Kembalikan") { _, _ ->
                        lifecycleScope.launch {
                            loanRepository.returnBook(loanId)
                            Toast.makeText(this@PdfReaderActivity, "Buku berhasil dikembalikan", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        } else {
            binding.btnReturnFromReader.visibility = View.GONE
        }

        binding.btnToggleReaderDark.setOnClickListener {
            isDarkMode = !isDarkMode
            val bg = if (isDarkMode) Color.parseColor("#121212") else Color.parseColor("#F4F7F4")
            binding.layoutPdfRoot.setBackgroundColor(bg)
            binding.btnToggleReaderDark.setImageResource(
                if (isDarkMode) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            )
        }
    }

    private fun setupKilatTimer() {
        if (!isKilat || dueDate <= 0L) return

        val now = System.currentTimeMillis()
        val remaining = dueDate - now

        if (remaining <= 0) {
            AlertDialog.Builder(this)
                .setTitle("Waktu Sesi Kilat Habis")
                .setMessage("Waktu Sesi Baca Kilat (2 Jam) telah berakhir. Buku telah otomatis dikembalikan.")
                .setPositiveButton("OK") { _, _ ->
                    lifecycleScope.launch {
                        if (loanId.isNotBlank()) loanRepository.returnBook(loanId)
                        finish()
                    }
                }
                .setCancelable(false)
                .show()
            return
        }

        kilatTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                binding.toolbarReader.subtitle = String.format("⏱️ Sisa Sesi Kilat: %02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
                binding.toolbarReader.subtitle = "⏱️ Sesi Kilat Berakhir"
                if (!isFinishing) {
                    AlertDialog.Builder(this@PdfReaderActivity)
                        .setTitle("Waktu Sesi Kilat Habis")
                        .setMessage("Waktu Sesi Baca Kilat 2 jam Anda telah berakhir. Buku telah otomatis dikembalikan.")
                        .setPositiveButton("OK") { _, _ ->
                            lifecycleScope.launch {
                                if (loanId.isNotBlank()) loanRepository.returnBook(loanId)
                                finish()
                            }
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }.start()
    }

    private fun setupControls() {
        binding.btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                renderPage(currentPageIndex - 1)
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPageIndex < maxAllowedPageIndex) {
                renderPage(currentPageIndex + 1)
            } else if (isPreview && currentPageIndex >= maxAllowedPageIndex) {
                Toast.makeText(this, "Batas cuplikan gratis (20%) tercapai. Pinjam buku untuk membaca penuh.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.viewTapPrev.setOnClickListener {
            if (currentPageIndex > 0) renderPage(currentPageIndex - 1)
        }

        binding.viewTapNext.setOnClickListener {
            if (currentPageIndex < maxAllowedPageIndex) {
                renderPage(currentPageIndex + 1)
            } else if (isPreview && currentPageIndex >= maxAllowedPageIndex) {
                Toast.makeText(this, "Batas cuplikan gratis (20%) tercapai. Pinjam buku untuk membaca penuh.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.sliderPage.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                try {
                    val target = (slider.value.toInt() - 1).coerceIn(0, maxAllowedPageIndex)
                    renderPage(target)
                } catch (_: Exception) {}
            }
        })
    }

    private fun resolveAndLoadPdf() {
        binding.pbPdfLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Jika pdfUrl kosong, coba ambil dari bookRepository berdasarkan bookId
            if (pdfUrl.isBlank() && bookId.isNotBlank()) {
                val bookRes = bookRepository.getBookById(bookId)
                val book = bookRes.getOrNull()
                if (book != null && book.urlPdf.isNotBlank()) {
                    pdfUrl = book.urlPdf
                    if (bookTitle == "Membaca Buku" && book.judul.isNotBlank()) {
                        bookTitle = book.judul
                        binding.toolbarReader.title = if (isPreview) "$bookTitle (Cuplikan 20%)" else bookTitle
                    }
                }
            }

            // Buka PDF melalui PdfRendererHelper
            val result = pdfHelper.openPdfFromUrl(pdfUrl)
            binding.pbPdfLoading.visibility = View.GONE

            result.fold(
                onSuccess = { count ->
                    totalPages = count.coerceAtLeast(1)
                    maxAllowedPageIndex = if (isPreview) {
                        ((totalPages * maxPreviewPercent / 100) - 1).coerceAtLeast(0)
                    } else {
                        totalPages - 1
                    }

                    // Pencegahan Crash: Material Slider mensyaratkan valueFrom < valueTo
                    if (totalPages > 1) {
                        try {
                            binding.sliderPage.visibility = View.VISIBLE
                            binding.sliderPage.valueFrom = 1f
                            binding.sliderPage.valueTo = totalPages.toFloat()
                            binding.sliderPage.stepSize = 1f
                        } catch (e: Exception) {
                            binding.sliderPage.visibility = View.GONE
                        }
                    } else {
                        binding.sliderPage.visibility = View.GONE
                    }

                    // Buka halaman terakhir jika melanjutkan baca
                    val initialIndex = (startPage - 1).coerceIn(0, maxAllowedPageIndex)
                    renderPage(initialIndex)
                },
                onFailure = { e ->
                    Toast.makeText(this@PdfReaderActivity, "Gagal memuat dokumen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun renderPage(pageIndex: Int) {
        currentPageIndex = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val displayPage = currentPageIndex + 1
        binding.tvPageIndicator.text = "Halaman $displayPage / $totalPages"

        // Update Slider aman
        if (totalPages > 1 && binding.sliderPage.visibility == View.VISIBLE) {
            try {
                binding.sliderPage.value = displayPage.toFloat().coerceIn(binding.sliderPage.valueFrom, binding.sliderPage.valueTo)
            } catch (_: Exception) {}
        }

        binding.btnPrevPage.isEnabled = currentPageIndex > 0
        binding.btnNextPage.isEnabled = currentPageIndex < maxAllowedPageIndex

        lifecycleScope.launch {
            try {
                val bitmap = pdfHelper.renderPage(currentPageIndex)
                if (bitmap != null) {
                    binding.ivPdfPage.setImageBitmap(bitmap)
                }
            } catch (_: Exception) {}

            // Sinkronisasi kemajuan baca ke Firebase jika membaca buku pinjaman
            val uid = authRepository.currentUser?.uid
            if (uid != null && loanId.isNotBlank()) {
                try {
                    loanRepository.updateReadingProgress(loanId, uid, displayPage, totalPages)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        kilatTimer?.cancel()
        kilatTimer = null
        pdfHelper.close()
    }
}
