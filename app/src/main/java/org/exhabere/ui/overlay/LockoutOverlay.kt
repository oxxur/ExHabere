package org.exhabere.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.exhabere.R
import org.exhabere.data.db.model.TriggerOutcomeType

@Composable
fun LockoutOverlay(
    method: String,
    endAtMillis: Long,
    onOutcome: (TriggerOutcomeType) -> Unit,
    onEndEarly: () -> Unit,
    onTimerComplete: () -> Unit,
    fullScreen: Boolean = false
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAtMillis) {
        while (now < endAtMillis) {
            now = System.currentTimeMillis()
            if (now < endAtMillis) delay(1_000)
        }
        now = System.currentTimeMillis()
        onTimerComplete()
    }
    val remainingMillis = (endAtMillis - now).coerceAtLeast(0L)
    val totalSeconds = (remainingMillis + 999L) / 1_000L
    val countdown = "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)

    Card(modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (remainingMillis > 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$countdown", style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.pause_in_progress), style = MaterialTheme.typography.titleMedium)
                }
                Text(method, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.stay_with_choice), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = onEndEarly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.end_pause_early))
                }
            } else {
                Text(stringResource(R.string.timer_complete), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.did_urge_pass), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOutcome(TriggerOutcomeType.EMERGENCY_PASSED) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.urge_passed), maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { onOutcome(TriggerOutcomeType.EMERGENCY_GAVE_IN) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.gave_in), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
