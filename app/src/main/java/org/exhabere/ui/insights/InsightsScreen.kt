package org.exhabere.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import org.exhabere.R
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.ui.components.TriggerHeatmapGrid
import org.exhabere.viewmodel.ActionUiState
import org.exhabere.viewmodel.InsightsData
import org.exhabere.viewmodel.InsightOutcomeFilter
import org.exhabere.viewmodel.LocalizedCopy
import org.exhabere.viewmodel.TriggerTagInsight
import org.exhabere.viewmodel.WeeklyUrgeTrend
import org.exhabere.viewmodel.buildInsightsData
import org.exhabere.viewmodel.formatFourHourPeriod
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(state: ActionUiState, onBack: () -> Unit) {
    var outcomeFilter by remember { mutableStateOf(InsightOutcomeFilter.ALL_URGES) }
    val data = remember(state.logs, state.config, outcomeFilter) {
        buildInsightsData(state.logs, state.config, filter = outcomeFilter)
    }
    var expandedCard by remember { mutableStateOf<InsightCard?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights)) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.insights_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightOutcomeFilter.entries.forEach { filter ->
                    val selectedAccent = filter.accentColor() ?: MaterialTheme.colorScheme.primary
                    FilterChip(
                        selected = outcomeFilter == filter,
                        onClick = { outcomeFilter = filter },
                        label = { Text(stringResource(filter.labelResource())) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = selectedAccent.copy(alpha = 0.18f),
                            selectedLabelColor = selectedAccent
                        )
                    )
                }
            }
            InsightCard(
                title = stringResource(R.string.recovery_trend),
                summary = data.recoverySummary.localized(),
                expanded = expandedCard == InsightCard.TREND,
                onToggle = { expandedCard = if (expandedCard == InsightCard.TREND) null else InsightCard.TREND },
                microVisual = { TrendSparkline(data.weeklyTrend, outcomeFilter) }
            ) {
                RecoveryTrendDetail(data, outcomeFilter)
            }
            InsightCard(
                title = stringResource(R.string.peak_trigger_windows),
                summary = data.peakSummary.localized(),
                expanded = expandedCard == InsightCard.PEAK,
                onToggle = { expandedCard = if (expandedCard == InsightCard.PEAK) null else InsightCard.PEAK },
                microVisual = {
                    data.peakWindow?.let { TagPill(formatFourHourPeriod(it.periodIndex)) }
                }
            ) {
                PeakWindowsDetail(data, outcomeFilter)
            }
            InsightCard(
                title = stringResource(R.string.core_patterns_context),
                summary = corePatternSummary(data),
                expanded = expandedCard == InsightCard.PATTERNS,
                onToggle = { expandedCard = if (expandedCard == InsightCard.PATTERNS) null else InsightCard.PATTERNS },
                microVisual = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.topFeelings.firstOrNull()?.let { TagPill("#${localizedTagName(it.name).take(12)}") }
                        data.topLocations.firstOrNull()?.let { TagPill("#${localizedTagName(it.name).take(12)}") }
                    }
                }
            ) {
                TagBreakdownDetail(data)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InsightCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    microVisual: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val expandCollapseText = stringResource(if (expanded) R.string.collapse_details else R.string.expand_details)
    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle, role = Role.Button)
                    .semantics {
                        contentDescription = "$title. $summary. $expandCollapseText."
                    }
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        summary,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    microVisual()
                }
                Text(
                    stringResource(if (expanded) R.string.tap_to_show_less else R.string.tap_to_explore),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) { detail() }
            }
        }
    }
}

