package org.exhabere.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.exhabere.R
import org.exhabere.supportedAppLocale
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.ArchivedHabitSummary
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.repository.ArchivedHabitRecord
import org.exhabere.ui.localizedDefaultPlan
import org.exhabere.ui.action.calculateMasteryStats
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: HabitConfigEntity?,
    customTags: List<ContextTagEntity>,
    archivedHabits: List<ArchivedHabitSummary>,
    selectedArchivedHabit: ArchivedHabitRecord?,
    onBack: () -> Unit,
    onSaveHabitName: (String) -> Unit,
    onSaveReplacementPlan: (String) -> Unit,
    onUpdateReliefTechniques: (List<String>) -> Unit,
    onUpdateTimer: (Int) -> Unit,
    onUpdatePauseOverlayPosition: (PauseOverlayPosition) -> Unit,
    onUpdateObservationDays: (Int) -> Unit,
    onUpdateMasteryDays: (Int) -> Unit,
    onDeleteCustomTag: (Long) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onRevisitGuidedPlanSetup: () -> Unit,
    onReturnToObservation: () -> Unit,
    onReset: () -> Unit,
    onViewArchivedHabit: (Long) -> Unit,
    onCloseArchivedHabit: () -> Unit
) {
    val savedHabitName = if (config?.habitName == "Doomscrolling") {
        stringResource(R.string.default_habit_name)
    } else config?.habitName
    var habitName by rememberSaveable(config?.habitName, savedHabitName) { mutableStateOf(savedHabitName.orEmpty()) }
    val savedPlan = config?.replacementPlan.localizedDefaultPlan()
    var replacementPlan by rememberSaveable(config?.replacementPlan, savedPlan) {
        mutableStateOf(savedPlan.orEmpty())
    }
    var confirmReset by remember { mutableStateOf(false) }
    var moreSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        )
    }) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(stringResource(R.string.habit_management), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = habitName,
                onValueChange = { habitName = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.habit_working_on)) },
                singleLine = true
            )
            Button(
                onClick = { onSaveHabitName(habitName.trim()) },
                enabled = habitName.isNotBlank() && habitName.trim() != savedHabitName,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save_habit_name)) }
            Text(stringResource(R.string.current_phase, config?.phase?.displayName() ?: stringResource(R.string.loading)))

            OutlinedTextField(
                value = replacementPlan,
                onValueChange = { replacementPlan = it.take(300) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.if_then_replacement_plan)) },
                supportingText = { Text(stringResource(R.string.replacement_plan_example)) }
            )
            Button(
                onClick = { onSaveReplacementPlan(replacementPlan) },
                modifier = Modifier.fillMaxWidth(),
                enabled = replacementPlan.trim() != savedPlan.orEmpty()
            ) { Text(stringResource(R.string.save_replacement_plan)) }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.relief_methods), style = MaterialTheme.typography.titleMedium)
                    RELIEF_METHODS.forEach { method ->
                        FilterChip(
                            selected = method in (config?.approvedReliefTechniques ?: emptyList()),
                            onClick = {
                                val current = config?.approvedReliefTechniques.orEmpty()
                                val updated = if (method in current) current - method else current + method
                                if (updated.isNotEmpty()) onUpdateReliefTechniques(updated)
                            },
                            label = { Text(reliefMethodLabel(method)) }
                        )
                    }
                }
            }

            if (config?.phase != HabitPhase.OBSERVATION) {
                OutlinedButton(
                    onClick = onRevisitGuidedPlanSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.revisit_plan_setup))
                }
                OutlinedButton(onClick = onReturnToObservation, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.return_to_observation))
                }
                Text(
                    stringResource(R.string.preserve_history),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClick = { moreSettingsExpanded = !moreSettingsExpanded }
                            )
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.more_settings), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (moreSettingsExpanded) "−" else "+",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    AnimatedVisibility(visible = moreSettingsExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.timer_duration), style = MaterialTheme.typography.titleMedium)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(2, 5, 10).forEach { minutes ->
                                            FilterChip(
                                                selected = config?.defaultTimerMinutes == minutes,
                                                onClick = { onUpdateTimer(minutes) },
                                                label = { Text(stringResource(R.string.minutes_short, minutes)) }
                                            )
                                        }
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.pause_overlay_position), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.pause_overlay_position_description),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PauseOverlayPosition.entries.forEach { position ->
                                            FilterChip(
                                                selected = config?.pauseOverlayPosition == position,
                                                onClick = { onUpdatePauseOverlayPosition(position) },
                                                label = { Text(stringResource(position.labelResource())) }
                                            )
                                        }
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.observation_target), style = MaterialTheme.typography.titleMedium)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(3, 7, 14).forEach { days ->
                                            FilterChip(
                                                selected = config?.targetObservationDays == days,
                                                onClick = { onUpdateObservationDays(days) },
                                                label = { Text(stringResource(R.string.days_short, days)) }
                                            )
                                        }
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.mastery_milestone), style = MaterialTheme.typography.titleMedium)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(66, 90, 120).forEach { days ->
                                            FilterChip(
                                                selected = config?.targetMasteryDays == days,
                                                onClick = { onUpdateMasteryDays(days) },
                                                label = { Text(stringResource(R.string.days_short, days)) }
                                            )
                                        }
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(stringResource(R.string.import_export), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.backup_explanation),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(onClick = onExportData, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.export_habit_data))
                                    }
                                    OutlinedButton(onClick = onImportData, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.restore_from_file))
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.custom_context_tags), style = MaterialTheme.typography.titleMedium)
                                    if (customTags.isEmpty()) {
                                        Text(stringResource(R.string.custom_tags_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        customTags.forEach { tag ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${tag.name} · ${stringResource(tag.category.displayNameResource())}")
                                                TextButton(onClick = { onDeleteCustomTag(tag.id) }) { Text(stringResource(R.string.delete)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.archived_mastered_habits), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.archived_habits_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (archivedHabits.isEmpty()) {
                        Text(stringResource(R.string.no_archived_habits), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        archivedHabits.forEach { archive ->
                            val isExpanded = selectedArchivedHabit?.id == archive.id
                            OutlinedButton(
                                onClick = {
                                    if (isExpanded) onCloseArchivedHabit() else onViewArchivedHabit(archive.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${archive.habitName} · ${archive.archivedAtMillis.formatArchiveDate()} · " +
                                        stringResource(if (isExpanded) R.string.hide_stats else R.string.view_stats)
                                )
                            }
                            if (isExpanded) {
                                selectedArchivedHabit?.let { record ->
                                    ArchivedHabitStats(record)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.reset_habit_data)) }
            Text(
                stringResource(R.string.reset_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_current_habit_title)) },
            text = { Text(stringResource(R.string.reset_current_habit_message)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text(stringResource(R.string.reset_data)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ArchivedHabitStats(record: ArchivedHabitRecord) {
    val backup = record.backup
    val config = backup.habitConfig
    val stats = calculateMasteryStats(
        logs = backup.triggerLogs,
        approvedReliefMethods = config.approvedReliefTechniques,
        replacementPlan = config.replacementPlan
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.mastery_stats_milestone, config.targetMasteryDays), style = MaterialTheme.typography.titleSmall)
        ArchivedStat(stringResource(R.string.total_urges_resisted), stats.urgesResisted.toString())
        ArchivedStat(
            stringResource(R.string.top_relief_method),
            stats.topReliefMethod?.let { method ->
                stringResource(R.string.top_relief_method_summary, reliefMethodLabel(method), stats.topReliefMethodPercent ?: 0, stats.reliefMethodSessionCount)
            } ?: stringResource(R.string.no_selected_relief_pauses)
        )
        ArchivedStat(stringResource(R.string.alternative_plan_selected), pluralPauses(stats.alternativePlanSelectionCount))
    }
}

@Composable
private fun ArchivedStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Long.formatArchiveDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM, supportedAppLocale()).format(Date(this))

@Composable
private fun HabitPhase.displayName(): String = stringResource(when (this) {
    HabitPhase.OBSERVATION -> R.string.settings_phase_observation
    HabitPhase.ACTION -> R.string.settings_phase_action
    HabitPhase.MASTERY -> R.string.settings_phase_mastery
})

private fun TagCategory.displayNameResource(): Int = when (this) {
    TagCategory.EMOTION -> R.string.observation_short
    TagCategory.LOCATION -> R.string.context_short
}

private fun PauseOverlayPosition.labelResource(): Int = when (this) {
    PauseOverlayPosition.TOP -> R.string.overlay_position_top
    PauseOverlayPosition.MIDDLE -> R.string.overlay_position_middle
    PauseOverlayPosition.BOTTOM -> R.string.overlay_position_bottom
    PauseOverlayPosition.FULL_SCREEN -> R.string.overlay_position_full_screen
}

@Composable
private fun reliefMethodLabel(method: String): String = stringResource(when (method) {
    "Box Breathing Exercise" -> R.string.relief_method_box_breathing
    "Physiological Sigh" -> R.string.relief_method_physiological_sigh
    "Cold Water Reset" -> R.string.relief_method_cold_water
    else -> R.string.relief_method_grounding
})

@Composable
private fun pluralPauses(count: Int): String = if (count == 1) {
    stringResource(R.string.recorded_pauses_one)
} else {
    stringResource(R.string.recorded_pauses_other, count)
}

private val RELIEF_METHODS = listOf(
    "Box Breathing Exercise",
    "Physiological Sigh",
    "Cold Water Reset",
    "5-4-3-2-1 Sensory Grounding"
)
