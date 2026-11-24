package com.example.perpustakaan2.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.BookAdapter
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.databinding.ActivityFavoritesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private val authRepository = AuthRepository()
    private val bookRepository = BookRepository()

    private lateinit var bookAdapter: BookAdapter
    private var allBooks: List<Book> = emptyList()
    private var favoriteIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeFavorites()
    }

    private fun setupToolbar() {
        binding.toolbarFavorites.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        bookAdapter = BookAdapter { book ->
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra(BookDetailActivity.EXTRA_BOOK_ID, book.id)
            }
            startActivity(intent)
        }
        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(this@FavoritesActivity)
            adapter = bookAdapter
        }
    }

    private fun observeFavorites() {
        val uid = authRepository.currentUser?.uid ?: return

        lifecycleScope.launch {
            bookRepository.getBooksFlow().collectLatest { books ->
                allBooks = books
                filterFavorites()
            }
        }

        lifecycleScope.launch {
            bookRepository.getUserFavoriteBookIdsFlow(uid).collectLatest { ids ->
                favoriteIds = ids
                filterFavorites()
            }
        }
    }

    private fun filterFavorites() {
        val favBooks = allBooks.filter { favoriteIds.contains(it.id) }
        bookAdapter.submitList(favBooks)
        binding.layoutEmptyFavorites.visibility = if (favBooks.isEmpty()) View.VISIBLE else View.GONE
        binding.rvFavorites.visibility = if (favBooks.isEmpty()) View.GONE else View.VISIBLE
    }
}
