package com.example.perpustakaan2.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.BookAdapter
import com.example.perpustakaan2.adapter.CategoryChipAdapter
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.repository.BookRepository
import com.example.perpustakaan2.data.repository.CategoryRepository
import com.example.perpustakaan2.databinding.FragmentSearchBinding
import com.example.perpustakaan2.ui.BookDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val bookRepository = BookRepository()
    private val categoryRepository = CategoryRepository()

    private lateinit var categoryAdapter: CategoryChipAdapter
    private lateinit var bookAdapter: BookAdapter

    private var allBooks: List<Book> = emptyList()
    private var searchQuery: String = ""
    private var selectedCategoryId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
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
        binding.rvSearchCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        bookAdapter = BookAdapter { book ->
            val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
                putExtra(BookDetailActivity.EXTRA_BOOK_ID, book.id)
            }
            startActivity(intent)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = bookAdapter
        }
    }

    private fun setupListeners() {
        binding.etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString()?.trim() ?: ""
            filterBooks()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            categoryRepository.getCategoriesFlow().collectLatest { categories ->
                categoryAdapter.submitList(categories, selectedCategoryId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bookRepository.getBooksFlow().collectLatest { books ->
                allBooks = books
                filterBooks()
            }
        }
    }

    private fun filterBooks() {
        val queryLower = searchQuery.lowercase()
        val filtered = allBooks.filter { book ->
            val matchQuery = queryLower.isEmpty() ||
                    book.judul.lowercase().contains(queryLower) ||
                    book.penulis.lowercase().contains(queryLower) ||
                    book.sinopsis.lowercase().contains(queryLower)
            val matchCat = selectedCategoryId == null || book.kategoriId == selectedCategoryId
            matchQuery && matchCat
        }

        bookAdapter.submitList(filtered)
        binding.layoutEmptySearch.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSearchResults.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
