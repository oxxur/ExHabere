package org.exhabere.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition

@Dao
interface HabitConfigDao {

    @Query("SELECT * FROM habit_config WHERE id = 1 LIMIT 1")
    fun getHabitConfigFlow(): Flow<HabitConfigEntity?>

    @Query("SELECT * FROM habit_config WHERE id = 1 LIMIT 1")
    suspend fun getHabitConfig(): HabitConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitConfig(config: HabitConfigEntity)

    @Update
    suspend fun updateHabitConfig(config: HabitConfigEntity)

    @Query("UPDATE habit_config SET current_phase = :phase, observation_start_date_millis = :observationStartDate, action_phase_start_date_millis = :actionStartDate, clean_streak_start_millis = :cleanStreakStart WHERE id = 1")
    suspend fun updatePhase(
        phase: HabitPhase,
        observationStartDate: Long,
        actionStartDate: Long?,
        cleanStreakStart: Long?
    )

    @Query("UPDATE habit_config SET default_timer_minutes = :minutes WHERE id = 1")
    suspend fun updateDefaultTimerMinutes(minutes: Int)

    @Query("UPDATE habit_config SET pause_overlay_position = :position WHERE id = 1")
    suspend fun updatePauseOverlayPosition(position: PauseOverlayPosition)

    @Query("UPDATE habit_config SET target_observation_days = :days WHERE id = 1")
    suspend fun updateTargetObservationDays(days: Int)

    @Query("UPDATE habit_config SET target_mastery_days = :days WHERE id = 1")
    suspend fun updateTargetMasteryDays(days: Int)

    @Query("UPDATE habit_config SET habit_name = :name WHERE id = 1")
    suspend fun updateHabitName(name: String)

    @Query("UPDATE habit_config SET replacement_plan = :plan WHERE id = 1")
    suspend fun updateReplacementPlan(plan: String)

    @Query("UPDATE habit_config SET approved_relief_techniques = :techniques WHERE id = 1")
    suspend fun updateApprovedReliefTechniques(techniques: List<String>)

    @Query("UPDATE habit_config SET replacement_plan = :plan, approved_relief_techniques = :techniques, default_timer_minutes = :minutes WHERE id = 1")
    suspend fun updateGuidedPlan(plan: String, techniques: List<String>, minutes: Int)

    @Query("UPDATE habit_config SET clean_streak_start_millis = :cleanStreakStart WHERE id = 1")
    suspend fun resetCleanStreak(cleanStreakStart: Long)

    @Query("DELETE FROM habit_config")
    suspend fun clearConfig()
}
