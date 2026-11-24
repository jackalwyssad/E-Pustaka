package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.Category
import com.example.perpustakaan2.databinding.ItemCategoryChipBinding

class CategoryChipAdapter(
    private var categories: List<Category> = emptyList(),
    private var selectedCategoryId: String? = null,
    private val onCategorySelected: (Category?) -> Unit
) : RecyclerView.Adapter<CategoryChipAdapter.CategoryViewHolder>() {

    fun submitList(newList: List<Category>, selectedId: String? = selectedCategoryId) {
        categories = newList
        selectedCategoryId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun getItemCount(): Int = categories.size + 1 // +1 for "Semua"

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        if (position == 0) {
            val isSelected = selectedCategoryId == null
            holder.binding.tvCategoryChip.text = "📚 Semua"
            updateChipStyle(holder.binding, isSelected)
            holder.binding.tvCategoryChip.setOnClickListener {
                selectedCategoryId = null
                notifyDataSetChanged()
                onCategorySelected(null)
            }
        } else {
            val category = categories[position - 1]
            val isSelected = selectedCategoryId == category.id
            holder.binding.tvCategoryChip.text = "${category.iconEmoji} ${category.namaKategori}"
            updateChipStyle(holder.binding, isSelected)
            holder.binding.tvCategoryChip.setOnClickListener {
                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                notifyDataSetChanged()
                onCategorySelected(if (selectedCategoryId == null) null else category)
            }
        }
    }

    private fun updateChipStyle(binding: ItemCategoryChipBinding, isSelected: Boolean) {
        val context = binding.root.context
        if (isSelected) {
            binding.tvCategoryChip.setBackgroundResource(R.drawable.bg_chip_selected)
            binding.tvCategoryChip.setTextColor(context.getColor(R.color.white))
        } else {
            binding.tvCategoryChip.setBackgroundResource(R.drawable.bg_chip)
            binding.tvCategoryChip.setTextColor(context.getColor(R.color.text_primary))
        }
    }

    class CategoryViewHolder(val binding: ItemCategoryChipBinding) : RecyclerView.ViewHolder(binding.root)
}
