package com.andcodedit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persisted code file / editor buffer. This is the unit the editor and the
 * runner share: a name, the language id (see [com.andcodedit.lang.LanguageRegistry]),
 * the text content, and bookkeeping timestamps.
 *
 * Files live in app-private storage (the Room DB) so unsaved work survives
 * process death and app restarts, independent of any external SAF document.
 */
@Entity(tableName = "project_files")
data class ProjectFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val languageId: String,
    val content: String,
    /** Optional SAF/content:// or filesystem path this buffer was opened from. */
    val sourceUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
