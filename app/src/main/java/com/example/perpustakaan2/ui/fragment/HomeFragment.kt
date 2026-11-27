package com.example.perpustakaan2.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.BookAdapter
import com.example.perpustakaan2.adapter.CategoryChipAdapter
import com.example.perpustakaan2.adapter.PopularBookAdapter
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.repository.AuthRepository
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.CategoryRepository
import com.example.perpustakaan2.databinding.FragmentHomeBinding
import com.example.perpustakaan2.ui.BookDetailActivity
import com.example.perpustakaan2.ui.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private val bookRepository = BookRepository()
    private val categoryRepository = CategoryRepository()

    private lateinit var categoryAdapter: CategoryChipAdapter
    private lateinit var popularBookAdapter: PopularBookAdapter
    private lateinit var bookAdapter: BookAdapter

    private var allBooks: List<Book> = emptyList()
    private var selectedCategoryId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryChipAdapter { category ->
            selectedCategoryId = category?.id
            filterBooks()
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        popularBookAdapter = PopularBookAdapter { book ->
            openBookDetail(book.id)
        }
        binding.rvPopularBooks.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = popularBookAdapter
        }

        bookAdapter = BookAdapter { book ->
            openBookDetail(book.id)
        }
        binding.rvBooks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = bookAdapter
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.cardSearchShortcut.setOnClickListener {
            (activity as? MainActivity)?.navigateToSearch()
        }
    }

    private fun observeData() {
        val currentUser = authRepository.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val res = authRepository.getUserProfile(currentUser.uid)
                res.fold(
                    onSuccess = { user ->
                        binding.tvGreeting.text = "Halo, ${user.nama.ifBlank { "Pembaca" }}"
                        binding.tvStreakDays.text = "${user.streakHari} Hari"
                    },
                    onFailure = {
                        binding.tvGreeting.text = "Halo, ${currentUser.displayName ?: "Pembaca"}"
                    }
                )
            }
        } else {
            binding.tvGreeting.text = "Halo, Tamu"
            binding.tvStreakDays.text = "0 Hari"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            categoryRepository.getCategoriesFlow().collectLatest { categories ->
                categoryAdapter.submitList(categories, selectedCategoryId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bookRepository.getBooksFlow().collectLatest { books ->
                allBooks = books
                val popular = books.sortedByDescending { it.jumlahPeminjam }.take(5)
                popularBookAdapter.submitList(popular)
                binding.layoutPopularSection.visibility = if (popular.isNotEmpty()) View.VISIBLE else View.GONE
                filterBooks()
            }
        }
    }

    private fun filterBooks() {
        val filtered = if (selectedCategoryId == null) {
            allBooks
        } else {
            allBooks.filter { it.kategoriId == selectedCategoryId }
        }
        bookAdapter.submitList(filtered)
        binding.tvEmptyBooks.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openBookDetail(bookId: String) {
        val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
            putExtra(BookDetailActivity.EXTRA_BOOK_ID, bookId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
