package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MemoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun getEnabledMemories(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(memory: MemoryItemEntity)

    @Update
    fun update(memory: MemoryItemEntity)

    @Delete
    fun delete(memory: MemoryItemEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    fun deleteById(id: String)
}
