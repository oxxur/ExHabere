package org.exhabere.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.exhabere.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.data.repository.HabitRepository
import org.exhabere.data.repository.TriggerLogRepository

data class ObservationUiState(
    val config: HabitConfigEntity? = null,
    val logs: List<TriggerLogEntity> = emptyList(),
    val emotions: List<ContextTagEntity> = emptyList(),
    val locations: List<ContextTagEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    private val urgeLogs: List<TriggerLogEntity>
        get() = logs.filter {
            it.phaseOrigin == TriggerPhaseOrigin.OBSERVATION &&
                (it.outcomeType == TriggerOutcomeType.URGE_ONLY ||
                    it.outcomeType == TriggerOutcomeType.ENGAGED_IN_HABIT)
        }

    val observationDay: Int
        get() = config?.let {
            (((System.currentTimeMillis() - it.observationStartDateMillis).coerceAtLeast(0L)) /
                MILLIS_PER_DAY + 1L).toInt().coerceAtMost(it.targetObservationDays)
        } ?: 1

    fun isObservationPeriodComplete(nowMillis: Long = System.currentTimeMillis()): Boolean =
        config?.let {
            it.phase == HabitPhase.OBSERVATION &&
                (nowMillis - it.observationStartDateMillis).coerceAtLeast(0L) >=
                it.targetObservationDays * MILLIS_PER_DAY
        } ?: false

    val primaryEmotion: String?
        get() = mostCommon(urgeLogs.map { it.emotionalState })

    val primaryLocation: String?
        get() = mostCommon(urgeLogs.map { it.location })

    val loggedUrgeCount: Int
        get() = urgeLogs.size

    val primaryEmotionPercent: Int
        get() = percentageOfUrges(urgeLogs.map { it.emotionalState })

    val primaryLocationPercent: Int
        get() = percentageOfUrges(urgeLogs.map { it.location })

    val peakHour: Int?
        get() = urgeLogs.peakLocalHour()

    /** Seven Monday-first rows by six four-hour local-time periods, for the observation heatmap. */
    val weekdayTimeHeatmap: List<List<Int>>
        get() = urgeLogs.toWeekdayTimeHeatmap()

    private fun percentageOfUrges(values: List<String>): Int {
        if (urgeLogs.isEmpty()) return 0
        val primary = mostCommon(values) ?: return 0
        return (values.count { it.trim() == primary } * 100f / urgeLogs.size).toInt()
    }

    private fun mostCommon(values: List<String>): String? = values
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}

/** State and actions for the low-friction observation logging flow. */
class ObservationViewModel(
    private val habitRepository: HabitRepository,
    private val triggerLogRepository: TriggerLogRepository
) : ViewModel() {

    val uiState: StateFlow<ObservationUiState> = combine(
        habitRepository.getHabitConfig(),
        triggerLogRepository.getLogsForOrigin(TriggerPhaseOrigin.OBSERVATION),
        triggerLogRepository.getTagsByCategory(TagCategory.EMOTION),
        triggerLogRepository.getTagsByCategory(TagCategory.LOCATION)
    ) { config, logs, emotions, locations ->
        ObservationUiState(
            config = config,
            logs = logs,
            emotions = emotions,
            locations = locations,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObservationUiState())

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun advanceToActionIfObservationComplete() {
        if (!uiState.value.isObservationPeriodComplete()) return
        viewModelScope.launch {
            try {
                habitRepository.transitionToPhase(HabitPhase.ACTION)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_action_transition_failed)
            }
        }
    }

    fun recordLog(
        emotion: String,
        location: String,
        intensity: UrgeIntensity,
        outcomeType: TriggerOutcomeType = TriggerOutcomeType.URGE_ONLY,
        isCustomEmotion: Boolean = false,
        isCustomLocation: Boolean = false
    ) {
        val config = uiState.value.config ?: return
        viewModelScope.launch {
            try {
                triggerLogRepository.recordLog(
                    log = TriggerLogEntity(
                        timestampMillis = System.currentTimeMillis(),
                        phaseOrigin = TriggerPhaseOrigin.OBSERVATION,
                        emotionalState = emotion.trim(),
                        location = location.trim(),
                        intensity = intensity,
                        outcomeType = outcomeType
                    ),
                    isCustomEmotion = isCustomEmotion,
                    isCustomLocation = isCustomLocation
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _messages.emit(R.string.toast_observation_save_failed)
            }
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val triggerLogRepository: TriggerLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ObservationViewModel::class.java))
            return ObservationViewModel(habitRepository, triggerLogRepository) as T
        }
    }
}
