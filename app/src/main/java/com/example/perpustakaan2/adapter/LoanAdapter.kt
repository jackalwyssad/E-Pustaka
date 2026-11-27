package com.example.perpustakaan2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.perpustakaan2.R
import com.example.perpustakaan2.data.model.Loan
import com.example.perpustakaan2.databinding.ItemLoanBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoanAdapter(
    private val isHistoryMode: Boolean = false,
    private val onReadClick: (Loan) -> Unit,
    private val onReturnClick: ((Loan) -> Unit)? = null
) : ListAdapter<Loan, LoanAdapter.LoanViewHolder>(LoanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoanViewHolder {
        val binding = ItemLoanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LoanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LoanViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LoanViewHolder(private val binding: ItemLoanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(loan: Loan) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
            binding.tvLoanTitle.text = loan.judulBuku

            if (isHistoryMode) {
                binding.tvLoanStatus.text = "STATUS: ${loan.status.uppercase()}"
                binding.tvLoanDueDate.text = "Dipinjam: ${dateFormat.format(Date(loan.tanggalPinjam))} (${loan.tipe.uppercase()})"
                binding.tvReadingProgress.text = "Terakhir dibaca: Hal ${loan.halamanTerakhir}"
                binding.btnReturnLoan.visibility = android.view.View.GONE
                binding.btnReadLoan.text = "Buka Kembali"
            } else {
                if (loan.tipe == "kilat") {
                    val remainingMillis = loan.tanggalJatuhTempo - System.currentTimeMillis()
                    if (remainingMillis <= 0) {
                        binding.tvLoanStatus.text = "SESI KILAT (KEDALUWARSA)"
                        binding.tvLoanDueDate.text = "Waktu 2 jam telah habis. Sedang proses pengembalian."
                    } else {
                        val hours = remainingMillis / (3600 * 1000)
                        val minutes = (remainingMillis % (3600 * 1000)) / (60 * 1000)
                        binding.tvLoanStatus.text = "SESI KILAT (2 JAM)"
                        binding.tvLoanDueDate.text = "Sisa Waktu: $hours jam $minutes menit"
                    }
                    binding.btnReturnLoan.text = "Akhiri Sesi"
                } else {
                    binding.tvLoanStatus.text = "PINJAMAN AKTIF (7 HARI)"
                    val dueStr = dateFormat.format(Date(loan.tanggalJatuhTempo))
                    binding.tvLoanDueDate.text = "Jatuh tempo: $dueStr"
                    binding.btnReturnLoan.text = "Kembalikan"
                }
                binding.tvReadingProgress.text = "Progres: Halaman ${loan.halamanTerakhir} dari ${if (loan.jumlahHalaman > 0) loan.jumlahHalaman else '?'}"
                binding.btnReturnLoan.visibility = android.view.View.VISIBLE
                binding.btnReadLoan.text = "Lanjut Baca (Hal ${loan.halamanTerakhir})"
            }

            if (loan.urlSampul.isNotEmpty()) {
                binding.ivLoanCover.load(loan.urlSampul) { crossfade(true) }
            } else {
                binding.ivLoanCover.setImageResource(R.drawable.ic_book)
            }

            binding.btnReadLoan.setOnClickListener { onReadClick(loan) }
            binding.btnReturnLoan.setOnClickListener { onReturnClick?.invoke(loan) }
        }
    }

    class LoanDiffCallback : DiffUtil.ItemCallback<Loan>() {
        override fun areItemsTheSame(oldItem: Loan, newItem: Loan): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Loan, newItem: Loan): Boolean = oldItem == newItem
    }
}
