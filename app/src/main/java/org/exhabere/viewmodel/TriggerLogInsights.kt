package org.exhabere.viewmodel

import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import org.exhabere.supportedAppLocale

data class PrimaryTriggerPattern(
    val emotion: String?,
    val location: String?,
    val coOccurrenceCount: Int,
    val totalObservations: Int,
    val emotionPercent: Int,
    val locationPercent: Int,
    val peakHour: Int?
) {
    val isSparse: Boolean get() = totalObservations < MIN_OBSERVATIONS_FOR_PATTERN
}

/** Finds the most frequent feeling/context pair from Observation urge logs only. */
internal fun List<TriggerLogEntity>.primaryTriggerPattern(): PrimaryTriggerPattern {
    val observations = filter {
        it.phaseOrigin == TriggerPhaseOrigin.OBSERVATION &&
            (it.outcomeType == TriggerOutcomeType.URGE_ONLY ||
                it.outcomeType == TriggerOutcomeType.ENGAGED_IN_HABIT)
    }
    val emotionCounts = observations.countTags { it.emotionalState }
    val locationCounts = observations.countTags { it.location }
    val pairs = observations.mapNotNull { log ->
        val emotion = log.emotionalState.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val location = log.location.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        emotion to location
    }.groupingBy { it }.eachCount()
    val primaryPair = pairs.entries.sortedWith(
        compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }
            .thenBy { it.key.first.lowercase(Locale.ROOT) }
            .thenBy { it.key.first }
            .thenBy { it.key.second.lowercase(Locale.ROOT) }
            .thenBy { it.key.second }
    ).firstOrNull()
    val primaryEmotion = primaryPair?.key?.first ?: emotionCounts.firstOrNull()?.first
    val primaryLocation = primaryPair?.key?.second ?: locationCounts.firstOrNull()?.first

    return PrimaryTriggerPattern(
        emotion = primaryEmotion,
        location = primaryLocation,
        coOccurrenceCount = primaryPair?.value ?: 0,
        totalObservations = observations.size,
        emotionPercent = primaryEmotion?.let { tagPercent(emotionCounts, it, observations.size) } ?: 0,
        locationPercent = primaryLocation?.let { tagPercent(locationCounts, it, observations.size) } ?: 0,
        peakHour = observations.peakLocalHour()
    )
}

internal fun formatPeakHourWindow(hour: Int, locale: Locale = supportedAppLocale()): String {
    require(hour in 0..23)
    val calendar = Calendar.getInstance().apply {
        clear()
        set(2000, Calendar.JANUARY, 1, hour, 0)
    }
    val formatter = SimpleDateFormat("h:mm a", locale)
    val start = formatter.format(calendar.time)
    calendar.add(Calendar.HOUR_OF_DAY, 1)
    return "$start – ${formatter.format(calendar.time)}"
}

private fun List<TriggerLogEntity>.countTags(tag: (TriggerLogEntity) -> String): List<Pair<String, Int>> =
    mapNotNull { tag(it).trim().takeIf(String::isNotEmpty) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.lowercase(Locale.ROOT) }
                .thenBy { it.key }
        )
        .map { it.key to it.value }

private fun tagPercent(counts: List<Pair<String, Int>>, tag: String, total: Int): Int =
    if (total == 0) 0 else (counts.firstOrNull { it.first == tag }?.second ?: 0) * 100 / total

private const val MIN_OBSERVATIONS_FOR_PATTERN = 3

internal fun List<TriggerLogEntity>.toWeekdayTimeHeatmap(): List<List<Int>> {
    val counts = List(WEEKDAYS_PER_WEEK) { MutableList(TIME_PERIODS_PER_DAY) { 0 } }
    forEach { log ->
        val calendar = Calendar.getInstance().apply { timeInMillis = log.timestampMillis }
        val mondayFirstDay = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % WEEKDAYS_PER_WEEK
        val fourHourPeriod = calendar.get(Calendar.HOUR_OF_DAY) / HOURS_PER_PERIOD
        counts[mondayFirstDay][fourHourPeriod]++
    }
    return counts
}

internal fun List<TriggerLogEntity>.peakLocalHour(): Int? = takeIf { it.isNotEmpty() }
    ?.groupingBy {
        Calendar.getInstance().apply { timeInMillis = it.timestampMillis }
            .get(Calendar.HOUR_OF_DAY)
    }
    ?.eachCount()
    ?.entries
    ?.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
    ?.firstOrNull()
    ?.key

private const val WEEKDAYS_PER_WEEK = 7
private const val TIME_PERIODS_PER_DAY = 6
private const val HOURS_PER_PERIOD = 4
