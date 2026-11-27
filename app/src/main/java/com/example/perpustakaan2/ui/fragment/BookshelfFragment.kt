package com.example.perpustakaan2.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.LoanAdapter
import com.example.perpustakaan2.adapter.QueueAdapter
import com.example.perpustakaan2.data.model.Loan
import com.example.perpustakaan2.data.model.QueueEntry
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.LoanRepository
import com.example.perpustakaan2.data.repository.QueueRepository
import com.example.perpustakaan2.databinding.FragmentBookshelfBinding
import com.example.perpustakaan2.ui.PdfReaderActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookshelfFragment : Fragment() {

    private var _binding: FragmentBookshelfBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private val bookRepository = BookRepository()
    private val loanRepository = LoanRepository()
    private val queueRepository = QueueRepository()

    private lateinit var loanAdapter: LoanAdapter
    private lateinit var queueAdapter: QueueAdapter

    private var currentLoans: List<Loan> = emptyList()
    private var currentQueues: List<QueueEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookshelfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupTabs()
        observeData()
    }

    private fun setupRecyclerViews() {
        loanAdapter = LoanAdapter(
            onReadClick = { loan ->
                val intent = Intent(requireContext(), PdfReaderActivity::class.java).apply {
                    putExtra(PdfReaderActivity.EXTRA_BOOK_ID, loan.bookId)
                    putExtra(PdfReaderActivity.EXTRA_BOOK_TITLE, loan.judulBuku)
                    putExtra(PdfReaderActivity.EXTRA_PDF_URL, loan.urlPdf)
                    putExtra(PdfReaderActivity.EXTRA_LOAN_ID, loan.id)
                    putExtra(PdfReaderActivity.EXTRA_START_PAGE, loan.halamanTerakhir)
                    putExtra(PdfReaderActivity.EXTRA_IS_KILAT, loan.tipe == com.example.perpustakaan2.util.Constants.TIPE_KILAT)
                    putExtra(PdfReaderActivity.EXTRA_DUE_DATE, loan.tanggalJatuhTempo)
                }
                startActivity(intent)
            },
            onReturnClick = { loan ->
                showReturnDialog(loan)
            }
        )
        binding.rvActiveLoans.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = loanAdapter
        }

        queueAdapter = QueueAdapter(
            onClaimClick = { queue ->
                claimQueue(queue)
            },
            onCancelClick = { queue ->
                showCancelQueueDialog(queue)
            }
        )
        binding.rvQueues.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayoutBookshelf.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateViewVisibility()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeData() {
        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            updateViewVisibility()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            loanRepository.getActiveLoansFlow(currentUser.uid).collectLatest { loans ->
                currentLoans = loans
                loanAdapter.submitList(loans)
                updateViewVisibility()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            queueRepository.getUserQueuesFlow(currentUser.uid).collectLatest { queues ->
                currentQueues = queues
                queueAdapter.submitList(queues)
                updateViewVisibility()
            }
        }
    }

    private fun updateViewVisibility() {
        val isLoansTab = binding.tabLayoutBookshelf.selectedTabPosition == 0

        if (isLoansTab) {
            binding.rvActiveLoans.visibility = if (currentLoans.isNotEmpty()) View.VISIBLE else View.GONE
            binding.rvQueues.visibility = View.GONE
            binding.layoutEmptyBookshelf.visibility = if (currentLoans.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmptyTitle.text = "Belum Ada Buku Dipinjam"
            binding.tvEmptySubtitle.text = "Jelajahi beranda untuk meminjam buku atau membaca sesi kilat"
        } else {
            binding.rvActiveLoans.visibility = View.GONE
            binding.rvQueues.visibility = if (currentQueues.isNotEmpty()) View.VISIBLE else View.GONE
            binding.layoutEmptyBookshelf.visibility = if (currentQueues.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmptyTitle.text = "Belum Ada Antrian Buku"
            binding.tvEmptySubtitle.text = "Saat buku favorit Anda habis stok, Anda dapat bergabung dalam antrian"
        }
    }

    private fun showReturnDialog(loan: Loan) {
        AlertDialog.Builder(requireContext())
            .setTitle("Kembalikan Buku")
            .setMessage("Apakah Anda yakin ingin mengembalikan buku \"${loan.judulBuku}\"?")
            .setPositiveButton("Kembalikan") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = loanRepository.returnBook(loan.id)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(requireContext(), "Buku berhasil dikembalikan", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCancelQueueDialog(queue: QueueEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Batalkan Antrian")
            .setMessage("Apakah Anda yakin ingin keluar dari antrian buku \"${queue.judulBuku}\"?")
            .setPositiveButton("Batalkan Antrian") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = queueRepository.cancelQueue(queue.id, queue.bookId)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(requireContext(), "Berhasil membatalkan antrian", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun claimQueue(queue: QueueEntry) {
        val uid = authRepository.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val bookRes = bookRepository.getBookById(queue.bookId)
            val book = bookRes.getOrNull()
            if (book == null) {
                Toast.makeText(requireContext(), "Data buku tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = queueRepository.claimAvailableBook(queue.id, uid, book)
            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Buku berhasil dipinjam dari antrian!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(requireContext(), "Gagal klaim: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
