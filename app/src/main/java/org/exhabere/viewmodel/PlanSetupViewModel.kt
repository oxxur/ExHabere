package org.exhabere.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.exhabere.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.repository.HabitRepository
import org.exhabere.data.repository.TriggerLogRepository

data class PlanSetupUiState(
    val config: HabitConfigEntity? = null,
    val triggerPattern: PrimaryTriggerPattern = emptyList<TriggerLogEntity>().primaryTriggerPattern(),
    val replacementPlan: String = "",
    val selectedReliefTechniques: List<String> = emptyList(),
    val timerMinutes: Int = 5,
    val isSuggestedPlan: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: Int? = null
) {
    val canCommit: Boolean
        get() = config != null && !isLoading && !isSaving && replacementPlan.isNotBlank() &&
            replacementPlan.trim().length <= MAX_REPLACEMENT_PLAN_LENGTH &&
            selectedReliefTechniques.isNotEmpty() && timerMinutes in ALLOWED_TIMER_MINUTES

    val shouldUseSparseFallback: Boolean
        get() = triggerPattern.isSparse || config?.let {
            (System.currentTimeMillis() - it.observationStartDateMillis).coerceAtLeast(0L) < MIN_OBSERVATION_PERIOD_MILLIS
        } ?: true

    companion object {
        const val MAX_REPLACEMENT_PLAN_LENGTH = 300
        val ALLOWED_TIMER_MINUTES = setOf(2, 5, 10)
        private const val MIN_OBSERVATION_PERIOD_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

/** Owns the editable plan draft and commits it without losing Observation history. */
class PlanSetupViewModel(
    private val habitRepository: HabitRepository,
    private val triggerLogRepository: TriggerLogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanSetupUiState())
    val uiState = _uiState.asStateFlow()

    private val _commitEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commitEvents = _commitEvents.asSharedFlow()

    private var draftEdited = false

    init {
        viewModelScope.launch {
            combine(
                habitRepository.getHabitConfig(),
                triggerLogRepository.getLogsForOrigin(TriggerPhaseOrigin.OBSERVATION)
            ) { config, logs -> config to logs }
                .collect { (config, logs) ->
                    val pattern = logs.primaryTriggerPattern()
                    val current = _uiState.value
                    val suggestedPlan = config?.let(::shouldSuggestPlan) == true
                    _uiState.value = current.copy(
                        config = config,
                        triggerPattern = pattern,
                        replacementPlan = if (draftEdited) current.replacementPlan else
                            config?.let(::persistedOrSuggestedPlan).orEmpty(),
                        isSuggestedPlan = if (draftEdited) current.isSuggestedPlan else suggestedPlan,
                        selectedReliefTechniques = if (draftEdited) current.selectedReliefTechniques else
                            config?.approvedReliefTechniques?.takeIf { it.isNotEmpty() }
                                ?: listOf(RELIEF_TECHNIQUES.first()),
                        timerMinutes = if (draftEdited) current.timerMinutes else
                            config?.defaultTimerMinutes?.takeIf { it in PlanSetupUiState.ALLOWED_TIMER_MINUTES } ?: 5,
                        isLoading = config == null
                    )
                }
        }
    }

    fun updateReplacementPlan(plan: String) {
        draftEdited = true
        _uiState.value = _uiState.value.copy(
            replacementPlan = plan.take(PlanSetupUiState.MAX_REPLACEMENT_PLAN_LENGTH),
            isSuggestedPlan = false,
            errorMessage = null
        )
    }

    fun toggleReliefTechnique(technique: String) {
        if (technique !in RELIEF_TECHNIQUES) return
        draftEdited = true
        val selected = _uiState.value.selectedReliefTechniques
        _uiState.value = _uiState.value.copy(
            selectedReliefTechniques = if (technique in selected) selected - technique else selected + technique,
            errorMessage = null
        )
    }

    fun selectTimer(minutes: Int) {
        if (minutes in PlanSetupUiState.ALLOWED_TIMER_MINUTES) {
            draftEdited = true
            _uiState.value = _uiState.value.copy(timerMinutes = minutes)
        }
    }

    /** Refreshes clean saved values when the setup screen is opened again from Settings. */
    fun beginSetup() {
        draftEdited = false
        val current = _uiState.value
        val config = current.config ?: return
        _uiState.value = current.copy(
            replacementPlan = persistedOrSuggestedPlan(config),
            isSuggestedPlan = shouldSuggestPlan(config),
            selectedReliefTechniques = config.approvedReliefTechniques.ifEmpty { listOf(RELIEF_TECHNIQUES.first()) },
            timerMinutes = config.defaultTimerMinutes.takeIf { it in PlanSetupUiState.ALLOWED_TIMER_MINUTES } ?: 5,
            errorMessage = null
        )
    }

    fun commitPlan() {
        val draft = _uiState.value
        if (!draft.canCommit) return
        _uiState.value = draft.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                habitRepository.commitGuidedPlan(
                    plan = draft.replacementPlan.trim(),
                    techniques = draft.selectedReliefTechniques,
                    timerMinutes = draft.timerMinutes
                )
                _uiState.value = _uiState.value.copy(isSaving = false)
                _commitEvents.emit(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = R.string.toast_plan_save_failed
                )
            }
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val triggerLogRepository: TriggerLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PlanSetupViewModel::class.java))
            return PlanSetupViewModel(habitRepository, triggerLogRepository) as T
        }
    }
}

internal val RELIEF_TECHNIQUES = listOf(
    "Box Breathing Exercise",
    "Physiological Sigh",
    "Cold Water Reset",
    "5-4-3-2-1 Sensory Grounding"
)

internal val DEFAULT_STARTER_IDEAS = listOf(
    "Read 2 pages of a physical book",
    "Drink water and stretch",
    "Take 10 mindful breaths",
    "Write down what I’m feeling"
)

private fun persistedOrSuggestedPlan(config: HabitConfigEntity): String {
    val savedPlan = config.replacementPlan?.takeIf { it.isNotBlank() }
    return if (shouldSuggestPlan(config)) "" else savedPlan.orEmpty()
}

private fun shouldSuggestPlan(config: HabitConfigEntity): Boolean =
    config.replacementPlan.isNullOrBlank() ||
        (config.phase == HabitPhase.OBSERVATION &&
            config.actionPhaseStartDateMillis == null &&
            config.replacementPlan == DEFAULT_STORED_REPLACEMENT_PLAN)

private const val DEFAULT_STORED_REPLACEMENT_PLAN =
    "When I feel Bored in Bed, I will Read 2 pages of a physical book."
