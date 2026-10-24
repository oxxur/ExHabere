package org.exhabere.data.repository

import kotlinx.coroutines.flow.Flow
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition

interface HabitRepository {
    fun getHabitConfig(): Flow<HabitConfigEntity?>
    suspend fun getCurrentConfig(): HabitConfigEntity?
    suspend fun saveHabitConfig(config: HabitConfigEntity)
    suspend fun transitionToPhase(phase: HabitPhase)
    suspend fun updateTimerMinutes(minutes: Int)
    suspend fun updatePauseOverlayPosition(position: PauseOverlayPosition)
    suspend fun updateObservationTargetDays(days: Int)
    suspend fun updateMasteryTargetDays(days: Int)
    suspend fun updateHabitName(name: String)
    suspend fun updateReplacementPlan(plan: String)
    suspend fun updateApprovedReliefTechniques(techniques: List<String>)
    suspend fun commitGuidedPlan(plan: String, techniques: List<String>, timerMinutes: Int)
    suspend fun resetCleanStreak()
    suspend fun resetAllHabitData()
}
