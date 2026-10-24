package org.exhabere.data.repository

import kotlinx.coroutines.flow.Flow
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

interface TriggerLogRepository {
    fun getAllLogs(): Flow<List<TriggerLogEntity>>
    fun getLogsForOrigin(phaseOrigin: TriggerPhaseOrigin): Flow<List<TriggerLogEntity>>
    fun getLogsSince(sinceMillis: Long): Flow<List<TriggerLogEntity>>
    fun getUrgeCountSince(sinceMillis: Long): Flow<Int>
    fun getTagsByCategory(category: TagCategory): Flow<List<ContextTagEntity>>
    fun getAllTags(): Flow<List<ContextTagEntity>>

    /**
     * Records a new urge log and ensures that dynamic/custom tags are persisted into the local database
     * and incremented for quick-select chips.
     */
    suspend fun recordLog(
        log: TriggerLogEntity,
        isCustomEmotion: Boolean = false,
        isCustomLocation: Boolean = false
    ): Long

    suspend fun recordEmergencyOutcome(
        reliefMethod: String?,
        outcomeType: TriggerOutcomeType,
        notes: String? = null
    ): Long

    suspend fun addEmergencyLogDetails(
        logId: Long,
        emotion: String,
        location: String,
        intensity: UrgeIntensity,
        isCustomEmotion: Boolean = false,
        isCustomLocation: Boolean = false
    )

    suspend fun addCustomTag(name: String, category: TagCategory): Long
    suspend fun deleteCustomTag(tagId: Long): Boolean
    suspend fun getTotalUrgesResisted(): Int
}
