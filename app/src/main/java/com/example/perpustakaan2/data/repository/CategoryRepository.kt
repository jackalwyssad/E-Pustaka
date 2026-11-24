package com.example.perpustakaan2.data.repository

import com.example.perpustakaan2.data.model.Category
import com.example.perpustakaan2.util.Constants
import com.example.perpustakaan2.util.DummyData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CategoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val categoriesCollection = firestore.collection(Constants.COLLECTION_CATEGORIES)

    fun getCategoriesFlow(): Flow<List<Category>> = callbackFlow {
        val listener = categoriesCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(DummyData.sampleCategories)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                Category.fromSnapshot(doc)
            } ?: emptyList()

            if (list.isEmpty()) {
                trySend(DummyData.sampleCategories)
            } else {
                trySend(list)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun addCategory(category: Category): Result<String> {
        return try {
            val docRef = if (category.id.isNotBlank()) categoriesCollection.document(category.id) else categoriesCollection.document()
            val newCategory = category.copy(id = docRef.id)
            docRef.set(newCategory).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCategory(category: Category): Result<Unit> {
        return try {
            categoriesCollection.document(category.id).set(category).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            categoriesCollection.document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
