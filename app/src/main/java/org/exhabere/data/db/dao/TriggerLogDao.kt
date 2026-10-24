package org.exhabere.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

@Dao
interface TriggerLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TriggerLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<TriggerLogEntity>)

    @Query("""
        UPDATE trigger_logs
        SET emotional_state = :emotion, location = :location, intensity = :intensity
        WHERE id = :logId AND phase_origin = 'ACTION'
            AND outcome_type IN ('EMERGENCY_PASSED', 'EMERGENCY_GAVE_IN')
    """)
    suspend fun updateEmergencyLogDetails(
        logId: Long,
        emotion: String,
        location: String,
        intensity: UrgeIntensity
    ): Int

    @Query("SELECT * FROM trigger_logs ORDER BY timestamp_millis DESC")
    fun getAllLogsFlow(): Flow<List<TriggerLogEntity>>

    @Query("SELECT * FROM trigger_logs ORDER BY timestamp_millis DESC")
    suspend fun getAllLogs(): List<TriggerLogEntity>

    @Query("SELECT * FROM trigger_logs WHERE phase_origin = :phaseOrigin ORDER BY timestamp_millis DESC")
    fun getLogsForOriginFlow(phaseOrigin: TriggerPhaseOrigin): Flow<List<TriggerLogEntity>>

    @Query("SELECT * FROM trigger_logs WHERE timestamp_millis >= :sinceMillis ORDER BY timestamp_millis DESC")
    fun getLogsSinceFlow(sinceMillis: Long): Flow<List<TriggerLogEntity>>

    @Query("SELECT * FROM trigger_logs WHERE timestamp_millis BETWEEN :fromMillis AND :toMillis ORDER BY timestamp_millis DESC")
    fun getLogsBetween(fromMillis: Long, toMillis: Long): List<TriggerLogEntity>

    @Query("SELECT COUNT(*) FROM trigger_logs WHERE timestamp_millis >= :sinceMillis")
    fun getUrgeCountSinceFlow(sinceMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM trigger_logs WHERE outcome_type = :outcomeType")
    fun getCountByOutcomeFlow(outcomeType: TriggerOutcomeType): Flow<Int>

    @Query("SELECT COUNT(*) FROM trigger_logs WHERE phase_origin = 'ACTION' AND outcome_type = 'EMERGENCY_PASSED'")
    suspend fun getTotalUrgesResisted(): Int

    @Query("SELECT * FROM trigger_logs ORDER BY timestamp_millis DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<TriggerLogEntity>

    @Delete
    suspend fun deleteLog(log: TriggerLogEntity)

    @Query("DELETE FROM trigger_logs")
    suspend fun deleteAllLogs()
}
