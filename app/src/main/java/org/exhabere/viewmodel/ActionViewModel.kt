package org.exhabere.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.exhabere.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.data.repository.HabitRepository
import org.exhabere.data.repository.TriggerLogRepository

enum class EmergencyOption { RELIEF, ALTERNATIVE }

data class EmergencyLaunchRequest(
    val method: String,
    val durationMinutes: Int,
    val endAtMillis: Long
)

data class ActionUiState(
    val config: HabitConfigEntity? = null,
    val logs: List<TriggerLogEntity> = emptyList(),
    val emotions: List<ContextTagEntity> = emptyList(),
    val locations: List<ContextTagEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isEmergencyVisible: Boolean = false,
    val selectedOption: EmergencyOption = EmergencyOption.RELIEF,
    val selectedDurationMinutes: Int = 5,
    val selectedReliefMethod: String? = null
) {
    private val actionUrgeLogs: List<TriggerLogEntity>
        get() = logs.filter {
            it.phaseOrigin == TriggerPhaseOrigin.ACTION && it.outcomeType in ACTION_URGE_OUTCOMES
        }

    private val observationBaselineLogs: List<TriggerLogEntity>
        get() = logs.filter {
            it.phaseOrigin == TriggerPhaseOrigin.OBSERVATION &&
                it.outcomeType in OBSERVATION_URGE_OUTCOMES
        }

    val cleanDurationMillis: Long
        get() = config?.cleanStreakStartMillis?.let {
            (System.currentTimeMillis() - it).coerceAtLeast(0L)
        } ?: 0L

    val cleanDays: Int
        get() = (cleanDurationMillis / MILLIS_PER_DAY).toInt()

    val resistancePercent: Int
        get() {
            val emergencyOutcomes = logs.filter {
                it.phaseOrigin == TriggerPhaseOrigin.ACTION &&
                    (it.outcomeType == TriggerOutcomeType.EMERGENCY_PASSED ||
                        it.outcomeType == TriggerOutcomeType.EMERGENCY_GAVE_IN)
            }
            if (emergencyOutcomes.isEmpty()) return 0
            val passed = emergencyOutcomes.count { it.outcomeType == TriggerOutcomeType.EMERGENCY_PASSED }
            return (passed * 100 / emergencyOutcomes.size).coerceIn(0, 100)
        }

    val actionUrgeCount: Int
        get() = actionUrgeLogs.size

    val actionPeakHour: Int?
        get() = actionUrgeLogs.peakLocalHour()

    val actionWeekdayTimeHeatmap: List<List<Int>>
        get() = actionUrgeLogs.toWeekdayTimeHeatmap()

    val observationBaselineCount: Int
        get() = observationBaselineLogs.size

    val observationBaselinePeakHour: Int?
        get() = observationBaselineLogs.peakLocalHour()

    val observationBaselinePrimaryEmotion: String?
        get() = observationBaselineLogs.mostCommonTag { it.emotionalState }

    val observationBaselinePrimaryEmotionPercent: Int
        get() = observationBaselineLogs.shareOfTag { it.emotionalState }

    val observationBaselinePrimaryLocation: String?
        get() = observationBaselineLogs.mostCommonTag { it.location }

    val observationBaselinePrimaryLocationPercent: Int
        get() = observationBaselineLogs.shareOfTag { it.location }

    val observationBaselineWeekdayTimeHeatmap: List<List<Int>>
        get() = observationBaselineLogs.toWeekdayTimeHeatmap()

    val milestones: List<MilestoneState>
        get() = listOf(7, 21, config?.targetMasteryDays ?: 66).distinct().map { day ->
            MilestoneState(day, cleanDays >= day)
        }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
        val ACTION_URGE_OUTCOMES = setOf(
            TriggerOutcomeType.URGE_ONLY,
            TriggerOutcomeType.ENGAGED_IN_HABIT,
            TriggerOutcomeType.EMERGENCY_PASSED,
            TriggerOutcomeType.EMERGENCY_GAVE_IN
        )
        val OBSERVATION_URGE_OUTCOMES = setOf(
            TriggerOutcomeType.URGE_ONLY,
            TriggerOutcomeType.ENGAGED_IN_HABIT
        )
    }
}

private fun List<TriggerLogEntity>.mostCommonTag(tag: (TriggerLogEntity) -> String): String? =
    map(tag).asSequence().map(String::trim).filter(String::isNotEmpty)
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

private fun List<TriggerLogEntity>.shareOfTag(tag: (TriggerLogEntity) -> String): Int {
    if (isEmpty()) return 0
    val primary = mostCommonTag(tag) ?: return 0
    return (count { tag(it).trim() == primary } * 100 / size).coerceIn(0, 100)
}

data class MilestoneState(val day: Int, val completed: Boolean)

