package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY isPinned DESC, updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: ConversationEntity)

    @Update
    fun update(conversation: ConversationEntity)

    @Delete
    fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    fun deleteById(id: String)
}
