package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.databinding.ItemPopularBookBinding

class PopularBookAdapter(
    private val onBookClick: (Book) -> Unit
) : ListAdapter<Book, PopularBookAdapter.PopularViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PopularViewHolder {
        val binding = ItemPopularBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PopularViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PopularViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PopularViewHolder(private val binding: ItemPopularBookBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.tvPopularTitle.text = book.judul
            binding.tvPopularAuthor.text = book.penulis

            if (book.urlSampul.isNotEmpty()) {
                binding.ivPopularCover.load(book.urlSampul) {
                    crossfade(true)
                }
            } else {
                binding.ivPopularCover.setImageResource(R.drawable.ic_book)
            }

            binding.cardPopular.setOnClickListener {
                onBookClick(book)
            }
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean = oldItem == newItem
    }
}
