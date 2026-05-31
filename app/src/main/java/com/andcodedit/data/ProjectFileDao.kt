package com.andcodedit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room access for [ProjectFile] buffers. */
@Dao
interface ProjectFileDao {

    @Query("SELECT * FROM project_files ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectFile>>

    @Query("SELECT * FROM project_files WHERE id = :id")
    suspend fun getById(id: Long): ProjectFile?

    @Query("SELECT * FROM project_files ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ProjectFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: ProjectFile): Long

    @Update
    suspend fun update(file: ProjectFile)

    @Upsert
    suspend fun upsert(file: ProjectFile): Long

    @Delete
    suspend fun delete(file: ProjectFile)

    @Query("DELETE FROM project_files WHERE id = :id")
    suspend fun deleteById(id: Long)
}
