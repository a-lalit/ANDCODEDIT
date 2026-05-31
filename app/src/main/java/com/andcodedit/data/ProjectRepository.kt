package com.andcodedit.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Thin repository over [ProjectFileDao] so screens/ViewModels don't touch Room
 * directly. Persists editor buffers and exposes them as a reactive [Flow].
 */
class ProjectRepository(private val dao: ProjectFileDao) {

    fun observeFiles(): Flow<List<ProjectFile>> = dao.observeAll()

    suspend fun allFiles(): List<ProjectFile> = dao.getAll()

    suspend fun load(id: Long): ProjectFile? = dao.getById(id)

    /** Insert or update; returns the row id. Stamps [ProjectFile.updatedAt]. */
    suspend fun save(file: ProjectFile): Long =
        dao.upsert(file.copy(updatedAt = System.currentTimeMillis()))

    suspend fun create(name: String, languageId: String, content: String): Long =
        dao.insert(ProjectFile(name = name, languageId = languageId, content = content))

    suspend fun delete(id: Long) = dao.deleteById(id)

    companion object {
        @Volatile
        private var INSTANCE: ProjectRepository? = null

        fun get(context: Context): ProjectRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProjectRepository(
                    AndCodeDatabase.get(context).projectFileDao()
                ).also { INSTANCE = it }
            }
    }
}
