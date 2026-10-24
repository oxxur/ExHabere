package org.exhabere.ui.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.exhabere.R
import org.exhabere.ui.localizedDefaultPlan

@Composable
fun MasteryScreen(
    targetDays: Int,
    stats: MasteryStats,
    replacementPlan: String?,
    isArchiving: Boolean = false,
    onArchiveAndStartNewHabit: () -> Unit,
    onContinueTracking: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.mastery_achieved),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.days_clean, targetDays),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.mastery_baseline_reached, targetDays),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.mastery_congratulations),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.your_mastery_stats), style = MaterialTheme.typography.titleLarge)
                    MasteryStat(
                        label = stringResource(R.string.total_urges_resisted),
                        value = stats.urgesResisted.toString()
                    )
                    MasteryStat(
                        label = stringResource(R.string.top_relief_method),
                        value = stats.topReliefMethod?.let { method ->
                            val percent = stats.topReliefMethodPercent ?: 0
                            stringResource(R.string.top_relief_method_summary, masteryReliefMethodLabel(method), percent, stats.reliefMethodSessionCount)
                        } ?: stringResource(R.string.no_selected_relief_summary)
                    )
                    MasteryStat(
                        label = stringResource(R.string.alternative_plan_selected),
                        value = if (stats.alternativePlanSelectionCount == 1) stringResource(R.string.recorded_pauses_one)
                        else stringResource(R.string.recorded_pauses_other, stats.alternativePlanSelectionCount)
                    )
                    replacementPlan?.takeIf(String::isNotBlank).localizedDefaultPlan()?.let { plan ->
                        Text("“$plan”", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.app_records_response),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(stringResource(R.string.next_step_question), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onArchiveAndStartNewHabit,
                enabled = !isArchiving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isArchiving) R.string.archiving else R.string.archive_start_new))
            }
            Text(
                stringResource(R.string.history_saved_settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onContinueTracking, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.maintain_mastery_tracker))
            }
        }
    }
}

@Composable
private fun masteryReliefMethodLabel(method: String): String = stringResource(when (method) {
    "Box Breathing Exercise" -> R.string.relief_method_box_breathing
    "Physiological Sigh" -> R.string.relief_method_physiological_sigh
    "Cold Water Reset" -> R.string.relief_method_cold_water
    else -> R.string.relief_method_grounding
})

@Composable
private fun MasteryStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
