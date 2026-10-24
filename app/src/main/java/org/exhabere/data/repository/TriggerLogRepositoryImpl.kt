package org.exhabere.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.exhabere.data.db.AppDatabase
import org.exhabere.data.db.dao.ContextTagDao
import org.exhabere.data.db.dao.HabitConfigDao
import org.exhabere.data.db.dao.TriggerLogDao
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

class TriggerLogRepositoryImpl(
    private val triggerLogDao: TriggerLogDao,
    private val contextTagDao: ContextTagDao,
    private val habitConfigDao: HabitConfigDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val database: AppDatabase? = null
) : TriggerLogRepository {

    override fun getAllLogs(): Flow<List<TriggerLogEntity>> = triggerLogDao.getAllLogsFlow()

    override fun getLogsForOrigin(phaseOrigin: TriggerPhaseOrigin): Flow<List<TriggerLogEntity>> =
        triggerLogDao.getLogsForOriginFlow(phaseOrigin)

    override fun getLogsSince(sinceMillis: Long): Flow<List<TriggerLogEntity>> =
        triggerLogDao.getLogsSinceFlow(sinceMillis)

    override fun getUrgeCountSince(sinceMillis: Long): Flow<Int> =
        triggerLogDao.getUrgeCountSinceFlow(sinceMillis)

    override fun getTagsByCategory(category: TagCategory): Flow<List<ContextTagEntity>> =
        contextTagDao.getTagsByCategoryFlow(category)

    override fun getAllTags(): Flow<List<ContextTagEntity>> =
        contextTagDao.getAllTagsFlow()

    override suspend fun recordLog(
        log: TriggerLogEntity,
        isCustomEmotion: Boolean,
        isCustomLocation: Boolean
    ): Long {
        require(log.outcomeType == TriggerOutcomeType.URGE_ONLY ||
            log.outcomeType == TriggerOutcomeType.ENGAGED_IN_HABIT) {
            "Quick logs must use a quick-log outcome type."
        }
        return withContext(ioDispatcher) {
            if (database == null) persistLog(log, isCustomEmotion, isCustomLocation)
            else database.withTransaction { persistLog(log, isCustomEmotion, isCustomLocation) }
        }
    }

    private suspend fun persistLog(
        log: TriggerLogEntity,
        isCustomEmotion: Boolean,
        isCustomLocation: Boolean
    ): Long {
        val now = System.currentTimeMillis()

        // Handle emotion tag persistence and usage increment
        val trimmedEmotion = log.emotionalState.trim()
        if (trimmedEmotion.isNotEmpty()) {
            val existing = contextTagDao.findTagByName(TagCategory.EMOTION, trimmedEmotion)
            if (existing == null) {
                contextTagDao.insertTag(
                    ContextTagEntity(
                        name = trimmedEmotion,
                        category = TagCategory.EMOTION,
                        isCustom = isCustomEmotion,
                        usageCount = 1,
                        lastUsedMillis = now
                    )
                )
            } else {
                contextTagDao.incrementUsage(TagCategory.EMOTION, trimmedEmotion, now)
            }
        }

        // Handle location tag persistence and usage increment
        val trimmedLocation = log.location.trim()
        if (trimmedLocation.isNotEmpty()) {
            val existing = contextTagDao.findTagByName(TagCategory.LOCATION, trimmedLocation)
            if (existing == null) {
                contextTagDao.insertTag(
                    ContextTagEntity(
                        name = trimmedLocation,
                        category = TagCategory.LOCATION,
                        isCustom = isCustomLocation,
                        usageCount = 1,
                        lastUsedMillis = now
                    )
                )
            } else {
                contextTagDao.incrementUsage(TagCategory.LOCATION, trimmedLocation, now)
            }
        }

        val logId = triggerLogDao.insertLog(log)
        if (log.phaseOrigin == TriggerPhaseOrigin.ACTION &&
            log.outcomeType == TriggerOutcomeType.ENGAGED_IN_HABIT
        ) {
            habitConfigDao.resetCleanStreak(now)
        }
        return logId
    }

    override suspend fun recordEmergencyOutcome(
        reliefMethod: String?,
        outcomeType: TriggerOutcomeType,
        notes: String?
    ): Long = withContext(ioDispatcher) {
        require(outcomeType == TriggerOutcomeType.EMERGENCY_PASSED ||
            outcomeType == TriggerOutcomeType.EMERGENCY_GAVE_IN) {
            "Emergency outcomes must describe an emergency intervention."
        }
        if (database == null) persistEmergencyOutcome(reliefMethod, outcomeType, notes)
        else database.withTransaction { persistEmergencyOutcome(reliefMethod, outcomeType, notes) }
    }

    override suspend fun addEmergencyLogDetails(
        logId: Long,
        emotion: String,
        location: String,
        intensity: UrgeIntensity,
        isCustomEmotion: Boolean,
        isCustomLocation: Boolean
    ) = withContext(ioDispatcher) {
        val saveDetails: suspend () -> Unit = {
            val now = System.currentTimeMillis()
            val updated = triggerLogDao.updateEmergencyLogDetails(
                logId = logId,
                emotion = emotion.trim(),
                location = location.trim(),
                intensity = intensity
            )
            check(updated == 1) { "Emergency log no longer exists." }
            persistTag(TagCategory.EMOTION, emotion, isCustomEmotion, now)
            persistTag(TagCategory.LOCATION, location, isCustomLocation, now)
        }
        if (database == null) saveDetails() else database.withTransaction { saveDetails() }
    }

    private suspend fun persistTag(category: TagCategory, value: String, isCustom: Boolean, now: Long) {
        val name = value.trim()
        if (name.isEmpty()) return
        val existing = contextTagDao.findTagByName(category, name)
        if (existing == null) {
            contextTagDao.insertTag(
                ContextTagEntity(
                    name = name,
                    category = category,
                    isCustom = isCustom,
                    usageCount = 1,
                    lastUsedMillis = now
                )
            )
        } else {
            contextTagDao.incrementUsage(category, name, now)
        }
    }

    private suspend fun persistEmergencyOutcome(
        reliefMethod: String?,
        outcomeType: TriggerOutcomeType,
        notes: String?
    ): Long {
        val now = System.currentTimeMillis()
        val log = TriggerLogEntity(
            timestampMillis = now,
            phaseOrigin = TriggerPhaseOrigin.ACTION,
            emotionalState = "Emergency Craving",
            location = "On Device",
            outcomeType = outcomeType,
            reliefMethodUsed = reliefMethod,
            notes = notes
        )
        val logId = triggerLogDao.insertLog(log)
        if (outcomeType == TriggerOutcomeType.EMERGENCY_GAVE_IN) habitConfigDao.resetCleanStreak(now)
        return logId
    }

    override suspend fun addCustomTag(name: String, category: TagCategory): Long = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext -1L

        if (database == null) persistCustomTag(trimmed, category)
        else database.withTransaction { persistCustomTag(trimmed, category) }
    }

    private suspend fun persistCustomTag(name: String, category: TagCategory): Long {
        val existing = contextTagDao.findTagByName(category, name)
        if (existing != null) {
            contextTagDao.incrementUsage(category, name)
            return existing.id
        }
        return contextTagDao.insertTag(
            ContextTagEntity(
                name = name,
                category = category,
                isCustom = true,
                usageCount = 1,
                lastUsedMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteCustomTag(tagId: Long): Boolean = withContext(ioDispatcher) {
        contextTagDao.deleteCustomTag(tagId) > 0
    }

    override suspend fun getTotalUrgesResisted(): Int = withContext(ioDispatcher) {
        triggerLogDao.getTotalUrgesResisted()
    }
}
