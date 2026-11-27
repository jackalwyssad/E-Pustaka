package com.example.perpustakaan2.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.perpustakaan2.adapter.ManageCategoryAdapter
import com.example.perpustakaan2.data.model.Category
import com.example.perpustakaan2.data.repository.CategoryRepository
import com.example.perpustakaan2.databinding.ActivityManageCategoriesBinding
import com.example.perpustakaan2.databinding.DialogCategoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ManageCategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCategoriesBinding
    private val categoryRepository = CategoryRepository()
    private lateinit var adapter: ManageCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeCategories()
    }

    private fun setupToolbar() {
        binding.toolbarCategories.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ManageCategoryAdapter(
            onEditClick = { category -> showCategoryDialog(category) },
            onDeleteClick = { category -> showDeleteCategoryDialog(category) }
        )
        binding.rvManageCategories.apply {
            layoutManager = LinearLayoutManager(this@ManageCategoriesActivity)
            this.adapter = this@ManageCategoriesActivity.adapter
        }
    }

    private fun setupListeners() {
        binding.fabAddCategory.setOnClickListener {
            showCategoryDialog(null)
        }
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            categoryRepository.getCategoriesFlow().collectLatest { list ->
                adapter.submitList(list)
                binding.tvEmptyCategories.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showCategoryDialog(existing: Category?) {
        val dialogBinding = DialogCategoryBinding.inflate(LayoutInflater.from(this))
        val isEdit = existing != null

        if (isEdit) {
            dialogBinding.tvDialogCategoryTitle.text = "Edit Kategori"
            dialogBinding.etDialogCategoryName.setText(existing!!.namaKategori)
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = dialogBinding.etDialogCategoryName.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Nama kategori tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val res = if (isEdit) {
                        categoryRepository.updateCategory(existing!!.copy(namaKategori = name, iconEmoji = ""))
                    } else {
                        categoryRepository.addCategory(Category(namaKategori = name, iconEmoji = ""))
                    }

                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@ManageCategoriesActivity, "Kategori disimpan", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@ManageCategoriesActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Kategori")
            .setMessage("Apakah Anda yakin ingin menghapus kategori \"${category.namaKategori}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    val res = categoryRepository.deleteCategory(category.id)
                    res.fold(
                        onSuccess = {
                            Toast.makeText(this@ManageCategoriesActivity, "Kategori dihapus", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@ManageCategoriesActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
