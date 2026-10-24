package org.exhabere.viewmodel

import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.R
import org.exhabere.supportedAppLocale
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

data class LocalizedCopy(val resourceId: Int, val arguments: List<Any> = emptyList())

enum class InsightOutcomeFilter {
    ALL_URGES,
    ACTED_ON_ONLY,
    LOGGED_ONLY;

    fun includes(outcome: TriggerOutcomeType): Boolean = when (this) {
        ALL_URGES -> true
        ACTED_ON_ONLY -> outcome == TriggerOutcomeType.ENGAGED_IN_HABIT ||
            outcome == TriggerOutcomeType.EMERGENCY_GAVE_IN
        LOGGED_ONLY -> outcome == TriggerOutcomeType.URGE_ONLY ||
            outcome == TriggerOutcomeType.EMERGENCY_PASSED
    }
}

data class WeeklyUrgeTrend(
    val week: Int,
    val urgeCount: Int,
    val averageIntensity: Float?
)

data class TriggerTagInsight(
    val name: String,
    val count: Int,
    val percent: Int
)

data class PeakTriggerWindow(
    val weekdayIndex: Int,
    val periodIndex: Int,
    val eventCount: Int,
    val phase: TriggerPhaseOrigin
)

data class InsightsData(
    val targetMasteryDays: Int = 66,
    val weeklyTrend: List<WeeklyUrgeTrend> = emptyList(),
    val recoverySummary: LocalizedCopy = LocalizedCopy(R.string.recovery_default),
    val observationHeatmap: List<List<Int>> = emptyList(),
    val observationCount: Int = 0,
    val actionHeatmap: List<List<Int>> = emptyList(),
    val actionCount: Int = 0,
    val peakWindow: PeakTriggerWindow? = null,
    val peakSummary: LocalizedCopy = LocalizedCopy(R.string.peak_windows_empty),
    val topFeelings: List<TriggerTagInsight> = emptyList(),
    val topLocations: List<TriggerTagInsight> = emptyList()
)

/** Builds reflection-only summaries from quick logs and emergency outcomes with added context. */
fun buildInsightsData(
    logs: List<TriggerLogEntity>,
    config: HabitConfigEntity?,
    nowMillis: Long = System.currentTimeMillis(),
    filter: InsightOutcomeFilter = InsightOutcomeFilter.ALL_URGES
): InsightsData {
    val filteredLogs = logs.filter { filter.includes(it.outcomeType) }
    val observationLogs = filteredLogs.filter {
        it.phaseOrigin == TriggerPhaseOrigin.OBSERVATION && it.isQuickLog()
    }
    val actionEvents = filteredLogs.filter {
        it.phaseOrigin == TriggerPhaseOrigin.ACTION
    }
    val actionQuickLogs = actionEvents.filter { it.isQuickLog() }
    val actionStart = config?.actionPhaseStartDateMillis
        ?: actionQuickLogs.minOfOrNull { it.timestampMillis }
    val elapsedWeeks = actionStart?.let { start ->
        ((nowMillis - start).coerceAtLeast(0L) / MILLIS_PER_WEEK).toInt() + 1
    } ?: 0
    val latestLoggedWeek = actionStart?.let { start ->
        actionQuickLogs.asSequence()
            .filter { it.timestampMillis >= start && it.timestampMillis <= nowMillis }
            .maxOfOrNull { ((it.timestampMillis - start) / MILLIS_PER_WEEK).toInt() + 1 }
    } ?: 0
    // Show elapsed/history weeks only: future weeks are not plotted as misleading zero-count weeks.
    val displayedWeeks = maxOf(elapsedWeeks, latestLoggedWeek).coerceAtLeast(1)
    val weeklyTrend = actionStart?.let { start ->
        val weeklyQuickLogs = actionQuickLogs
            .filter { it.timestampMillis >= start && it.timestampMillis <= nowMillis }
            .groupBy { ((it.timestampMillis - start) / MILLIS_PER_WEEK).toInt() + 1 }
        val weeklyTrendEvents = (if (filter == InsightOutcomeFilter.ACTED_ON_ONLY) actionEvents else actionQuickLogs)
            .filter { it.timestampMillis >= start && it.timestampMillis <= nowMillis }
            .groupBy { ((it.timestampMillis - start) / MILLIS_PER_WEEK).toInt() + 1 }
        (1..displayedWeeks).map { week ->
            val weekLogs = weeklyQuickLogs[week].orEmpty()
            WeeklyUrgeTrend(
                week = week,
                urgeCount = weeklyTrendEvents[week].orEmpty().size,
                averageIntensity = weekLogs.takeIf {
                    it.isNotEmpty() && filter != InsightOutcomeFilter.ACTED_ON_ONLY
                }
                    ?.map { it.intensity.numericValue() }
                    ?.average()
                    ?.toFloat()
            )
        }
    }.orEmpty()
    val observationHeatmap = observationLogs.toInsightsHeatmap()
    val actionHeatmap = actionEvents.toInsightsHeatmap()
    val actionPeak = actionHeatmap.peakWindow(TriggerPhaseOrigin.ACTION)
    val observationPeak = observationHeatmap.peakWindow(TriggerPhaseOrigin.OBSERVATION)
    val peak = actionPeak?.takeIf { actionEvents.isNotEmpty() } ?: observationPeak
    val peakSummary = peak?.let {
        val day = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, (it.weekdayIndex + 1) % 7 + 1)
        }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, supportedAppLocale())
            ?: "${it.weekdayIndex + 1}"
        val resourceId = if (it.phase == TriggerPhaseOrigin.ACTION) R.string.peak_action_summary else R.string.peak_observation_summary
        LocalizedCopy(resourceId, listOf(formatFourHourPeriod(it.periodIndex), day))
    } ?: LocalizedCopy(R.string.peak_windows_empty)

    val allQuickLogs = filteredLogs.filter { it.isQuickLog() }
    return InsightsData(
        targetMasteryDays = config?.targetMasteryDays ?: 66,
        weeklyTrend = weeklyTrend,
        recoverySummary = if (filter == InsightOutcomeFilter.ACTED_ON_ONLY) {
            actedOnRecoverySummary(weeklyTrend)
        } else recoverySummary(weeklyTrend),
        observationHeatmap = observationHeatmap,
        observationCount = observationLogs.size,
        actionHeatmap = actionHeatmap,
        actionCount = actionEvents.size,
        peakWindow = peak,
        peakSummary = peakSummary,
        topFeelings = allQuickLogs.tagInsights { it.emotionalState },
        topLocations = allQuickLogs.tagInsights { it.location }
    )
}

