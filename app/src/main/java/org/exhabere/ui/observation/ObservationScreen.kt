package org.exhabere.ui.observation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.exhabere.R
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.ui.components.QuickLogSheet
import org.exhabere.viewmodel.ObservationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationScreen(
    state: ObservationUiState,
    onOpenSettings: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenPlanSetup: () -> Unit,
    onSaveLog: (String, String, UrgeIntensity, TriggerOutcomeType, Boolean, Boolean) -> Unit
) {
    var showLogSheet by remember { mutableStateOf(false) }
    val config = state.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        config?.habitName?.takeUnless { it == "Doomscrolling" }
                            ?: stringResource(if (config == null) R.string.app_name else R.string.default_habit_name)
                    )
                },
                actions = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) } }
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
            Text(
                stringResource(R.string.observation_day, state.observationDay, config?.targetObservationDays ?: 7),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showLogSheet = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            ) {
                Text(stringResource(R.string.log_an_urge), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.observation_encouragement),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.view_insights))
            }
            OutlinedButton(onClick = onOpenPlanSetup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_patterns_plan))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLogSheet) {
        QuickLogSheet(
            emotions = state.emotions.map { it.name },
            locations = state.locations.map { it.name },
            onDismiss = { showLogSheet = false },
            onSave = { emotion, location, intensity, outcome, customEmotion, customLocation ->
                onSaveLog(emotion, location, intensity, outcome, customEmotion, customLocation)
                showLogSheet = false
            }
        )
    }

}
