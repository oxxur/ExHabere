package org.exhabere.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.exhabere.R
import org.exhabere.supportedAppLocale
import java.text.DateFormatSymbols
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerHeatmapSheet(
    title: String,
    description: String,
    emptyMessage: String,
    heatmap: List<List<Int>>,
    eventCount: Int,
    comparisonHeatmap: List<List<Int>>? = null,
    comparisonEventCount: Int? = null,
    comparisonTitle: String? = null,
    onDismiss: () -> Unit
) {
    val maxCount = (heatmap.flatten() + comparisonHeatmap.orEmpty().flatten()).maxOrNull()?.coerceAtLeast(1) ?: 1
    val hasComparison = comparisonHeatmap != null && comparisonEventCount != null
    val resolvedComparisonTitle = comparisonTitle ?: stringResource(R.string.observation_baseline_title)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasComparison) {
                Text(stringResource(R.string.events_count, resolvedComparisonTitle, comparisonEventCount!!), style = MaterialTheme.typography.titleMedium)
                if (comparisonEventCount == 0) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
                } else {
                    TriggerHeatmapGrid(comparisonHeatmap!!, maxCount)
                }

                Text(stringResource(R.string.events_count, stringResource(R.string.action_phase), eventCount), style = MaterialTheme.typography.titleMedium)
                if (eventCount == 0) {
                    Text(stringResource(R.string.no_action_urge_events), style = MaterialTheme.typography.bodyMedium)
                } else {
                    TriggerHeatmapGrid(heatmap, maxCount)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeatmapCell(0, maxCount, stringResource(R.string.no_urge_events), Modifier.size(24.dp))
                    Text(stringResource(R.string.fewer), style = MaterialTheme.typography.labelMedium)
                    HeatmapCell(maxCount, maxCount, stringResource(R.string.more_urge_events), Modifier.size(24.dp))
                    Text(stringResource(R.string.more), style = MaterialTheme.typography.labelMedium)
                }
            } else if (eventCount == 0) {
                Text(emptyMessage, style = MaterialTheme.typography.bodyLarge)
            } else {
                TriggerHeatmapGrid(heatmap, maxCount)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeatmapCell(
                        count = 0,
                        maxCount = maxCount,
                        description = stringResource(R.string.no_urge_events),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(R.string.fewer), style = MaterialTheme.typography.labelMedium)
                    HeatmapCell(
                        count = maxCount,
                        maxCount = maxCount,
                        description = stringResource(R.string.more_urge_events),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(R.string.more), style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.close))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun TriggerHeatmapGrid(
    heatmap: List<List<Int>>,
    maxCount: Int,
    highlightCell: Pair<Int, Int>? = null,
    accentColor: Color? = null
) {
    val dayLabels = DateFormatSymbols.getInstance(supportedAppLocale()).shortWeekdays
    val weekdays = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
    val periodLabels = listOf(
        R.string.heatmap_period_0,
        R.string.heatmap_period_1,
        R.string.heatmap_period_2,
        R.string.heatmap_period_3,
        R.string.heatmap_period_4,
        R.string.heatmap_period_5
    ).map { stringResource(it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(38.dp))
        periodLabels.forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    weekdays.forEachIndexed { dayIndex, weekday ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dayLabels[weekday],
                modifier = Modifier.width(38.dp).height(30.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            heatmap[dayIndex].forEachIndexed { periodIndex, count ->
                HeatmapCell(
                    count = count,
                    maxCount = maxCount,
                    description = stringResource(
                        R.string.heatmap_cell_description,
                        dayLabels[weekday],
                        periodLabels[periodIndex],
                        count,
                        stringResource(if (count == 1) R.string.urge_event else R.string.urge_events)
                    ),
                    modifier = Modifier.weight(1f),
                    isHighlighted = highlightCell == (dayIndex to periodIndex),
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    count: Int,
    maxCount: Int,
    description: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    accentColor: Color? = null
) {
    val fraction = if (count == 0) 0f else count.toFloat() / maxCount
    val color = lerp(
        if (accentColor == null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        accentColor ?: MaterialTheme.colorScheme.primary,
        fraction
    )
    Box(
        modifier = modifier
            .height(30.dp)
            .background(color, RoundedCornerShape(4.dp))
            .then(
                if (isHighlighted) Modifier.border(2.dp, accentColor ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                else Modifier
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            count.takeIf { it > 0 }?.toString() ?: "–",
            style = MaterialTheme.typography.labelSmall,
            color = if (fraction > 0.55f) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
