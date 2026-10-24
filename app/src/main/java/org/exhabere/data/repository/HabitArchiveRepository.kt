package org.exhabere.data.repository

import kotlinx.coroutines.flow.Flow
import org.exhabere.data.backup.HabitBackup
import org.exhabere.data.db.entity.ArchivedHabitSummary

data class ArchivedHabitRecord(
    val id: Long,
    val archivedAtMillis: Long,
    val backup: HabitBackup
)

interface HabitArchiveRepository {
    fun getArchivedHabits(): Flow<List<ArchivedHabitSummary>>

    /** Saves the mastered habit's full backup, then starts a fresh Observation habit atomically. */
    suspend fun archiveMasteredHabitAndStartFresh(): Long

    /** Full history is loaded only after the user opens an archive in Settings. */
    suspend fun getArchivedHabit(archiveId: Long): ArchivedHabitRecord?
}
