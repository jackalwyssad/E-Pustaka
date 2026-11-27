package com.example.perpustakaan2.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.LoanAdapter
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.data.repository.LoanRepository
import com.example.perpustakaan2.databinding.ActivityReadingHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReadingHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingHistoryBinding
    private val authRepository = AuthRepository()
    private val loanRepository = LoanRepository()
    private lateinit var loanAdapter: LoanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeHistory()
    }

    private fun setupToolbar() {
        binding.toolbarHistory.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        loanAdapter = LoanAdapter(
            isHistoryMode = true,
            onReadClick = { loan ->
                val intent = Intent(this, PdfReaderActivity::class.java).apply {
                    putExtra(PdfReaderActivity.EXTRA_BOOK_ID, loan.bookId)
                    putExtra(PdfReaderActivity.EXTRA_BOOK_TITLE, loan.judulBuku)
                    putExtra(PdfReaderActivity.EXTRA_PDF_URL, loan.urlPdf)
                }
                startActivity(intent)
            },
            onReturnClick = {}
        )
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@ReadingHistoryActivity)
            adapter = loanAdapter
        }
    }

    private fun observeHistory() {
        val uid = authRepository.currentUser?.uid ?: return

        lifecycleScope.launch {
            loanRepository.getLoanHistoryFlow(uid).collectLatest { loans ->
                loanAdapter.submitList(loans)
                binding.tvEmptyHistory.visibility = if (loans.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
