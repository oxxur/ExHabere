package org.exhabere.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** On-device snapshot of a completed habit and its complete history. */
@Entity(
    tableName = "archived_habits",
    indices = [Index(value = ["archived_at_millis"])]
)
data class ArchivedHabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "habit_name")
    val habitName: String,

    @ColumnInfo(name = "archived_at_millis")
    val archivedAtMillis: Long,

    /** Versioned local backup JSON so archived logs, tags, and settings stay together. */
    @ColumnInfo(name = "backup_json")
    val backupJson: String
)

/** Lightweight row used for the collapsed Settings archive list. */
data class ArchivedHabitSummary(
    val id: Long,
    @ColumnInfo(name = "habit_name")
    val habitName: String,
    @ColumnInfo(name = "archived_at_millis")
    val archivedAtMillis: Long
)
