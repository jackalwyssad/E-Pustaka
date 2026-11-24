package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.QueueEntry
import com.example.perpustakaan2.databinding.ItemQueueBinding

class QueueAdapter(
    private val onClaimClick: (QueueEntry) -> Unit,
    private val onCancelClick: (QueueEntry) -> Unit
) : ListAdapter<QueueEntry, QueueAdapter.QueueViewHolder>(DiffCallback) {

    inner class QueueViewHolder(private val binding: ItemQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(queue: QueueEntry) {
            binding.tvQueueTitle.text = queue.bookTitle
            binding.tvQueueNumber.text = "Nomor Urut Antrian: #${queue.position}"
            binding.tvQueueEstimate.text = if (queue.status == "READY") {
                "Buku sudah tersedia! Segera klaim sebelum hangus."
            } else {
                "Estimasi giliran Anda: ~${queue.position * 2} hari"
            }

            if (queue.bookCoverUrl.isNotBlank()) {
                binding.ivQueueCover.load(queue.bookCoverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_book)
                    error(R.drawable.ic_book)
                }
            } else {
                binding.ivQueueCover.setImageResource(R.drawable.ic_book)
            }

            if (queue.status == "READY") {
                binding.btnClaimQueue.visibility = View.VISIBLE
                binding.btnClaimQueue.setOnClickListener { onClaimClick(queue) }
            } else {
                binding.btnClaimQueue.visibility = View.GONE
            }

            binding.btnCancelQueue.setOnClickListener { onCancelClick(queue) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<QueueEntry>() {
        override fun areItemsTheSame(oldItem: QueueEntry, newItem: QueueEntry) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: QueueEntry, newItem: QueueEntry) =
            oldItem == newItem
    }
}
