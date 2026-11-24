package com.example.perpustakaan2.data.repository

import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.Loan
import com.example.perpustakaan2.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LoanRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val loansCollection = firestore.collection(Constants.COLLECTION_LOANS)
    private val booksCollection = firestore.collection(Constants.COLLECTION_BOOKS)
    private val queueCollection = firestore.collection(Constants.COLLECTION_QUEUE)
    private val usersCollection = firestore.collection(Constants.COLLECTION_USERS)

    suspend fun borrowBook(userId: String, book: Book): Result<String> {
        return try {
            val effectiveBookId = book.id.ifBlank {
                throw Exception("Identitas buku tidak valid atau belum tersimpan.")
            }

            val userLoansSnapshot = loansCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", Constants.STATUS_AKTIF)
                .whereEqualTo("tipe", Constants.TIPE_PINJAM)
                .get()
                .await()

            if (userLoansSnapshot.size() >= Constants.BATAS_PINJAM_AKTIF) {
                throw Exception("Batas maksimal peminjaman (${Constants.BATAS_PINJAM_AKTIF} buku) telah tercapai. Harap kembalikan buku yang sedang dipinjam terlebih dahulu.")
            }

            val alreadyBorrowed = userLoansSnapshot.documents.any { it.getString("bookId") == effectiveBookId }
            if (alreadyBorrowed) {
                throw Exception("Anda sudah sedang meminjam buku ini.")
            }

            val bookRef = booksCollection.document(effectiveBookId)
            val bookDoc = bookRef.get().await()
            val currentBook = if (bookDoc.exists()) {
                Book.fromSnapshot(bookDoc)
            } else {
                bookRef.set(book).await()
                book
            }

            if (currentBook.stokTersedia <= 0) {
                throw Exception("Stok pinjaman buku reguler sedang habis. Silakan gunakan Sesi Baca Kilat (2 Jam) atau masuk ke Antrian.")
            }

            val loanRef = loansCollection.document()
            val now = System.currentTimeMillis()
            val dueDate = now + (Constants.MASA_PINJAM_HARI * Constants.MILLIS_PER_DAY)

            val loan = Loan(
                id = loanRef.id,
                userId = userId,
                bookId = effectiveBookId,
                judulBuku = currentBook.judul,
                urlSampul = currentBook.urlSampul,
                tipe = Constants.TIPE_PINJAM,
                tanggalPinjam = now,
                tanggalJatuhTempo = dueDate,
                terakhirDibaca = now,
                halamanTerakhir = 1,
                jumlahHalaman = currentBook.jumlahHalaman,
                status = Constants.STATUS_AKTIF,
                urlPdf = currentBook.urlPdf
            )

            loanRef.set(loan).await()

            val newAvailable = (currentBook.stokTersedia - 1).coerceAtLeast(0)
            bookRef.update(mapOf(
                "stokTersedia" to newAvailable,
                "jumlahPeminjam" to (currentBook.jumlahPeminjam + 1)
            )).await()

            try {
                val queueDocs = queueCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("bookId", effectiveBookId)
                    .get()
                    .await()
                for (doc in queueDocs.documents) {
                    doc.reference.delete().await()
                }
            } catch (_: Exception) {}

            Result.success(loanRef.id)
        } catch (e: Exception) {
            val pesan = e.localizedMessage ?: e.message ?: "Terjadi kesalahan saat meminjam buku."
            Result.failure(Exception(pesan))
        }
    }

    suspend fun startQuickSession(userId: String, book: Book): Result<String> {
        return try {
            val effectiveBookId = book.id.ifBlank {
                throw Exception("Identitas buku tidak valid atau belum tersimpan.")
            }

            val bookRef = booksCollection.document(effectiveBookId)
            val bookDoc = bookRef.get().await()
            val currentBook = if (bookDoc.exists()) {
                Book.fromSnapshot(bookDoc)
            } else {
                bookRef.set(book).await()
                book
            }

            if (currentBook.stokSesiKilat <= 0) {
                throw Exception("Slot Sesi Baca Kilat untuk buku ini sedang penuh. Silakan coba beberapa saat lagi atau gunakan pinjam reguler.")
            }

            val loanRef = loansCollection.document()
            val now = System.currentTimeMillis()
            val dueDate = now + (Constants.MASA_SESI_KILAT_JAM * Constants.MILLIS_PER_HOUR)

            val loan = Loan(
                id = loanRef.id,
                userId = userId,
                bookId = effectiveBookId,
                judulBuku = currentBook.judul,
                urlSampul = currentBook.urlSampul,
                tipe = Constants.TIPE_KILAT,
                tanggalPinjam = now,
                tanggalJatuhTempo = dueDate,
                terakhirDibaca = now,
                halamanTerakhir = 1,
                jumlahHalaman = currentBook.jumlahHalaman,
                status = Constants.STATUS_AKTIF,
                urlPdf = currentBook.urlPdf
            )

            loanRef.set(loan).await()

            val newKilat = (currentBook.stokSesiKilat - 1).coerceAtLeast(0)
            bookRef.update("stokSesiKilat", newKilat).await()

            Result.success(loanRef.id)
        } catch (e: Exception) {
            val pesan = e.localizedMessage ?: e.message ?: "Terjadi kesalahan saat memulai Sesi Baca Kilat."
            Result.failure(Exception(pesan))
        }
    }

    suspend fun returnBook(loanId: String): Result<Unit> {
        return try {
            val loanDoc = loansCollection.document(loanId).get().await()
            val loan = if (loanDoc.exists()) Loan.fromSnapshot(loanDoc) else null
                ?: throw Exception("Data peminjaman tidak ditemukan di database.")

            if (loan.status != Constants.STATUS_AKTIF && loan.status != Constants.STATUS_DIPERPANJANG) {
                return Result.success(Unit)
            }

            loansCollection.document(loanId).update("status", Constants.STATUS_DIKEMBALIKAN).await()

            val bookRef = booksCollection.document(loan.bookId)
            val bookDoc = bookRef.get().await()

            if (loan.tipe == Constants.TIPE_KILAT) {
                if (bookDoc.exists()) {
                    val currentKilat = bookDoc.getLong("stokSesiKilat")?.toInt() ?: 0
                    bookRef.update("stokSesiKilat", currentKilat + 1).await()
                }
            } else {
                val queueSnap = queueCollection
                    .whereEqualTo("bookId", loan.bookId)
                    .whereEqualTo("status", Constants.STATUS_MENUNGGU)
                    .get()
                    .await()

                val nextUserDoc = queueSnap.documents.minByOrNull { it.getLong("nomorUrut") ?: Long.MAX_VALUE }
                if (nextUserDoc != null) {
                    nextUserDoc.reference.update("status", Constants.STATUS_TERSEDIA).await()
                } else {
                    if (bookDoc.exists()) {
                        val currentAvailable = bookDoc.getLong("stokTersedia")?.toInt() ?: 0
                        bookRef.update("stokTersedia", currentAvailable + 1).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mengembalikan buku: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun updateReadingProgress(
        loanId: String,
        userId: String,
        page: Int,
        totalBookPages: Int
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val updateMap = mutableMapOf<String, Any>(
                "halamanTerakhir" to page,
                "terakhirDibaca" to now
            )
            if (totalBookPages > 0) {
                updateMap["jumlahHalaman"] = totalBookPages
            }
            loansCollection.document(loanId).update(updateMap).await()

            try {
                val userRef = usersCollection.document(userId)
                val userDoc = userRef.get().await()
                if (userDoc.exists()) {
                    val totalHalaman = (userDoc.getLong("totalHalamanDibaca") ?: 0) + 1
                    val lastRead = userDoc.getLong("terakhirBaca") ?: 0L
                    var streak = (userDoc.getLong("streakHari") ?: 0).toInt()

                    val dayInMillis = Constants.MILLIS_PER_DAY
                    val diffDays = (now / dayInMillis) - (lastRead / dayInMillis)

                    if (diffDays == 1L) {
                        streak += 1
                    } else if (diffDays > 1L) {
                        streak = 1
                    } else if (diffDays == 0L && streak == 0) {
                        streak = 1
                    }

                    userRef.update(mapOf(
                        "totalHalamanDibaca" to totalHalaman,
                        "streakHari" to streak,
                        "terakhirBaca" to now
                    )).await()
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal menyimpan riwayat baca: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun getActiveLoansFlow(userId: String): Flow<List<Loan>> = callbackFlow {
        val listener = loansCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    Loan.fromSnapshot(doc)
                } ?: emptyList()
                val active = list.filter { it.status == Constants.STATUS_AKTIF || it.status == Constants.STATUS_DIPERPANJANG }
                    .sortedByDescending { it.tanggalPinjam }
                trySend(active)
            }
        awaitClose { listener.remove() }
    }

    fun getLoanHistoryFlow(userId: String): Flow<List<Loan>> = callbackFlow {
        val listener = loansCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    Loan.fromSnapshot(doc)
                } ?: emptyList()
                trySend(list.sortedByDescending { it.tanggalPinjam })
            }
        awaitClose { listener.remove() }
    }

    suspend fun getActiveLoanForBook(userId: String, bookId: String): Loan? {
        return try {
            val docs = loansCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()
            docs.documents.mapNotNull { Loan.fromSnapshot(it) }
                .firstOrNull { it.status == Constants.STATUS_AKTIF || it.status == Constants.STATUS_DIPERPANJANG }
        } catch (e: Exception) {
            null
        }
    }
}
