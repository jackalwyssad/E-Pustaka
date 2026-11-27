package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.Review
import com.example.perpustakaan2.databinding.ItemReviewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter(
    private val currentUserId: String,
    private val onEditClick: (Review) -> Unit,
    private val onDeleteClick: (Review) -> Unit
) : ListAdapter<Review, ReviewAdapter.ReviewViewHolder>(DiffCallback) {

    inner class ReviewViewHolder(private val binding: ItemReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(review: Review) {
            binding.tvReviewUserName.text = review.userName.ifBlank { "Pembaca" }
            binding.tvReviewContent.text = review.comment
            binding.tvReviewRatingStar.text = "★ ${review.rating}"

            val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
            binding.tvReviewDate.text = dateFormat.format(Date(review.createdAt))

            if (review.userPhotoUrl.isNotBlank()) {
                binding.ivReviewUserAvatar.load(review.userPhotoUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_person)
                    error(R.drawable.ic_person)
                }
            } else {
                binding.ivReviewUserAvatar.setImageResource(R.drawable.ic_person)
            }

            val isOwner = currentUserId.isNotBlank() && review.userId == currentUserId
            binding.tvYouBadge.visibility = if (isOwner) View.VISIBLE else View.GONE
            binding.btnReviewMenu.visibility = if (isOwner) View.VISIBLE else View.GONE

            if (isOwner) {
                binding.btnReviewMenu.setOnClickListener { view ->
                    val popup = PopupMenu(view.context, view)
                    popup.menu.add(0, 1, 0, "Edit Ulasan")
                    popup.menu.add(0, 2, 1, "Hapus Ulasan")
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> {
                                onEditClick(review)
                                true
                            }
                            2 -> {
                                onDeleteClick(review)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            } else {
                binding.btnReviewMenu.setOnClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Review, newItem: Review) =
            oldItem == newItem
    }
}