private fun actedOnRecoverySummary(trend: List<WeeklyUrgeTrend>): LocalizedCopy {
    val first = trend.firstOrNull { it.week == 1 }?.urgeCount ?: 0
    val latest = trend.lastOrNull { it.urgeCount > 0 } ?: return LocalizedCopy(R.string.acted_on_empty)
    if (latest.week == 1) return LocalizedCopy(R.string.acted_on_week_one)
    if (first == 0) return LocalizedCopy(R.string.acted_on_first_event, listOf(latest.week))
    val change = ((first - latest.urgeCount).toFloat() / first * 100f).toInt()
    return when {
        change > 0 -> LocalizedCopy(R.string.acted_on_down, listOf(change))
        change < 0 -> LocalizedCopy(R.string.acted_on_up, listOf(-change))
        else -> LocalizedCopy(R.string.acted_on_similar)
    }
}

fun formatFourHourPeriod(index: Int, locale: Locale = supportedAppLocale()): String {
    require(index in 0..5)
    val calendar = Calendar.getInstance().apply {
        clear()
        set(2000, Calendar.JANUARY, 1, index * 4, 0)
    }
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    val start = formatter.format(calendar.time)
    calendar.add(Calendar.HOUR_OF_DAY, 4)
    return "$start–${formatter.format(calendar.time)}"
}

private fun recoverySummary(trend: List<WeeklyUrgeTrend>): LocalizedCopy {
    val first = trend.firstOrNull { it.week == 1 }?.averageIntensity
        ?: return trend.firstOrNull { it.averageIntensity != null }?.let {
            LocalizedCopy(R.string.week_recovery_first_log, listOf(it.week))
        } ?: LocalizedCopy(R.string.recovery_default)
    val latest = trend.lastOrNull { it.averageIntensity != null }
        ?: return LocalizedCopy(R.string.week_one_start)
    if (latest.week == 1) {
        return LocalizedCopy(R.string.week_one_intro)
    }
    val latestIntensity = latest.averageIntensity ?: return LocalizedCopy(R.string.keep_logging_intensity)
    val change = ((first - latestIntensity) / first * 100f).toInt()
    return when {
        change > 0 -> LocalizedCopy(R.string.intensity_down, listOf(change))
        change < 0 -> LocalizedCopy(R.string.intensity_up, listOf(-change))
        else -> LocalizedCopy(R.string.intensity_similar)
    }
}

private fun List<TriggerLogEntity>.tagInsights(tag: (TriggerLogEntity) -> String): List<TriggerTagInsight> {
    val counts = mapNotNull { tag(it).trim().takeIf(String::isNotEmpty) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase(Locale.ROOT) })
    val total = counts.sumOf { it.value }
    return counts.take(5).map { (name, count) ->
        TriggerTagInsight(name, count, if (total == 0) 0 else count * 100 / total)
    }
}

private fun List<TriggerLogEntity>.toInsightsHeatmap(): List<List<Int>> {
    val counts = List(7) { MutableList(6) { 0 } }
    forEach { log ->
        val calendar = Calendar.getInstance().apply { timeInMillis = log.timestampMillis }
        val mondayFirstDay = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        counts[mondayFirstDay][calendar.get(Calendar.HOUR_OF_DAY) / 4]++
    }
    return counts
}

private fun List<List<Int>>.peakWindow(phase: TriggerPhaseOrigin): PeakTriggerWindow? {
    val peak = flatMapIndexed { dayIndex, row ->
        row.mapIndexed { periodIndex, count -> Triple(dayIndex, periodIndex, count) }
    }.maxWithOrNull(compareBy<Triple<Int, Int, Int>> { it.third }.thenByDescending { it.first }.thenByDescending { it.second })
        ?.takeIf { it.third > 0 }
        ?: return null
    return PeakTriggerWindow(peak.first, peak.second, peak.third, phase)
}

private fun TriggerLogEntity.isQuickLog(): Boolean = when (outcomeType) {
    TriggerOutcomeType.URGE_ONLY, TriggerOutcomeType.ENGAGED_IN_HABIT -> true
    TriggerOutcomeType.EMERGENCY_PASSED, TriggerOutcomeType.EMERGENCY_GAVE_IN ->
        emotionalState != "Emergency Craving" || location != "On Device"
}

private fun UrgeIntensity.numericValue(): Int = when (this) {
    UrgeIntensity.MILD -> 1
    UrgeIntensity.MODERATE -> 2
    UrgeIntensity.STRONG -> 3
}

private const val MILLIS_PER_WEEK = 7L * 24L * 60L * 60L * 1_000L
