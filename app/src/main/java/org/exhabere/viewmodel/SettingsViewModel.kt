package org.exhabere.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.exhabere.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.exhabere.data.backup.HabitBackup
import org.exhabere.data.backup.HabitBackupJsonCodec
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.ArchivedHabitSummary
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.repository.HabitRepository
import org.exhabere.data.repository.HabitBackupRepository
import org.exhabere.data.repository.HabitArchiveRepository
import org.exhabere.data.repository.ArchivedHabitRecord
import org.exhabere.data.repository.TriggerLogRepository

class SettingsViewModel(
    private val habitRepository: HabitRepository,
    private val triggerLogRepository: TriggerLogRepository,
    private val habitBackupRepository: HabitBackupRepository,
    private val habitArchiveRepository: HabitArchiveRepository
) : ViewModel() {
    val customTags: StateFlow<List<ContextTagEntity>> = triggerLogRepository.getAllTags()
        .map { tags -> tags.filter { it.isCustom } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedHabits: StateFlow<List<ArchivedHabitSummary>> = habitArchiveRepository.getArchivedHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedArchivedHabit = MutableStateFlow<ArchivedHabitRecord?>(null)
    val selectedArchivedHabit: StateFlow<ArchivedHabitRecord?> = _selectedArchivedHabit

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    suspend fun createHabitBackupJson(): String = withContext(Dispatchers.Default) {
        HabitBackupJsonCodec.encode(habitBackupRepository.createBackup())
    }

    suspend fun parseHabitBackup(json: String): HabitBackup = withContext(Dispatchers.Default) {
        HabitBackupJsonCodec.decode(json)
    }

    suspend fun restoreHabitBackup(backup: HabitBackup) {
        habitBackupRepository.restoreBackup(backup)
    }

    suspend fun archiveMasteredHabit(): Long = habitArchiveRepository.archiveMasteredHabitAndStartFresh()

    fun showArchivedHabit(archiveId: Long) {
        viewModelScope.launch {
            _selectedArchivedHabit.value = null
            try {
                _selectedArchivedHabit.value = habitArchiveRepository.getArchivedHabit(archiveId)
                if (_selectedArchivedHabit.value == null) _messages.emit(R.string.toast_archive_open_failed)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _selectedArchivedHabit.value = null
                _messages.emit(R.string.toast_archive_open_failed)
            }
        }
    }

    fun closeArchivedHabit() {
        _selectedArchivedHabit.value = null
    }

    fun updateTimer(minutes: Int) = update(R.string.toast_timer_save_failed) {
        habitRepository.updateTimerMinutes(minutes)
    }

    fun updatePauseOverlayPosition(position: PauseOverlayPosition) = update(R.string.toast_overlay_position_save_failed) {
        habitRepository.updatePauseOverlayPosition(position)
    }

    fun updateObservationDays(days: Int) = update(R.string.toast_observation_target_save_failed) {
        habitRepository.updateObservationTargetDays(days)
    }

    fun updateMasteryDays(days: Int) = update(R.string.toast_mastery_target_save_failed) {
        habitRepository.updateMasteryTargetDays(days)
    }

    fun updateHabitName(name: String) = update(R.string.toast_habit_name_save_failed) {
        habitRepository.updateHabitName(name)
    }

    fun updateReplacementPlan(plan: String) = update(R.string.toast_replacement_plan_save_failed) {
        habitRepository.updateReplacementPlan(plan.trim())
    }

    fun updateReliefTechniques(techniques: List<String>) = update(R.string.toast_relief_methods_save_failed) {
        habitRepository.updateApprovedReliefTechniques(techniques)
    }

    fun deleteCustomTag(tagId: Long) {
        viewModelScope.launch {
            try {
                triggerLogRepository.deleteCustomTag(tagId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_custom_tag_delete_failed)
            }
        }
    }

    fun returnToObservation() = update(R.string.toast_return_observation_failed) {
        habitRepository.transitionToPhase(HabitPhase.OBSERVATION)
    }

    fun resetHabit() = update(R.string.toast_reset_failed) {
        habitRepository.resetAllHabitData()
    }

    private fun update(errorMessage: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(errorMessage)
            }
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val triggerLogRepository: TriggerLogRepository,
        private val habitBackupRepository: HabitBackupRepository,
        private val habitArchiveRepository: HabitArchiveRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(
                habitRepository,
                triggerLogRepository,
                habitBackupRepository,
                habitArchiveRepository
            ) as T
        }
    }
}
