package com.example.perpustakaan2.data.repository

import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.QueueEntry
import com.example.perpustakaan2.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class QueueRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val loanRepository: LoanRepository = LoanRepository(firestore),
    private val bookRepository: BookRepository = BookRepository(firestore)
) {
    private val queueCollection = firestore.collection(Constants.COLLECTION_QUEUE)
    private val booksCollection = firestore.collection(Constants.COLLECTION_BOOKS)

    suspend fun joinQueue(userId: String, book: Book): Result<Int> {
        return try {
            val effectiveBookId = book.id.ifBlank {
                throw Exception("Identitas buku tidak valid.")
            }

            val existingQueue = queueCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("bookId", effectiveBookId)
                .get()
                .await()

            val alreadyInQueue = existingQueue.documents.any {
                val status = it.getString("status")
                status == Constants.STATUS_MENUNGGU || status == Constants.STATUS_TERSEDIA
            }
            if (alreadyInQueue) {
                throw Exception("Anda sudah terdaftar dalam antrian buku ini.")
            }

            val activeQueuesSnapshot = queueCollection
                .whereEqualTo("bookId", effectiveBookId)
                .whereEqualTo("status", Constants.STATUS_MENUNGGU)
                .get()
                .await()

            val nextNumber = activeQueuesSnapshot.size() + 1
            val queueRef = queueCollection.document()

            val entry = QueueEntry(
                id = queueRef.id,
                userId = userId,
                bookId = effectiveBookId,
                judulBuku = book.judul,
                urlSampul = book.urlSampul,
                nomorUrut = nextNumber,
                tanggalDaftar = System.currentTimeMillis(),
                status = Constants.STATUS_MENUNGGU
            )

            queueRef.set(entry).await()

            val bookRef = booksCollection.document(effectiveBookId)
            val bookDoc = bookRef.get().await()
            if (bookDoc.exists()) {
                val currentQueueCount = bookDoc.getLong("jumlahAntrian")?.toInt() ?: 0
                bookRef.update("jumlahAntrian", currentQueueCount + 1).await()
            }

            bookRepository.recalculateDynamicStock(effectiveBookId)

            Result.success(nextNumber)
        } catch (e: Exception) {
            val pesan = e.localizedMessage ?: e.message ?: "Gagal bergabung ke dalam antrian."
            Result.failure(Exception(pesan))
        }
    }

    suspend fun cancelQueue(queueId: String, bookId: String): Result<Unit> {
        return try {
            val queueRef = queueCollection.document(queueId)
            val queueDoc = queueRef.get().await()
            val wasAvailable = queueDoc.exists() && queueDoc.getString("status") == Constants.STATUS_TERSEDIA

            queueRef.delete().await()

            if (wasAvailable) {
                val waitingSnap = queueCollection
                    .whereEqualTo("bookId", bookId)
                    .whereEqualTo("status", Constants.STATUS_MENUNGGU)
                    .get()
                    .await()
                val nextUserDoc = waitingSnap.documents.minByOrNull { it.getLong("nomorUrut") ?: Long.MAX_VALUE }
                if (nextUserDoc != null) {
                    nextUserDoc.reference.update("status", Constants.STATUS_TERSEDIA).await()
                } else {
                    val bookRef = booksCollection.document(bookId)
                    val bookDoc = bookRef.get().await()
                    if (bookDoc.exists()) {
                        val currentAvailable = bookDoc.getLong("stokTersedia")?.toInt() ?: 0
                        val maxStock = bookDoc.getLong("stokMaksimal")?.toInt() ?: 15
                        val newAvailable = (currentAvailable + 1).coerceAtMost(maxStock)
                        bookRef.update("stokTersedia", newAvailable).await()
                    }
                }
            }

            val bookRef = booksCollection.document(bookId)
            val bookDoc = bookRef.get().await()
            if (bookDoc.exists()) {
                val currentQueueCount = bookDoc.getLong("jumlahAntrian")?.toInt() ?: 0
                val newCount = (currentQueueCount - 1).coerceAtLeast(0)
                bookRef.update("jumlahAntrian", newCount).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal membatalkan antrian: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun getUserQueueForBook(userId: String, bookId: String): QueueEntry? {
        return try {
            val query = queueCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()

            query.documents.map { QueueEntry.fromSnapshot(it) }
                .firstOrNull { it.status == Constants.STATUS_MENUNGGU || it.status == Constants.STATUS_TERSEDIA }
        } catch (e: Exception) {
            null
        }
    }

    fun getUserQueuesFlow(userId: String): Flow<List<QueueEntry>> = callbackFlow {
        val listener = queueCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    QueueEntry.fromSnapshot(doc)
                } ?: emptyList()
                val active = list.filter { it.status == Constants.STATUS_MENUNGGU || it.status == Constants.STATUS_TERSEDIA }
                    .sortedByDescending { it.tanggalDaftar }
                trySend(active)
            }
        awaitClose { listener.remove() }
    }

    suspend fun claimAvailableBook(queueId: String, userId: String, book: Book): Result<String> {
        return try {
            val loanId = loanRepository.borrowBook(userId, book, fromQueue = true).getOrThrow()
            queueCollection.document(queueId).update("status", "dipinjam").await()

            val bookRef = booksCollection.document(book.id)
            val bookDoc = bookRef.get().await()
            if (bookDoc.exists()) {
                val currentQueueCount = bookDoc.getLong("jumlahAntrian")?.toInt() ?: 0
                val newCount = (currentQueueCount - 1).coerceAtLeast(0)
                bookRef.update("jumlahAntrian", newCount).await()
            }
            Result.success(loanId)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mengklaim buku: ${e.localizedMessage ?: e.message}"))
        }
    }
}
