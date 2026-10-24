package org.exhabere.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.exhabere.R
import org.exhabere.supportedAppLocale
import org.exhabere.viewmodel.DEFAULT_STARTER_IDEAS
import org.exhabere.viewmodel.PlanSetupUiState
import org.exhabere.viewmodel.RELIEF_TECHNIQUES
import org.exhabere.viewmodel.formatPeakHourWindow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanSetupScreen(
    state: PlanSetupUiState,
    onBack: () -> Unit,
    onPlanChange: (String) -> Unit,
    onChooseStarter: (String) -> Unit,
    onToggleRelief: (String) -> Unit,
    onSelectTimer: (Int) -> Unit,
    onCommit: () -> Unit
) {
    val suggestedPlan = localizedSuggestedPlan(state.triggerPattern, DEFAULT_STARTER_IDEAS.first())
    val displayedPlan = if (state.isSuggestedPlan) suggestedPlan else state.replacementPlan
    LaunchedEffect(state.isSuggestedPlan, suggestedPlan) {
        if (state.isSuggestedPlan) onPlanChange(suggestedPlan)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_setup)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionTitle(stringResource(R.string.section_observed_triggers))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(stringResource(R.string.primary_cue_pattern), style = MaterialTheme.typography.titleMedium)
                    val pattern = state.triggerPattern
                    if (state.shouldUseSparseFallback) {
                        Text(
                            stringResource(R.string.sparse_pattern_guidance),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val locationSuffix = pattern.location?.let {
                            stringResource(R.string.pattern_location_suffix, localizedLocationPhrase(it))
                        }.orEmpty()
                        val peakSuffix = pattern.peakHour?.let {
                            stringResource(R.string.pattern_peak_suffix, formatPeakHourWindow(it))
                        }.orEmpty()
                        val patternSummary = pattern.emotion?.let {
                            stringResource(R.string.urge_pattern, localizedFeelingPhrase(it), locationSuffix, peakSuffix)
                        } ?: stringResource(R.string.urge_pattern_no_feeling, locationSuffix, peakSuffix)
                        Text(patternSummary, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(stringResource(
                        R.string.most_common_feeling,
                        pattern.emotion?.let { localizedTagName(it) } ?: stringResource(R.string.not_enough_data),
                        if (pattern.emotion != null && !state.shouldUseSparseFallback) stringResource(R.string.most_common_percent, pattern.emotionPercent) else ""
                    ))
                    Text(stringResource(
                        R.string.most_common_context,
                        pattern.location?.let { localizedTagName(it) } ?: stringResource(R.string.not_enough_data),
                        if (pattern.location != null && !state.shouldUseSparseFallback) stringResource(R.string.most_common_percent, pattern.locationPercent) else ""
                    ))
                    Text(stringResource(
                        R.string.peak_window,
                        pattern.peakHour?.let(::formatPeakHourWindow) ?: stringResource(R.string.not_enough_data_yet)
                    ))
                    Text(stringResource(R.string.total_observations, pattern.totalObservations))
                }
            }

            SectionTitle(stringResource(R.string.section_replacement_routine))
            Text(
                stringResource(R.string.if_then_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(localizedCuePrompt(state), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = displayedPlan,
                onValueChange = onPlanChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.my_if_then_plan)) },
                supportingText = {
                    Text(stringResource(R.string.character_count, displayedPlan.length, PlanSetupUiState.MAX_REPLACEMENT_PLAN_LENGTH))
                },
                minLines = 2,
                maxLines = 4
            )
            Text(stringResource(R.string.quick_starter_ideas), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DEFAULT_STARTER_IDEAS.forEach { idea ->
                    val label = starterIdeaLabel(idea)
                    val plan = localizedSuggestedPlan(state.triggerPattern, idea)
                    FilterChip(
                        selected = false,
                        onClick = { onChooseStarter(plan) },
                        label = { Text(label) }
                    )
                }
            }

            SectionTitle(stringResource(R.string.section_relief_toolkit))
            Text(
                stringResource(R.string.relief_toolkit_guidance),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    RELIEF_TECHNIQUES.forEach { technique ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleRelief(technique) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = technique in state.selectedReliefTechniques,
                                onCheckedChange = { onToggleRelief(technique) }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(reliefMethodLabel(technique), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(reliefMethodDescription(technique)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.section_pause_duration))
            Text(
                stringResource(R.string.pause_duration_guidance),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 5, 10).forEach { minutes ->
                    FilterChip(
                        selected = state.timerMinutes == minutes,
                        onClick = { onSelectTimer(minutes) },
                        label = { Text(stringResource(R.string.minutes_short, minutes)) }
                    )
                }
            }
            Text(stringResource(R.string.pause_duration_advice),
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            state.errorMessage?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onCommit,
                enabled = state.canCommit && !state.isSuggestedPlan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(stringResource(if (state.isSaving) R.string.saving_plan else R.string.commit_begin_action))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun localizedCuePrompt(state: PlanSetupUiState): String {
    val pattern = state.triggerPattern
    return when {
        pattern.emotion != null && pattern.location != null -> stringResource(
            R.string.cue_both,
            localizedFeelingPhrase(pattern.emotion),
            localizedLocationPhrase(pattern.location)
        )
        pattern.emotion != null -> stringResource(R.string.cue_feeling, localizedFeelingPhrase(pattern.emotion))
        pattern.location != null -> stringResource(R.string.cue_location, localizedLocationPhrase(pattern.location))
        else -> stringResource(R.string.cue_urge)
    }
}

@Composable
private fun localizedTagName(name: String): String {
    val resource = when (name) {
        "Bored" -> R.string.tag_bored
        "Stressed" -> R.string.tag_stressed
        "Tired" -> R.string.tag_tired
        "Anxious" -> R.string.tag_anxious
        "Lonely" -> R.string.tag_lonely
        "Neutral" -> R.string.tag_neutral
        "Bed" -> R.string.tag_bed
        "Desk" -> R.string.tag_desk
        "Couch" -> R.string.tag_couch
        "Transit" -> R.string.tag_transit
        "Social" -> R.string.tag_social
        else -> return name
    }
    return stringResource(resource)
}

@Composable
private fun starterIdeaLabel(idea: String): String = stringResource(when (idea) {
    "Read 2 pages of a physical book" -> R.string.starter_read
    "Drink water and stretch" -> R.string.starter_drink_stretch
    "Take 10 mindful breaths" -> R.string.starter_breaths
    else -> R.string.starter_write
})

@Composable
private fun reliefMethodLabel(method: String): String = stringResource(when (method) {
    "Box Breathing Exercise" -> R.string.relief_method_box_breathing
    "Physiological Sigh" -> R.string.relief_method_physiological_sigh
    "Cold Water Reset" -> R.string.relief_method_cold_water
    else -> R.string.relief_method_grounding
})

private fun reliefMethodDescription(method: String): Int = when (method) {
    "Box Breathing Exercise" -> R.string.relief_description_box_breathing
    "Physiological Sigh" -> R.string.relief_description_physiological_sigh
    "Cold Water Reset" -> R.string.relief_description_cold_water
    else -> R.string.relief_description_grounding
}

@Composable
private fun localizedLocationPhrase(location: String): String = when (location) {
    "Bed" -> stringResource(R.string.location_phrase_bed)
    "Desk" -> stringResource(R.string.location_phrase_desk)
    "Couch" -> stringResource(R.string.location_phrase_couch)
    "Transit" -> stringResource(R.string.location_phrase_transit)
    "Social" -> stringResource(R.string.location_phrase_social)
    else -> stringResource(R.string.location_phrase_custom, location)
}

@Composable
private fun localizedFeelingPhrase(feeling: String): String = localizedTagName(feeling)
    .replaceFirstChar { it.lowercase(supportedAppLocale()) }

@Composable
private fun localizedSuggestedPlan(pattern: org.exhabere.viewmodel.PrimaryTriggerPattern, idea: String): String {
    val action = starterIdeaLabel(idea)
    return when {
        pattern.emotion != null && pattern.location != null -> stringResource(
            R.string.suggested_plan_both,
            localizedFeelingPhrase(pattern.emotion),
            localizedLocationPhrase(pattern.location),
            action
        )
        pattern.emotion != null -> stringResource(R.string.suggested_plan_feeling, localizedFeelingPhrase(pattern.emotion), action)
        pattern.location != null -> stringResource(R.string.suggested_plan_location, localizedLocationPhrase(pattern.location), action)
        else -> stringResource(R.string.suggested_plan_default, action)
    }
}