@Composable
private fun RecoveryTrendDetail(data: InsightsData, filter: InsightOutcomeFilter) {
    val actedOnOnly = filter == InsightOutcomeFilter.ACTED_ON_ONLY
    val trendAccent = filter.accentColor() ?: Color(0xFF34D399)
    Text(
        stringResource(if (actedOnOnly) R.string.acted_on_trend_detail_intro else R.string.recovery_detail_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (data.weeklyTrend.none { it.urgeCount > 0 }) {
        EmptyDetail(
            stringResource(if (actedOnOnly) R.string.acted_on_empty else R.string.weekly_trend_empty)
        )
        return
    }

    val latestLoggedWeek = data.weeklyTrend.indexOfLast { it.urgeCount > 0 }.coerceAtLeast(0)
    var selectedIndex by remember(data.weeklyTrend) { mutableIntStateOf(latestLoggedWeek) }
    val selected = data.weeklyTrend.getOrNull(selectedIndex)
    val frequencyMax = data.weeklyTrend.maxOfOrNull { it.urgeCount }?.coerceAtLeast(1) ?: 1
    val chartWidth = maxOf(280, data.weeklyTrend.size * 32).dp
    Column(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.width(chartWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendSeriesHeading(
                title = stringResource(
                    when (filter) {
                        InsightOutcomeFilter.ACTED_ON_ONLY -> R.string.acted_on_frequency_per_week
                        else -> R.string.event_frequency_per_week
                    }
                ),
                scale = stringResource(R.string.count_scale, frequencyMax),
                color = trendAccent,
                dashed = true
            )
            TrendMetricChart(
                trend = data.weeklyTrend,
                selectedIndex = selectedIndex,
                metric = TrendMetric.FREQUENCY,
                onSelectIndex = { selectedIndex = it },
                frequencyColor = trendAccent
            )
            if (!actedOnOnly) {
                TrendSeriesHeading(
                    title = stringResource(R.string.average_reported_intensity),
                    scale = stringResource(R.string.intensity_scale),
                    color = trendAccent,
                    dashed = false
                )
                TrendMetricChart(
                    trend = data.weeklyTrend,
                    selectedIndex = selectedIndex,
                    metric = TrendMetric.INTENSITY,
                    onSelectIndex = { selectedIndex = it },
                    frequencyColor = trendAccent
                )
            }
        }
    }
    selected?.let {
        Text(
            stringResource(
                R.string.selected_week_event_summary,
                it.week,
                it.urgeCount,
                if (!actedOnOnly) it.averageIntensity?.let { average ->
                    stringResource(R.string.average_intensity_summary, intensityLabel(average))
                } ?: stringResource(R.string.no_intensity_data) else ""
            ),
            style = MaterialTheme.typography.titleSmall
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.weeklyTrend.forEachIndexed { index, week ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { selectedIndex = index },
                label = { Text(stringResource(R.string.week_chip, week.week)) }
            )
        }
    }
    Text(
        stringResource(R.string.trend_milestone_note, data.targetMasteryDays),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TrendSeriesHeading(title: String, scale: String, color: Color, dashed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(width = 30.dp, height = 14.dp).semantics { contentDescription = title }) {
                val middle = size.height / 2f
                val markerX = size.width / 2f
                val dashEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
                drawLine(
                    color = color,
                    start = Offset(0f, middle),
                    end = Offset(size.width, middle),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect
                )
                if (dashed) {
                    drawRect(color, topLeft = Offset(markerX - 3.dp.toPx(), middle - 3.dp.toPx()), size = Size(6.dp.toPx(), 6.dp.toPx()))
                } else {
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(markerX, middle))
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        }
        Text(
            stringResource(R.string.scale_label, scale),
            modifier = Modifier.padding(start = 38.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrendMetricChart(
    trend: List<WeeklyUrgeTrend>,
    selectedIndex: Int,
    metric: TrendMetric,
    onSelectIndex: (Int) -> Unit,
    frequencyColor: Color
) {
    val color = when (metric) {
        TrendMetric.FREQUENCY, TrendMetric.INTENSITY -> frequencyColor
    }
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    val selectionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val metricDescription = stringResource(
        if (metric == TrendMetric.FREQUENCY) R.string.weekly_event_frequency_chart else metric.descriptionRes
    )
    val yAxisLabels = when (metric) {
        TrendMetric.FREQUENCY -> {
            val maxFrequency = trend.maxOfOrNull { it.urgeCount }?.coerceAtLeast(1) ?: 1
            listOf(maxFrequency.toString(), ((maxFrequency + 1) / 2).toString(), "0")
        }
        TrendMetric.INTENSITY -> listOf("3", "2", "1")
    }
    val chartDescription = trend.map { week ->
        when (metric) {
            TrendMetric.FREQUENCY -> stringResource(R.string.chart_week_event_frequency, week.week, week.urgeCount)
            TrendMetric.INTENSITY -> stringResource(
                R.string.chart_week_intensity,
                week.week,
                week.averageIntensity?.let { intensityLabel(it) } ?: stringResource(R.string.not_available)
            )
        }
    }.joinToString(". ")
    val chartAccessibilityDescription = "$metricDescription. $chartDescription. ${stringResource(R.string.tap_select_week)}"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.width(22.dp).height(74.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            yAxisLabels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(74.dp)
                .pointerInput(trend.size, metric) {
                    detectTapGestures { tap ->
                        if (trend.isNotEmpty()) {
                            val left = 12.dp.toPx()
                            val rightInset = 12.dp.toPx()
                            val fraction = ((tap.x - left) / (size.width - left - rightInset).coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            onSelectIndex((fraction * (trend.size - 1)).roundToInt())
                        }
                    }
                }
                .semantics { contentDescription = chartAccessibilityDescription }
        ) {
            val left = 12.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val plotHeight = bottom - top
            val xAt: (Int) -> Float = { index ->
                if (trend.size <= 1) (left + right) / 2f
                else left + (right - left) * index / (trend.size - 1)
            }
            val points = when (metric) {
                TrendMetric.FREQUENCY -> {
                    val maxFrequency = trend.maxOfOrNull { it.urgeCount }?.coerceAtLeast(1) ?: 1
                    trend.mapIndexed { index, week ->
                        Offset(xAt(index), bottom - plotHeight * week.urgeCount / maxFrequency)
                    }
                }
                TrendMetric.INTENSITY -> trend.mapIndexedNotNull { index, week ->
                    week.averageIntensity?.let { average ->
                        Offset(xAt(index), bottom - plotHeight * ((average - 1f) / 2f).coerceIn(0f, 1f))
                    }
                }
            }
            listOf(top, top + plotHeight / 2f, bottom).forEach { y ->
                drawLine(axisColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
            }
            if (metric == TrendMetric.INTENSITY && points.size > 1) {
                val fill = Path().apply {
                    moveTo(points.first().x, bottom)
                    lineTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, bottom)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.01f)),
                        startY = top,
                        endY = bottom
                    )
                )
            }
            drawConnectedLine(
                points = points,
                color = color,
                strokeWidth = if (metric == TrendMetric.INTENSITY) 3.dp.toPx() else 2.5f.dp.toPx(),
                dashed = metric == TrendMetric.FREQUENCY
            )
            if (trend.isNotEmpty()) {
                val selectedX = xAt(selectedIndex.coerceIn(0, trend.lastIndex))
                drawLine(selectionColor, Offset(selectedX, top), Offset(selectedX, bottom), 1.dp.toPx())
            }
            points.forEach { point ->
                val selectedX = xAt(selectedIndex.coerceIn(0, trend.lastIndex.coerceAtLeast(0)))
                val radius = if (point.x == selectedX) 5.dp.toPx() else 3.dp.toPx()
                if (metric == TrendMetric.FREQUENCY) {
                    drawRect(color, topLeft = Offset(point.x - radius, point.y - radius), size = Size(radius * 2, radius * 2))
                } else {
                    drawCircle(color, radius = radius, center = point)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConnectedLine(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    dashed: Boolean = false
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color, radius = strokeWidth * 1.5f, center = points.single())
        return
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())) else null
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = pathEffect))
}

private enum class TrendMetric(val descriptionRes: Int) {
    FREQUENCY(R.string.chart_frequency_description),
    INTENSITY(R.string.chart_intensity_description)
}

@Composable
private fun TrendSparkline(trend: List<WeeklyUrgeTrend>, filter: InsightOutcomeFilter) {
    val actedOnOnly = filter == InsightOutcomeFilter.ACTED_ON_ONLY
    val values = if (actedOnOnly) trend.map { it.urgeCount.toFloat() } else trend.mapNotNull { it.averageIntensity }
    val color = filter.accentColor() ?: Color(0xFF34D399)
    val sparklineDescription = stringResource(
        if (actedOnOnly) R.string.weekly_acted_on_sparkline else R.string.weekly_intensity_sparkline
    )
    Canvas(
        modifier = Modifier
            .size(width = 58.dp, height = 36.dp)
            .semantics { contentDescription = sparklineDescription }
    ) {
        if (values.isEmpty()) return@Canvas
        val min = if (actedOnOnly) 0f else 1f
        val range = if (actedOnOnly) (values.maxOrNull() ?: 1f).coerceAtLeast(1f) else 2f
        val points = values.mapIndexed { index, value ->
            val x = if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1)
            Offset(x, size.height - size.height * ((value - min) / range).coerceIn(0f, 1f))
        }
        drawConnectedLine(points, color, 2.dp.toPx())
    }
}

@Composable
private fun PeakWindowsDetail(data: InsightsData, filter: InsightOutcomeFilter) {
    Text(
        stringResource(R.string.peak_detail_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val maxCount = (data.observationHeatmap.flatten() + data.actionHeatmap.flatten()).maxOrNull()?.coerceAtLeast(1) ?: 1
    val accentColor = filter.accentColor()
    Text(stringResource(R.string.observation_baseline_count, data.observationCount), style = MaterialTheme.typography.titleSmall)
    if (data.observationCount > 0) {
        TriggerHeatmapGrid(
            heatmap = data.observationHeatmap,
            maxCount = maxCount,
            highlightCell = data.peakWindow?.takeIf { it.phase == TriggerPhaseOrigin.OBSERVATION }
                ?.let { it.weekdayIndex to it.periodIndex },
            accentColor = accentColor
        )
    } else EmptyDetail(stringResource(R.string.no_observation_logs))

    Text(stringResource(R.string.action_phase_count, data.actionCount), style = MaterialTheme.typography.titleSmall)
    if (data.actionCount > 0) {
        TriggerHeatmapGrid(
            heatmap = data.actionHeatmap,
            maxCount = maxCount,
            highlightCell = data.peakWindow?.takeIf { it.phase == TriggerPhaseOrigin.ACTION }
                ?.let { it.weekdayIndex to it.periodIndex },
            accentColor = accentColor
        )
    } else EmptyDetail(stringResource(R.string.action_patterns_empty))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        HeatLegendCell(maxCount = maxCount, accentColor = accentColor)
        Text(stringResource(R.string.fewer), style = MaterialTheme.typography.labelSmall)
        HeatLegendCell(count = maxCount, maxCount = maxCount, accentColor = accentColor)
        Text(stringResource(R.string.more), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HeatLegendCell(count: Int = 0, maxCount: Int, accentColor: Color? = null) {
    Spacer(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (accentColor == null) androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primary,
                    if (count == 0) 0f else count.toFloat() / maxCount
                ) else androidx.compose.ui.graphics.lerp(
                    Color.Transparent,
                    accentColor,
                    if (count == 0) 0f else count.toFloat() / maxCount
                )
            )
    )
}

@Composable
private fun TagBreakdownDetail(data: InsightsData) {
    Text(
        stringResource(R.string.tag_breakdown_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(stringResource(R.string.feelings), style = MaterialTheme.typography.titleMedium)
    if (data.topFeelings.isEmpty()) EmptyDetail(stringResource(R.string.feeling_patterns_empty))
    else data.topFeelings.forEach { TagBar(it) }
    Text(stringResource(R.string.places_contexts), style = MaterialTheme.typography.titleMedium)
    if (data.topLocations.isEmpty()) EmptyDetail(stringResource(R.string.context_patterns_empty))
    else data.topLocations.forEach { TagBar(it) }
}

@Composable
private fun TagBar(insight: TriggerTagInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(localizedTagName(insight.name), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.percent_count, insight.percent, insight.count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { insight.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TagPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1
    )
}

@Composable
private fun EmptyDetail(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun corePatternSummary(data: InsightsData): String {
    val feeling = data.topFeelings.firstOrNull()
    val location = data.topLocations.firstOrNull()
    return when {
        feeling != null && location != null -> stringResource(
            R.string.top_feeling_and_context,
            localizedTagName(feeling.name), feeling.percent,
            localizedTagName(location.name), location.percent
        )
        feeling != null -> stringResource(R.string.top_feeling, localizedTagName(feeling.name), feeling.percent) + " " + stringResource(R.string.add_context_tag)
        location != null -> stringResource(R.string.top_context, localizedTagName(location.name), location.percent) + " " + stringResource(R.string.add_feeling_tag)
        else -> stringResource(R.string.patterns_empty)
    }
}

@Composable
private fun intensityLabel(value: Float): String = stringResource(when {
    value < 1.5f -> R.string.mild
    value < 2.5f -> R.string.moderate
    else -> R.string.strong
})

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
private fun LocalizedCopy.localized(): String = stringResource(resourceId, *arguments.toTypedArray())

private enum class InsightCard { TREND, PEAK, PATTERNS }

private fun InsightOutcomeFilter.labelResource(): Int = when (this) {
    InsightOutcomeFilter.ALL_URGES -> R.string.insights_filter_all
    InsightOutcomeFilter.ACTED_ON_ONLY -> R.string.insights_filter_acted
    InsightOutcomeFilter.LOGGED_ONLY -> R.string.insights_filter_logged
}

private fun InsightOutcomeFilter.accentColor(): Color? = when (this) {
    InsightOutcomeFilter.ALL_URGES -> null
    InsightOutcomeFilter.ACTED_ON_ONLY -> Color(0xFFC2637E)
    InsightOutcomeFilter.LOGGED_ONLY -> Color(0xFF34D399)
}
