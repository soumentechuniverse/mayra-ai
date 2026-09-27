package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MemoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memories WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun getEnabledMemories(): Flow<List<MemoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryItemEntity)

    @Update
    suspend fun updateMemory(memory: MemoryItemEntity)

    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}
