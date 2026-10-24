package org.exhabere.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.exhabere.R
import androidx.compose.ui.unit.dp
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.UrgeIntensity
import java.util.Locale

/** Shared context logger used in both the Observation and Action dashboards. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickLogSheet(
    emotions: List<String>,
    locations: List<String>,
    onDismiss: () -> Unit,
    editingEmergencyOutcome: Boolean = false,
    onSave: (String, String, UrgeIntensity, TriggerOutcomeType, Boolean, Boolean) -> Unit
) {
    var emotion by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var customEmotion by remember { mutableStateOf("") }
    var customLocation by remember { mutableStateOf("") }
    var intensity by remember { mutableStateOf(UrgeIntensity.MODERATE) }
    var outcomeType by remember { mutableStateOf(TriggerOutcomeType.URGE_ONLY) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (editingEmergencyOutcome) R.string.add_emergency_urge_details else R.string.log_urge_context),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(stringResource(R.string.feeling), style = MaterialTheme.typography.titleMedium)
            TagChips(tags = emotions, selected = emotion, onSelected = { emotion = it; customEmotion = "" })
            OutlinedTextField(
                value = customEmotion,
                onValueChange = { customEmotion = it; if (it.isNotBlank()) emotion = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.other_feeling_optional)) },
                singleLine = true
            )

            Text(stringResource(R.string.location_or_context), style = MaterialTheme.typography.titleMedium)
            TagChips(tags = locations, selected = location, onSelected = { location = it; customLocation = "" })
            OutlinedTextField(
                value = customLocation,
                onValueChange = { customLocation = it; if (it.isNotBlank()) location = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.other_context_optional)) },
                singleLine = true
            )

            Text(stringResource(R.string.intensity), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrgeIntensity.values().forEach { value ->
                    FilterChip(
                        selected = intensity == value,
                        onClick = { intensity = value },
                        label = {
                            Text(stringResource(when (value) {
                                UrgeIntensity.MILD -> R.string.mild
                                UrgeIntensity.MODERATE -> R.string.moderate
                                UrgeIntensity.STRONG -> R.string.strong
                            }))
                        }
                    )
                }
            }
            if (!editingEmergencyOutcome) {
                Text(stringResource(R.string.what_happened), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = outcomeType == TriggerOutcomeType.URGE_ONLY,
                        onClick = { outcomeType = TriggerOutcomeType.URGE_ONLY },
                        label = { Text(stringResource(R.string.urge_only)) }
                    )
                    FilterChip(
                        selected = outcomeType == TriggerOutcomeType.ENGAGED_IN_HABIT,
                        onClick = { outcomeType = TriggerOutcomeType.ENGAGED_IN_HABIT },
                        label = { Text(stringResource(R.string.engaged_in_habit)) }
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = {
                        onSave(
                            customEmotion.trim().ifEmpty { emotion },
                            customLocation.trim().ifEmpty { location },
                            intensity,
                            outcomeType,
                            customEmotion.isNotBlank(),
                            customLocation.isNotBlank()
                        )
                    }
                ) { Text(stringResource(R.string.save_log)) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TagChips(tags: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            FilterChip(selected = tag == selected, onClick = { onSelected(tag) }, label = { Text(localizedTagName(tag)) })
        }
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
