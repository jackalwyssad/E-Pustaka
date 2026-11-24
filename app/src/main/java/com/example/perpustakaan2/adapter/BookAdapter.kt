package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.databinding.ItemBookBinding
import java.util.Locale

class BookAdapter(
    private val onBookClick: (Book) -> Unit
) : ListAdapter<Book, BookAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookViewHolder(private val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.tvTitle.text = book.judul
            binding.tvAuthor.text = "Oleh ${book.penulis}"
            binding.tvCategory.text = book.namaKategori.ifBlank { "Umum" }
            binding.tvRating.text = if (book.rataRating > 0) String.format(Locale.US, "%.1f", book.rataRating) else "-"
            binding.tvRatingCount.text = "(${book.jumlahRating})"

            if (book.stokTersedia > 0) {
                binding.tvStockStatus.text = "Tersedia: ${book.stokTersedia}/${book.stokAwal}"
                binding.tvStockStatus.setTextColor(binding.root.context.getColor(R.color.primary))
            } else {
                binding.tvStockStatus.text = "Antrian: ${book.jumlahAntrian}"
                binding.tvStockStatus.setTextColor(binding.root.context.getColor(R.color.error))
            }

            if (book.urlSampul.isNotEmpty()) {
                binding.ivCover.load(book.urlSampul) {
                    crossfade(true)
                }
            } else {
                binding.ivCover.setImageResource(R.drawable.ic_book)
            }

            binding.cardBook.setOnClickListener {
                onBookClick(book)
            }
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean = oldItem == newItem
    }
}
