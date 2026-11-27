package com.example.perpustakaan2.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.perpustakaan2.R
import com.example.perpustakaan2.adapter.ReviewAdapter
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.Loan
import com.example.perpustakaan2.data.model.QueueEntry
import com.example.perpustakaan2.data.model.Review
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.LoanRepository
import com.example.perpustakaan2.data.repository.QueueRepository
import com.example.perpustakaan2.databinding.ActivityBookDetailBinding
import com.example.perpustakaan2.databinding.DialogReviewBinding
import com.example.perpustakaan2.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class BookDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }

    private lateinit var binding: ActivityBookDetailBinding

    private val authRepository = AuthRepository()
    private val bookRepository = BookRepository()
    private val loanRepository = LoanRepository()
    private val queueRepository = QueueRepository()

    private var bookId: String = ""
    private var currentBook: Book? = null
    private var activeLoan: Loan? = null
    private var activeQueue: QueueEntry? = null
    private var isFavorite: Boolean = false
    private var canWriteReview: Boolean = false
    private var isAutoReturningKilat: Boolean = false
    private var bookDeletedListener: ListenerRegistration? = null

    private lateinit var reviewAdapter: ReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
        if (bookId.isEmpty()) {
            Toast.makeText(this, "Buku tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupReviewsRecyclerView()
        setupButtons()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbarDetail.setNavigationOnClickListener { finish() }

        binding.btnFavorite.setOnClickListener {
            val uid = authRepository.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Silakan login untuk menyimpan favorit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                bookRepository.toggleFavorite(uid, bookId)
            }
        }
    }

    private fun setupReviewsRecyclerView() {
        val currentUid = authRepository.currentUser?.uid ?: ""
        reviewAdapter = ReviewAdapter(
            currentUserId = currentUid,
            onEditClick = { review -> showReviewDialog(review) },
            onDeleteClick = { review -> showDeleteReviewDialog(review) }
        )
        binding.rvReviews.apply {
            layoutManager = LinearLayoutManager(this@BookDetailActivity)
            adapter = reviewAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupButtons() {
        binding.btnContinueReading.setOnClickListener {
            val loan = activeLoan ?: return@setOnClickListener
            val pdfTarget = if (!currentBook?.urlPdf.isNullOrBlank()) currentBook!!.urlPdf else loan.urlPdf
            openPdfReader(
                pdfUrl = pdfTarget,
                isPreview = false,
                loanId = loan.id,
                startPage = loan.halamanTerakhir,
                isKilat = loan.tipe == Constants.TIPE_KILAT,
                dueDate = loan.tanggalJatuhTempo
            )
        }

        binding.btnReturnBookDetail.setOnClickListener {
            val loan = activeLoan ?: return@setOnClickListener
            val isKilat = loan.tipe == Constants.TIPE_KILAT
            val title = if (isKilat) "Akhiri Sesi Kilat" else "Kembalikan Buku"
            val message = if (isKilat) {
                "Apakah Anda ingin mengakhiri Sesi Baca Kilat untuk buku \"${loan.judulBuku}\" sekarang? Slot kilat akan dikembalikan."
            } else {
                "Apakah Anda yakin ingin mengembalikan buku \"${loan.judulBuku}\"?"
            }

            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(if (isKilat) "Akhiri Sesi" else "Kembalikan") { _, _ ->
                    lifecycleScope.launch {
                        val res = loanRepository.returnBook(loan.id)
                        res.fold(
                            onSuccess = {
                                Toast.makeText(
                                    this@BookDetailActivity,
                                    if (isKilat) "Sesi kilat telah diakhiri." else "Buku berhasil dikembalikan.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                activeLoan = null
                                refreshBook()
                                updateLoanAndQueueUI()
                            },
                            onFailure = { e ->
                                Toast.makeText(this@BookDetailActivity, "Gagal mengembalikan buku: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        binding.btnReadPreview.setOnClickListener {
            val book = currentBook ?: return@setOnClickListener
            openPdfReader(book.urlPdf, isPreview = true, maxPreviewPercent = 20, startPage = 1)
        }

        binding.btnQuickSession.setOnClickListener {
            val uid = authRepository.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Silakan login untuk meminjam buku kilat", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val book = currentBook ?: return@setOnClickListener
            binding.btnQuickSession.isEnabled = false
            lifecycleScope.launch {
                try {
                    val res = loanRepository.startQuickSession(uid, book)
                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@BookDetailActivity, "Sesi Baca Kilat (2 Jam) Dimulai!", Toast.LENGTH_SHORT).show()
                            refreshBook()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@BookDetailActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                } finally {
                    binding.btnQuickSession.isEnabled = true
                }
            }
        }

        binding.btnBorrowBook.setOnClickListener {
            val uid = authRepository.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Silakan login untuk meminjam buku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val book = currentBook ?: return@setOnClickListener

            binding.btnBorrowBook.isEnabled = false
            if (book.stokTersedia > 0) {
                lifecycleScope.launch {
                    try {
                        val res = loanRepository.borrowBook(uid, book)
                        res.fold(
                            onSuccess = {
                                Toast.makeText(this@BookDetailActivity, "Buku berhasil dipinjam selama 7 hari!", Toast.LENGTH_SHORT).show()
                                refreshBook()
                            },
                            onFailure = { e ->
                                Toast.makeText(this@BookDetailActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    } finally {
                        binding.btnBorrowBook.isEnabled = true
                    }
                }
            } else {
                lifecycleScope.launch {
                    try {
                        val res = queueRepository.joinQueue(uid, book)
                        res.fold(
                            onSuccess = {
                                Toast.makeText(this@BookDetailActivity, "Anda telah masuk dalam antrian peminjaman", Toast.LENGTH_SHORT).show()
                                refreshBook()
                            },
                            onFailure = { e ->
                                Toast.makeText(this@BookDetailActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    } finally {
                        binding.btnBorrowBook.isEnabled = true
                    }
                }
            }
        }

        binding.btnCancelQueueFromDetail.setOnClickListener {
            val queue = activeQueue ?: return@setOnClickListener
            binding.btnCancelQueueFromDetail.isEnabled = false
            lifecycleScope.launch {
                try {
                    val res = queueRepository.cancelQueue(queue.id, queue.bookId)
                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@BookDetailActivity, "Antrian berhasil dibatalkan", Toast.LENGTH_SHORT).show()
                            refreshBook()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@BookDetailActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                } finally {
                    binding.btnCancelQueueFromDetail.isEnabled = true
                }
            }
        }

        binding.btnAddReview.setOnClickListener {
            val uid = authRepository.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Silakan login untuk menulis ulasan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!canWriteReview) {
                Toast.makeText(this, "Anda hanya dapat memberi ulasan setelah meminjam buku atau menggunakan Sesi Kilat.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showReviewDialog(null)
        }
    }

    private fun refreshBook() {
        lifecycleScope.launch {
            val res = bookRepository.getBookById(bookId)
            if (res.isSuccess && res.getOrNull() == null) {
                Toast.makeText(this@BookDetailActivity, "Buku ini telah dihapus oleh Admin.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            res.getOrNull()?.let {
                currentBook = it
                bindBook(it)
            }
        }
    }

    private fun observeData() {
        val currentUid = authRepository.currentUser?.uid

        refreshBook()

        // Pantau penghapusan buku secara real-time agar halaman detail ikut tertutup
        // (termasuk saat pengguna sedang membaca di PdfReaderActivity di atasnya)
        var bookExistedInFirestore = false
        bookDeletedListener = FirebaseFirestore.getInstance()
            .collection(Constants.COLLECTION_BOOKS)
            .document(bookId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    if (snapshot.exists()) {
                        bookExistedInFirestore = true
                    } else if (bookExistedInFirestore) {
                        if (!isFinishing && !isDestroyed) {
                            finish()
                        }
                    }
                }
            }

        lifecycleScope.launch {
            bookRepository.getReviewsFlow(bookId).collectLatest { reviews ->
                reviewAdapter.submitList(reviews)
                binding.tvEmptyReviews.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
                binding.tvReviewSectionTitle.text = "Ulasan Pembaca (${reviews.size})"
            }
        }

        if (currentUid != null) {
            lifecycleScope.launch {
                loanRepository.getActiveLoansFlow(currentUid).collectLatest { loans ->
                    activeLoan = loans.find { it.bookId == bookId }
                    updateLoanAndQueueUI()
                }
            }

            lifecycleScope.launch {
                loanRepository.getLoanHistoryFlow(currentUid).collectLatest { allLoans ->
                    canWriteReview = allLoans.any {
                        it.bookId == bookId && (it.tipe == Constants.TIPE_PINJAM || it.tipe == Constants.TIPE_KILAT)
                    }
                    binding.btnAddReview.visibility = if (canWriteReview) View.VISIBLE else View.GONE
                }
            }

            lifecycleScope.launch {
                queueRepository.getUserQueuesFlow(currentUid).collectLatest { queues ->
                    activeQueue = queues.find { it.bookId == bookId }
                    updateLoanAndQueueUI()
                }
            }

            lifecycleScope.launch {
                bookRepository.isFavoriteFlow(currentUid, bookId).collectLatest { fav ->
                    isFavorite = fav
                    binding.btnFavorite.setImageResource(
                        if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                    )
                    binding.btnFavorite.imageTintList = getColorStateList(
                        if (isFavorite) R.color.error else R.color.text_primary
                    )
                }
            }
        }
    }

    private fun bindBook(book: Book) {
        binding.tvDetailTitle.text = book.judul
        binding.tvDetailAuthor.text = book.penulis
        binding.tvDetailCategory.text = book.namaKategori
        binding.tvDetailRating.text = if (book.rataRating > 0f) {
            "★ ${String.format(Locale.US, "%.1f", book.rataRating)}"
        } else {
            "★ -"
        }
        binding.tvDetailRatingCount.text = "(${book.jumlahRating} Ulasan)"

        binding.tvMetricPages.text = "${book.jumlahHalaman}\nHalaman"
        binding.tvMetricBorrowers.text = "${book.jumlahPeminjam}\nPeminjam"
        binding.tvMetricStock.text = "${book.stokTersedia}/${book.stokMaksimal}\nTersedia"
        binding.tvMetricKilat.text = "${book.stokSesiKilat}\nSlot Kilat"

        binding.tvStockDetailStatus.text = "Ketersediaan Stok Fisik / Lisensi (${book.stokTersedia} dari ${book.stokMaksimal} tersedia)"
        val progress = if (book.stokMaksimal > 0) (book.stokTersedia * 100 / book.stokMaksimal) else 0
        binding.pbStock.progress = progress

        binding.tvDetailSynopsis.text = book.sinopsis.ifBlank { "Tidak ada sinopsis." }

        if (book.urlSampul.isNotBlank()) {
            binding.ivDetailCover.load(book.urlSampul) {
                crossfade(true)
                placeholder(R.drawable.ic_book)
                error(R.drawable.ic_book)
            }
        } else {
            binding.ivDetailCover.setImageResource(R.drawable.ic_book)
        }

        updateLoanAndQueueUI()
    }

    private fun updateLoanAndQueueUI() {
        val book = currentBook ?: return

        if (activeLoan != null) {
            val loan = activeLoan!!
            val now = System.currentTimeMillis()

            // Jika sesi kilat telah lewat 2 jam, otomatis kembalikan
            if (loan.tipe == Constants.TIPE_KILAT && loan.tanggalJatuhTempo in 1..now) {
                if (!isAutoReturningKilat) {
                    isAutoReturningKilat = true
                    lifecycleScope.launch {
                        try {
                            loanRepository.returnBook(loan.id)
                            activeLoan = null
                            refreshBook()
                        } finally {
                            isAutoReturningKilat = false
                        }
                    }
                }
                return
            }

            binding.btnContinueReading.visibility = View.VISIBLE
            binding.btnReturnBookDetail.visibility = View.VISIBLE

            if (loan.tipe == Constants.TIPE_KILAT) {
                val remainingMinutes = ((loan.tanggalJatuhTempo - now) / 60000).coerceAtLeast(0)
                binding.btnContinueReading.text = "Lanjut Baca Kilat (Sisa ${remainingMinutes} mnt)"
                binding.btnReturnBookDetail.text = "Akhiri Sesi Kilat Sekarang"
            } else {
                binding.btnContinueReading.text = "Lanjut Membaca (Hal ${loan.halamanTerakhir})"
                binding.btnReturnBookDetail.text = "Kembalikan Buku"
            }

            binding.cardQueueActive.visibility = View.GONE
            binding.btnBorrowBook.visibility = View.GONE
            binding.btnQuickSession.visibility = View.GONE
            binding.tvQueueNotice.visibility = View.GONE
            binding.tvKilatNotice.visibility = View.GONE
            return
        }

        binding.btnReturnBookDetail.visibility = View.GONE
        binding.btnContinueReading.visibility = View.GONE

        if (activeQueue != null) {
            binding.cardQueueActive.visibility = View.VISIBLE
            binding.tvUserQueuePosition.text = "Nomor Urut Antrian Anda: #${activeQueue?.nomorUrut}"
            binding.btnBorrowBook.visibility = View.GONE
            binding.btnQuickSession.visibility = View.GONE
            return
        }

        binding.cardQueueActive.visibility = View.GONE
        binding.btnBorrowBook.visibility = View.VISIBLE
        binding.btnQuickSession.visibility = View.VISIBLE

        if (book.stokTersedia > 0) {
            binding.btnBorrowBook.text = "Pinjam Buku (7 Hari)"
            binding.btnBorrowBook.setBackgroundColor(getColor(R.color.primary))
            binding.tvQueueNotice.visibility = View.GONE
        } else {
            binding.btnBorrowBook.text = "Masuk Antrian Buku"
            binding.btnBorrowBook.setBackgroundColor(getColor(R.color.secondary))
            binding.tvQueueNotice.visibility = View.VISIBLE
            binding.tvQueueNotice.text = "Buku sedang dipinjam seluruhnya. Masuk antrian agar diprioritaskan saat buku dikembalikan!"
        }

        if (book.stokSesiKilat > 0) {
            binding.btnQuickSession.visibility = View.VISIBLE
            binding.tvKilatNotice.visibility = View.GONE
        } else {
            binding.btnQuickSession.visibility = View.GONE
            binding.tvKilatNotice.visibility = View.VISIBLE
            binding.tvKilatNotice.text = "Slot baca kilat 2 jam saat ini sedang penuh."
        }
    }

    private fun openPdfReader(
        pdfUrl: String,
        isPreview: Boolean,
        maxPreviewPercent: Int = 100,
        loanId: String = "",
        startPage: Int = 1,
        isKilat: Boolean = false,
        dueDate: Long = 0L
    ) {
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_BOOK_ID, bookId)
            putExtra(PdfReaderActivity.EXTRA_BOOK_TITLE, currentBook?.judul ?: "Buku")
            putExtra(PdfReaderActivity.EXTRA_PDF_URL, pdfUrl)
            putExtra(PdfReaderActivity.EXTRA_IS_PREVIEW, isPreview)
            putExtra(PdfReaderActivity.EXTRA_MAX_PREVIEW_PERCENT, maxPreviewPercent)
            putExtra(PdfReaderActivity.EXTRA_LOAN_ID, loanId)
            putExtra(PdfReaderActivity.EXTRA_START_PAGE, startPage)
            putExtra(PdfReaderActivity.EXTRA_IS_KILAT, isKilat)
            putExtra(PdfReaderActivity.EXTRA_DUE_DATE, dueDate)
        }
        startActivity(intent)
    }

    private fun showReviewDialog(existingReview: Review?) {
        val dialogBinding = DialogReviewBinding.inflate(LayoutInflater.from(this))
        val isEdit = existingReview != null

        if (isEdit) {
            dialogBinding.tvDialogReviewTitle.text = "Edit Ulasan Anda"
            dialogBinding.rbReviewRating.rating = existingReview!!.rating.toFloat()
            dialogBinding.etReviewText.setText(existingReview.ulasan)
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(if (isEdit) "Perbarui" else "Kirim Ulasan") { _, _ ->
                val rating = dialogBinding.rbReviewRating.rating.toInt().coerceIn(1, 5)
                val comment = dialogBinding.etReviewText.text.toString().trim()
                if (comment.isEmpty()) {
                    Toast.makeText(this, "Harap tulis ulasan Anda", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val currentUid = authRepository.currentUser?.uid ?: return@setPositiveButton

                lifecycleScope.launch {
                    val res = if (isEdit) {
                        bookRepository.updateReview(
                            reviewId = existingReview!!.id,
                            bookId = bookId,
                            userId = currentUid,
                            newRating = rating,
                            newUlasan = comment
                        )
                    } else {
                        val user = authRepository.getUserProfile(currentUid).getOrNull()
                        val review = Review(
                            userId = currentUid,
                            bookId = bookId,
                            namaUser = user?.nama ?: "Anggota",
                            fotoProfil = user?.fotoProfil,
                            rating = rating,
                            ulasan = comment
                        )
                        bookRepository.addReview(review)
                    }

                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@BookDetailActivity, if (isEdit) "Ulasan diperbarui" else "Ulasan berhasil dikirim", Toast.LENGTH_SHORT).show()
                            refreshBook()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@BookDetailActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDeleteReviewDialog(review: Review) {
        val currentUid = authRepository.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Hapus Ulasan")
            .setMessage("Apakah Anda yakin ingin menghapus ulasan Anda?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    val res = bookRepository.deleteReview(review.id, bookId, currentUid)
                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@BookDetailActivity, "Ulasan berhasil dihapus", Toast.LENGTH_SHORT).show()
                            refreshBook()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@BookDetailActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshBook()
    }

    override fun onDestroy() {
        super.onDestroy()
        bookDeletedListener?.remove()
        bookDeletedListener = null
    }
}
