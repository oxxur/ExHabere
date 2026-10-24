package org.exhabere.data.repository

import org.exhabere.data.backup.HabitBackup

interface HabitBackupRepository {
    suspend fun createBackup(exportedAtMillis: Long = System.currentTimeMillis()): HabitBackup

    /** Replaces this device's current habit, logs, and context-tag cache atomically. */
    suspend fun restoreBackup(backup: HabitBackup)
}
