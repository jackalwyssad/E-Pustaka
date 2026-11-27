package com.example.perpustakaan2.data.repository

import android.content.Context
import android.net.Uri
import com.example.perpustakaan2.data.model.Book
import com.example.perpustakaan2.data.model.Review
import com.example.perpustakaan2.util.Constants
import com.example.perpustakaan2.util.DummyData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BookRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val booksCollection = firestore.collection(Constants.COLLECTION_BOOKS)
    private val categoriesCollection = firestore.collection(Constants.COLLECTION_CATEGORIES)
    private val reviewsCollection = firestore.collection(Constants.COLLECTION_REVIEWS)
    private val favoritesCollection = firestore.collection(Constants.COLLECTION_FAVORITES)
    private val usersCollection = firestore.collection(Constants.COLLECTION_USERS)

    fun getBooksFlow(): Flow<List<Book>> = callbackFlow {
        val listener = booksCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(DummyData.sampleBooks)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                Book.fromSnapshot(doc)
            } ?: emptyList()

            if (list.isEmpty()) {
                trySend(DummyData.sampleBooks)
            } else {
                trySend(list.sortedByDescending { it.tanggalUpload })
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun getBookById(bookId: String): Result<Book> {
        return try {
            val doc = booksCollection.document(bookId).get().await()
            val book = if (doc.exists()) Book.fromSnapshot(doc)
                else DummyData.sampleBooks.find { it.id == bookId }
                ?: throw Exception("Buku tidak ditemukan")
            Result.success(book)
        } catch (e: Exception) {
            val dummy = DummyData.sampleBooks.find { it.id == bookId }
            if (dummy != null) Result.success(dummy)
            else Result.failure(e)
        }
    }

    suspend fun seedDummyDataToFirestore(): Result<Unit> {
        return try {
            val batch = firestore.batch()

            for (cat in DummyData.sampleCategories) {
                val catRef = categoriesCollection.document(cat.id)
                batch.set(catRef, cat)
            }

            for (book in DummyData.sampleBooks) {
                val bookRef = booksCollection.document(book.id)
                batch.set(bookRef, book)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveOrUploadFile(
        context: Context,
        uri: Uri,
        folder: String,
        extension: String
    ): Result<String> {
        return try {
            val targetDir = File(context.filesDir, folder)
            if (!targetDir.exists()) targetDir.mkdirs()
            val localFile = File(targetDir, "${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Gagal membaca berkas dari galeri/penyimpanan HP.")
            Result.success("file://${localFile.absolutePath}")
        } catch (localError: Exception) {
            Result.failure(localError)
        }
    }

    suspend fun addBook(book: Book): Result<String> {
        return try {
            val docRef = if (book.id.isNotBlank()) booksCollection.document(book.id) else booksCollection.document()
            val newBook = book.copy(
                id = docRef.id,
                stokTersedia = if (book.stokTersedia > 0) book.stokTersedia else book.stokAwal,
                tanggalUpload = if (book.tanggalUpload > 0) book.tanggalUpload else System.currentTimeMillis()
            )
            docRef.set(newBook).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBook(book: Book): Result<Unit> {
        return try {
            booksCollection.document(book.id).set(book).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBook(bookId: String): Result<Unit> {
        return try {
            // 1. Hapus dokumen buku utama
            booksCollection.document(bookId).delete().await()

            // 2. Hentikan semua peminjaman aktif/diperpanjang untuk buku ini
            try {
                val now = System.currentTimeMillis()
                val loanDocs = firestore.collection(Constants.COLLECTION_LOANS)
                    .whereEqualTo("bookId", bookId)
                    .get()
                    .await()
                for (doc in loanDocs.documents) {
                    val status = doc.getString("status") ?: ""
                    if (status == Constants.STATUS_AKTIF || status == Constants.STATUS_DIPERPANJANG) {
                        doc.reference.update(
                            mapOf(
                                "status" to Constants.STATUS_BUKU_DIHAPUS,
                                "tanggalKembali" to now
                            )
                        ).await()
                    }
                }
            } catch (_: Exception) {}

            // 3. Hapus semua antrian untuk buku ini
            try {
                val queueDocs = firestore.collection(Constants.COLLECTION_QUEUE)
                    .whereEqualTo("bookId", bookId)
                    .get()
                    .await()
                for (doc in queueDocs.documents) {
                    doc.reference.delete().await()
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBookPageCount(bookId: String, totalPages: Int): Result<Unit> {
        return try {
            booksCollection.document(bookId).update("jumlahHalaman", totalPages).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recalculateDynamicStock(bookId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val bookDoc = transaction.get(booksCollection.document(bookId))
                val book = bookDoc.toObject(Book::class.java) ?: return@runTransaction

                val threshold = book.stokAwal * Constants.MULTIPLIER_ANTRIAN_UNTUK_TAMBAH_STOK
                if (book.jumlahAntrian > threshold) {
                    val bonusStok = (book.jumlahAntrian - threshold) / Constants.PEMBAGI_STOK_DINAMIS
                    val newTotal = (book.stokAwal + bonusStok).coerceAtMost(book.stokMaksimal)
                    val activeBorrowers = book.stokAwal - book.stokTersedia
                    val newAvailable = (newTotal - activeBorrowers).coerceAtLeast(0)

                    transaction.update(booksCollection.document(bookId), mapOf(
                        "stokTersedia" to newAvailable
                    ))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getReviewsFlow(bookId: String): Flow<List<Review>> = callbackFlow {
        val listener = reviewsCollection.whereEqualTo("bookId", bookId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val rawList = snapshot?.documents?.mapNotNull { doc ->
                    Review.fromSnapshot(doc)
                } ?: emptyList()

                trySend(rawList.sortedByDescending { it.tanggal })

                CoroutineScope(Dispatchers.IO).launch {
                    val userIds = rawList.map { it.userId }.filter { it.isNotBlank() }.distinct()
                    val userProfiles = mutableMapOf<String, Pair<String?, String?>>()
                    for (uId in userIds) {
                        try {
                            val uDoc = usersCollection.document(uId).get().await()
                            if (uDoc.exists()) {
                                userProfiles[uId] = Pair(uDoc.getString("nama"), uDoc.getString("fotoProfil"))
                            }
                        } catch (_: Exception) {}
                    }

                    if (userProfiles.isNotEmpty()) {
                        val updatedList = rawList.map { rev ->
                            val profile = userProfiles[rev.userId]
                            if (profile != null) {
                                rev.copy(
                                    namaUser = profile.first?.ifBlank { rev.namaUser } ?: rev.namaUser,
                                    fotoProfil = profile.second ?: rev.fotoProfil
                                )
                            } else {
                                rev
                            }
                        }
                        trySend(updatedList.sortedByDescending { it.tanggal })
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun addReview(review: Review): Result<Unit> {
        return try {
            val loanDocs = firestore.collection(Constants.COLLECTION_LOANS)
                .whereEqualTo("userId", review.userId)
                .whereEqualTo("bookId", review.bookId)
                .get()
                .await()

            val hasBorrowed = loanDocs.documents.any { doc ->
                val tipe = doc.getString("tipe") ?: ""
                tipe == Constants.TIPE_PINJAM || tipe == Constants.TIPE_KILAT
            }

            if (!hasBorrowed) {
                return Result.failure(Exception("Hanya pembaca yang pernah meminjam buku atau menggunakan Sesi Kilat yang dapat memberikan ulasan."))
            }

            val reviewRef = reviewsCollection.document()
            reviewRef.set(review.copy(id = reviewRef.id)).await()
            recalculateBookRating(review.bookId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mengirim ulasan: ${Constants.translateError(e)}"))
        }
    }

    suspend fun updateReview(reviewId: String, bookId: String, userId: String, newRating: Int, newUlasan: String): Result<Unit> {
        return try {
            val reviewRef = reviewsCollection.document(reviewId)
            val doc = reviewRef.get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Ulasan tidak ditemukan."))
            }
            val authorId = doc.getString("userId") ?: ""
            if (authorId != userId) {
                return Result.failure(Exception("Anda hanya dapat mengedit ulasan milik Anda sendiri."))
            }

            reviewRef.update(mapOf(
                "rating" to newRating,
                "ulasan" to newUlasan,
                "tanggal" to System.currentTimeMillis()
            )).await()

            recalculateBookRating(bookId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal memperbarui ulasan: ${Constants.translateError(e)}"))
        }
    }

    suspend fun deleteReview(reviewId: String, bookId: String, userId: String): Result<Unit> {
        return try {
            val reviewRef = reviewsCollection.document(reviewId)
            val doc = reviewRef.get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Ulasan tidak ditemukan."))
            }
            val authorId = doc.getString("userId") ?: ""
            if (authorId != userId) {
                return Result.failure(Exception("Anda hanya dapat menghapus ulasan milik Anda sendiri."))
            }

            reviewRef.delete().await()
            recalculateBookRating(bookId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal menghapus ulasan: ${Constants.translateError(e)}"))
        }
    }

    private suspend fun recalculateBookRating(bookId: String) {
        try {
            val reviewsSnapshot = reviewsCollection.whereEqualTo("bookId", bookId).get().await()
            val bookRef = booksCollection.document(bookId)
            if (reviewsSnapshot.isEmpty) {
                bookRef.update(mapOf(
                    "jumlahRating" to 0,
                    "rataRating" to 0f
                )).await()
            } else {
                val ratings = reviewsSnapshot.documents.mapNotNull {
                    when (val r = it.get("rating")) {
                        is Number -> r.toFloat()
                        is String -> r.toFloatOrNull()
                        else -> null
                    }
                }
                val count = ratings.size
                val avg = if (count > 0) ratings.average().toFloat() else 0f
                bookRef.update(mapOf(
                    "jumlahRating" to count,
                    "rataRating" to avg
                )).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun toggleFavorite(userId: String, bookId: String): Result<Boolean> {
        return try {
            val query = favoritesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()

            if (query.isEmpty) {
                val favDoc = favoritesCollection.document()
                favDoc.set(mapOf(
                    "id" to favDoc.id,
                    "userId" to userId,
                    "bookId" to bookId,
                    "tanggal" to System.currentTimeMillis()
                )).await()
                Result.success(true)
            } else {
                for (doc in query.documents) {
                    doc.reference.delete().await()
                }
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isBookFavorite(userId: String, bookId: String): Boolean {
        return try {
            val query = favoritesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()
            !query.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    fun isFavoriteFlow(userId: String, bookId: String): Flow<Boolean> = callbackFlow {
        val listener = favoritesCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("bookId", bookId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                val isFav = snapshot != null && !snapshot.isEmpty
                trySend(isFav)
            }
        awaitClose { listener.remove() }
    }

    fun getUserFavoriteBookIdsFlow(userId: String): Flow<List<String>> = callbackFlow {
        val listener = favoritesCollection.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.mapNotNull { it.getString("bookId") } ?: emptyList()
                trySend(ids)
            }
        awaitClose { listener.remove() }
    }
}