/** Action-phase state, emergency commitment choices, and timer launch events. */
class ActionViewModel(
    private val habitRepository: HabitRepository,
    private val triggerLogRepository: TriggerLogRepository
) : ViewModel() {

    private val emergencyState = MutableStateFlow(EmergencyUiState())

    val uiState: StateFlow<ActionUiState> = combine(
        habitRepository.getHabitConfig(),
        triggerLogRepository.getAllLogs(),
        triggerLogRepository.getTagsByCategory(TagCategory.EMOTION),
        triggerLogRepository.getTagsByCategory(TagCategory.LOCATION),
        emergencyState
    ) { config, logs, emotions, locations, emergency ->
        ActionUiState(
            config = config,
            logs = logs,
            emotions = emotions,
            locations = locations,
            isLoading = false,
            isEmergencyVisible = emergency.visible,
            selectedOption = emergency.option,
            selectedDurationMinutes = emergency.durationMinutes,
            selectedReliefMethod = emergency.reliefMethod
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionUiState())

    private val launchRequestsChannel = Channel<EmergencyLaunchRequest>(capacity = Channel.BUFFERED)
    val launchRequests = launchRequestsChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun beginEmergency() {
        val config = uiState.value.config ?: return
        emergencyState.value = EmergencyUiState(
            visible = true,
            option = EmergencyOption.RELIEF,
            durationMinutes = config.defaultTimerMinutes.takeIf { it in ALLOWED_TIMER_MINUTES } ?: 5,
            reliefMethod = config.approvedReliefTechniques.shuffled().firstOrNull()
                ?: DEFAULT_RELIEF_METHOD
        )
    }

    fun recordLog(
        emotion: String,
        location: String,
        intensity: UrgeIntensity,
        outcomeType: TriggerOutcomeType = TriggerOutcomeType.URGE_ONLY,
        isCustomEmotion: Boolean = false,
        isCustomLocation: Boolean = false
    ) {
        val config = uiState.value.config?.takeIf { it.phase == HabitPhase.ACTION } ?: return
        viewModelScope.launch {
            try {
                triggerLogRepository.recordLog(
                    TriggerLogEntity(
                        timestampMillis = System.currentTimeMillis(),
                        phaseOrigin = TriggerPhaseOrigin.ACTION,
                        emotionalState = emotion.trim(),
                        location = location.trim(),
                        intensity = intensity,
                        outcomeType = outcomeType
                    ),
                    isCustomEmotion = isCustomEmotion,
                    isCustomLocation = isCustomLocation
                )
                _messages.emit(R.string.toast_log_saved)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_log_save_failed)
            }
        }
    }

    fun addEmergencyLogDetails(
        logId: Long,
        emotion: String,
        location: String,
        intensity: UrgeIntensity,
        isCustomEmotion: Boolean = false,
        isCustomLocation: Boolean = false
    ) {
        if (uiState.value.config?.phase != HabitPhase.ACTION) return
        viewModelScope.launch {
            try {
                triggerLogRepository.addEmergencyLogDetails(
                    logId = logId,
                    emotion = emotion,
                    location = location,
                    intensity = intensity,
                    isCustomEmotion = isCustomEmotion,
                    isCustomLocation = isCustomLocation
                )
                _messages.emit(R.string.toast_log_saved)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_log_save_failed)
            }
        }
    }

    fun dismissEmergency() {
        emergencyState.value = EmergencyUiState()
    }

    fun selectOption(option: EmergencyOption) {
        emergencyState.value = emergencyState.value.copy(option = option)
    }

    fun selectDuration(minutes: Int) {
        if (minutes in ALLOWED_TIMER_MINUTES) {
            emergencyState.value = emergencyState.value.copy(durationMinutes = minutes)
        }
    }

    fun commitEmergency() {
        val config = uiState.value.config?.takeIf { it.phase == HabitPhase.ACTION } ?: return
        val selection = emergencyState.value
        val method = when (selection.option) {
            EmergencyOption.RELIEF -> selection.reliefMethod ?: DEFAULT_RELIEF_METHOD
            EmergencyOption.ALTERNATIVE -> config.replacementPlan?.takeIf { it.isNotBlank() }
                ?: DEFAULT_PAUSE_METHOD
        }
        val minutes = selection.durationMinutes.takeIf { it in ALLOWED_TIMER_MINUTES } ?: 5
        emergencyState.value = selection.copy(visible = false)
        viewModelScope.launch {
            launchRequestsChannel.send(
                EmergencyLaunchRequest(
                    method = method,
                    durationMinutes = minutes,
                    endAtMillis = System.currentTimeMillis() + minutes * MILLIS_PER_MINUTE
                )
            )
        }
    }

    fun returnToObservation() {
        viewModelScope.launch {
            try {
                habitRepository.transitionToPhase(HabitPhase.OBSERVATION)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_return_observation_failed)
            }
        }
    }

    fun markMastered() {
        viewModelScope.launch {
            try {
                habitRepository.transitionToPhase(HabitPhase.MASTERY)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_mastery_update_failed)
            }
        }
    }

    private data class EmergencyUiState(
        val visible: Boolean = false,
        val option: EmergencyOption = EmergencyOption.RELIEF,
        val durationMinutes: Int = 5,
        val reliefMethod: String? = null
    )

    override fun onCleared() {
        launchRequestsChannel.close()
        super.onCleared()
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val triggerLogRepository: TriggerLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionViewModel::class.java))
            return ActionViewModel(habitRepository, triggerLogRepository) as T
        }
    }

    private companion object {
        val ALLOWED_TIMER_MINUTES = setOf(2, 5, 10)
        const val MILLIS_PER_MINUTE = 60_000L
        const val DEFAULT_RELIEF_METHOD = "5-4-3-2-1 Sensory Grounding"
        const val DEFAULT_PAUSE_METHOD = "Take a mindful pause"
    }
}
