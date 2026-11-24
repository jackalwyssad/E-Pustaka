package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.databinding.ItemAdminBookBinding

class AdminBookAdapter(
    private val onEditClick: (Book) -> Unit,
    private val onDeleteClick: (Book) -> Unit,
) : ListAdapter<Book, AdminBookAdapter.AdminBookViewHolder>(DiffCallback) {

    inner class AdminBookViewHolder(private val binding: ItemAdminBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: Book) {
            binding.tvAdminBookTitle.text = book.title
            binding.tvAdminBookAuthor.text = book.author
            binding.tvAdminBookCategory.text = "Kategori: ${book.category}"
            binding.tvAdminBookStock.text = "Stok: ${book.availableStock}/${book.totalStock} • Kilat: ${book.quickReadSlots} • Antrian: ${book.queueCount}"

            if (book.coverUrl.isNotBlank()) {
                binding.ivAdminBookCover.load(book.coverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_book)
                    error(R.drawable.ic_book)
                }
            } else {
                binding.ivAdminBookCover.setImageResource(R.drawable.ic_book)
            }

            binding.btnEditBook.setOnClickListener { onEditClick(book) }
            binding.btnDeleteBook.setOnClickListener { onDeleteClick(book) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminBookViewHolder {
        val binding = ItemAdminBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminBookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminBookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Book, newItem: Book) =
            oldItem == newItem
    }
}
