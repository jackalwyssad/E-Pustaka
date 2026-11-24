package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.User
import com.example.perpustakaan2.databinding.ItemUserBinding

class UserAdapter(
    private val onToggleRoleClick: (User) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback) {

    inner class UserViewHolder(private val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvUserItemName.text = user.displayName.ifBlank { "Pengguna" }
            binding.tvUserItemEmail.text = user.email
            binding.tvUserItemStats.text = "Baca: ${user.booksCompleted} buku • Streak: ${user.streakDays} hari"

            val isAdmin = user.role.equals("admin", ignoreCase = true)
            binding.tvUserItemRoleBadge.text = if (isAdmin) "ADMIN" else "MEMBER"
            binding.tvUserItemRoleBadge.setBackgroundResource(
                if (isAdmin) R.drawable.bg_badge_gold else R.drawable.bg_badge_primary
            )
            binding.tvUserItemRoleBadge.setTextColor(
                binding.root.context.getColor(if (isAdmin) R.color.gold_star else R.color.primary)
            )

            binding.btnToggleRole.text = if (isAdmin) "Turunkan ke Member" else "Jadikan Admin"

            if (user.photoUrl.isNotBlank()) {
                binding.ivUserItemAvatar.load(user.photoUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_person)
                    error(R.drawable.ic_person)
                }
            } else {
                binding.ivUserItemAvatar.setImageResource(R.drawable.ic_person)
            }

            binding.btnToggleRole.setOnClickListener { onToggleRoleClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: User, newItem: User) =
            oldItem == newItem
    }
}
