package org.exhabere.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.exhabere.data.db.AppDatabase
import org.exhabere.data.db.InitialHabitDefaults
import org.exhabere.data.db.dao.ContextTagDao
import org.exhabere.data.db.dao.HabitConfigDao
import org.exhabere.data.db.dao.TriggerLogDao
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition

class HabitRepositoryImpl(
    private val habitConfigDao: HabitConfigDao,
    private val triggerLogDao: TriggerLogDao,
    private val contextTagDao: ContextTagDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val database: AppDatabase? = null
) : HabitRepository {

    override fun getHabitConfig(): Flow<HabitConfigEntity?> {
        return habitConfigDao.getHabitConfigFlow()
    }

    override suspend fun getCurrentConfig(): HabitConfigEntity? = withContext(ioDispatcher) {
        habitConfigDao.getHabitConfig()
    }

    override suspend fun saveHabitConfig(config: HabitConfigEntity): Unit = withContext(ioDispatcher) {
        habitConfigDao.upsertHabitConfig(config)
    }

    override suspend fun transitionToPhase(phase: HabitPhase): Unit = withContext(ioDispatcher) {
        transitionToPhaseInternal(phase)
    }

    private suspend fun transitionToPhaseInternal(phase: HabitPhase) {
        val now = System.currentTimeMillis()
        val current = habitConfigDao.getHabitConfig()
        val enteringAction = phase == HabitPhase.ACTION && current?.phase != HabitPhase.ACTION
        val enteringObservation = phase == HabitPhase.OBSERVATION && current?.phase != HabitPhase.OBSERVATION

        val observationStart = if (enteringObservation) {
            now
        } else {
            current?.observationStartDateMillis ?: now
        }

        val actionStart = when (phase) {
            HabitPhase.ACTION -> if (enteringAction) now else current?.actionPhaseStartDateMillis ?: now
            HabitPhase.OBSERVATION -> current?.actionPhaseStartDateMillis
            HabitPhase.MASTERY -> current?.actionPhaseStartDateMillis
        }

        val cleanStart = when (phase) {
            // Returning to Action after a temporary Observation period must not erase the
            // existing clean-time baseline; only the first Action transition creates it.
            HabitPhase.ACTION -> current?.cleanStreakStartMillis ?: now
            HabitPhase.OBSERVATION -> current?.cleanStreakStartMillis
            HabitPhase.MASTERY -> current?.cleanStreakStartMillis
        }

        habitConfigDao.updatePhase(
            phase = phase,
            observationStartDate = observationStart,
            actionStartDate = actionStart,
            cleanStreakStart = cleanStart
        )
    }

    override suspend fun updateTimerMinutes(minutes: Int): Unit = withContext(ioDispatcher) {
        require(minutes in setOf(2, 5, 10)) { "Timer duration must be 2, 5, or 10 minutes." }
        habitConfigDao.updateDefaultTimerMinutes(minutes)
    }

    override suspend fun updatePauseOverlayPosition(position: PauseOverlayPosition): Unit = withContext(ioDispatcher) {
        habitConfigDao.updatePauseOverlayPosition(position)
    }

    override suspend fun updateObservationTargetDays(days: Int): Unit = withContext(ioDispatcher) {
        require(days in 1..30) { "Observation target must be between 1 and 30 days." }
        habitConfigDao.updateTargetObservationDays(days)
    }

    override suspend fun updateMasteryTargetDays(days: Int): Unit = withContext(ioDispatcher) {
        require(days in 21..365) { "Mastery target must be between 21 and 365 days." }
        habitConfigDao.updateTargetMasteryDays(days)
    }

    override suspend fun updateHabitName(name: String): Unit = withContext(ioDispatcher) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Habit name cannot be blank." }
        habitConfigDao.updateHabitName(trimmed)
    }

    override suspend fun updateReplacementPlan(plan: String): Unit = withContext(ioDispatcher) {
        habitConfigDao.updateReplacementPlan(plan)
    }

    override suspend fun updateApprovedReliefTechniques(techniques: List<String>): Unit = withContext(ioDispatcher) {
        val selected = techniques.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(selected.isNotEmpty()) { "Select at least one relief technique." }
        habitConfigDao.updateApprovedReliefTechniques(selected)
    }

    override suspend fun commitGuidedPlan(
        plan: String,
        techniques: List<String>,
        timerMinutes: Int
    ): Unit = withContext(ioDispatcher) {
        val replacementPlan = plan.trim()
        require(replacementPlan.isNotEmpty()) { "Replacement plan cannot be blank." }
        require(replacementPlan.length <= 300) { "Replacement plan must be 300 characters or fewer." }
        val selected = techniques.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(selected.isNotEmpty()) { "Select at least one relief technique." }
        require(timerMinutes in setOf(2, 5, 10)) { "Timer duration must be 2, 5, or 10 minutes." }

        if (database == null) {
            habitConfigDao.updateGuidedPlan(replacementPlan, selected, timerMinutes)
            transitionToPhaseInternal(HabitPhase.ACTION)
        } else {
            database.withTransaction {
                habitConfigDao.updateGuidedPlan(replacementPlan, selected, timerMinutes)
                transitionToPhaseInternal(HabitPhase.ACTION)
            }
        }
    }

    override suspend fun resetCleanStreak(): Unit = withContext(ioDispatcher) {
        habitConfigDao.resetCleanStreak(System.currentTimeMillis())
    }

    override suspend fun resetAllHabitData(): Unit = withContext(ioDispatcher) {
        if (database == null) clearAndSeedDefaults()
        else database.withTransaction { clearAndSeedDefaults() }
    }

    private suspend fun clearAndSeedDefaults() {
        triggerLogDao.deleteAllLogs()
        contextTagDao.clearCustomTags()
        contextTagDao.resetDefaultTagUsage()
        habitConfigDao.clearConfig()
        // Reinitialize baseline config
        habitConfigDao.upsertHabitConfig(InitialHabitDefaults.habitConfig())
    }
}
