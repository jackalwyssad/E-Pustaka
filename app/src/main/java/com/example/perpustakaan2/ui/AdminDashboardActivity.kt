package com.example.perpustakaan2.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.AdminBookAdapter
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.CategoryRepository
import com.example.perpustakaan2.data.repository.UserRepository
import com.example.perpustakaan2.databinding.ActivityAdminDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding

    private val bookRepository = BookRepository()
    private val categoryRepository = CategoryRepository()
    private val userRepository = UserRepository()

    private lateinit var adminBookAdapter: AdminBookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbarAdmin.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adminBookAdapter = AdminBookAdapter(
            onEditClick = { book ->
                val intent = Intent(this, AddEditBookActivity::class.java).apply {
                    putExtra(AddEditBookActivity.EXTRA_BOOK_ID, book.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { book ->
                showDeleteBookDialog(book)
            }
        )
        binding.rvAdminBooks.apply {
            layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
            adapter = adminBookAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnManageCategories.setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java))
        }

        binding.btnManageUsers.setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }

        binding.fabAddBook.setOnClickListener {
            startActivity(Intent(this, AddEditBookActivity::class.java))
        }

        binding.btnSeedDummy.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Inisialisasi Data Contoh")
                .setMessage("Apakah Anda ingin mengisi database dengan katalog buku dan kategori bawaan?")
                .setPositiveButton("Mulai Seed") { _, _ ->
                    lifecycleScope.launch {
                        val res = bookRepository.seedDummyDataToFirestore()
                        res.fold(
                            onSuccess = {
                                Toast.makeText(this@AdminDashboardActivity, "Data contoh berhasil dimuat!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(this@AdminDashboardActivity, "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            bookRepository.getBooksFlow().collectLatest { books ->
                adminBookAdapter.submitList(books)
                binding.tvAdminTotalBooks.text = "${books.size}"
                binding.tvAdminEmptyBooks.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            categoryRepository.getCategoriesFlow().collectLatest { cats ->
                binding.tvAdminTotalCategories.text = "${cats.size}"
            }
        }

        lifecycleScope.launch {
            userRepository.getAllUsersFlow().collectLatest { users ->
                binding.tvAdminTotalUsers.text = "${users.size}"
            }
        }
    }

    private fun showDeleteBookDialog(book: Book) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Buku")
            .setMessage("Apakah Anda yakin ingin menghapus buku \"${book.judul}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    val res = bookRepository.deleteBook(book.id)
                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@AdminDashboardActivity, "Buku berhasil dihapus", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@AdminDashboardActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
