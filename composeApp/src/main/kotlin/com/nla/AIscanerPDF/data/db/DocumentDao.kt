package com.nla.AIscanerPDF.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    @Query("SELECT * FROM pages ORDER BY documentId, position")
    fun observeAllPages(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    fun observePages(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    suspend fun getPages(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: String)

    @Query("UPDATE documents SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchDocument(id: String, updatedAt: Long)

    @Transaction
    suspend fun reorderPages(documentId: String, orderedIds: List<String>, now: Long) {
        val pages = getPages(documentId).associateBy { it.id }
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            pages[id]?.copy(position = index)
        }
        updatePages(updated)
        touchDocument(documentId, now)
    }
}
