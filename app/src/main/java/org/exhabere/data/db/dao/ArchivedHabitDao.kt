package org.exhabere.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.exhabere.data.db.entity.ArchivedHabitEntity
import org.exhabere.data.db.entity.ArchivedHabitSummary

@Dao
interface ArchivedHabitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(archive: ArchivedHabitEntity): Long

    @Query("SELECT id, habit_name, archived_at_millis FROM archived_habits ORDER BY archived_at_millis DESC")
    fun getSummariesFlow(): Flow<List<ArchivedHabitSummary>>

    @Query("SELECT * FROM archived_habits WHERE id = :archiveId LIMIT 1")
    suspend fun getById(archiveId: Long): ArchivedHabitEntity?
}
