package org.exhabere.viewmodel

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.exhabere.R
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

class InsightsDataTest {

    @Test
    fun insightSummariesCompareActionTrendAndContextWithoutEmergencyNoise() {
        val actionStart = timestamp(day = 2, hour = 9)
        val logs = listOf(
            log(actionStart + 4 * HOUR, TriggerPhaseOrigin.ACTION, "Bored", "Bed", UrgeIntensity.STRONG),
            log(actionStart + 4 * HOUR + 30 * MINUTE, TriggerPhaseOrigin.ACTION, "Bored", "Bed", UrgeIntensity.STRONG),
            log(actionStart + 14 * DAY + 4 * HOUR, TriggerPhaseOrigin.ACTION, "Anxious", "Couch", UrgeIntensity.MILD),
            log(actionStart + 2 * DAY, TriggerPhaseOrigin.OBSERVATION, "Bored", "Bed", UrgeIntensity.MODERATE),
            log(
                actionStart + 3 * DAY,
                TriggerPhaseOrigin.ACTION,
                "Emergency Craving",
                "On Device",
                UrgeIntensity.MODERATE,
                TriggerOutcomeType.EMERGENCY_PASSED
            )
        )
        val config = HabitConfigEntity(
            habitName = "Test habit",
            phase = HabitPhase.ACTION,
            actionPhaseStartDateMillis = actionStart,
            targetMasteryDays = 66
        )

        val insights = buildInsightsData(logs, config, nowMillis = actionStart + 40 * DAY)

        assertEquals(2, insights.weeklyTrend[0].urgeCount)
        assertEquals(3f, insights.weeklyTrend[0].averageIntensity)
        assertEquals(0, insights.weeklyTrend[1].urgeCount)
        assertEquals(1, insights.weeklyTrend[2].urgeCount)
        assertEquals(1f, insights.weeklyTrend[2].averageIntensity)
        assertEquals(LocalizedCopy(R.string.intensity_down, listOf(66)), insights.recoverySummary)
        assertEquals(1, insights.observationCount)
        assertEquals(4, insights.actionCount) // Includes the emergency heatmap event.
        assertEquals("Bored", insights.topFeelings.first().name)
        assertEquals(75, insights.topFeelings.first().percent)
        assertEquals("Bed", insights.topLocations.first().name)
        assertEquals(75, insights.topLocations.first().percent)
        assertNotNull(insights.peakWindow)
        assertEquals(TriggerPhaseOrigin.ACTION, insights.peakWindow?.phase)
        assertEquals(0, insights.peakWindow?.weekdayIndex) // Monday, Monday-first
        assertEquals(3, insights.peakWindow?.periodIndex) // 12–4 PM
    }

    @Test
    fun missingActionHistoryUsesObservationPeakAndPlainLanguageFallbacks() {
        val observation = log(
            timestamp(day = 3, hour = 22),
            TriggerPhaseOrigin.OBSERVATION,
            "Bored",
            "Bed",
            UrgeIntensity.MODERATE
        )

        val insights = buildInsightsData(listOf(observation), config = null)

        assertEquals(0, insights.actionCount)
        assertEquals(1, insights.observationCount)
        assertEquals(TriggerPhaseOrigin.OBSERVATION, insights.peakWindow?.phase)
        assertEquals(LocalizedCopy(R.string.recovery_default), insights.recoverySummary)
        assertEquals("Bored", insights.topFeelings.first().name)
    }

    @Test
    fun outcomeFiltersKeepEmergencyEventsInHeatmapsAndOnlyMatchingEventsElsewhere() {
        val actionStart = timestamp(day = 2, hour = 9)
        val logs = listOf(
            log(actionStart + HOUR, TriggerPhaseOrigin.ACTION, "Bored", "Bed", UrgeIntensity.STRONG,
                TriggerOutcomeType.ENGAGED_IN_HABIT),
            log(actionStart + 2 * HOUR, TriggerPhaseOrigin.ACTION, "Emergency Craving", "On Device", UrgeIntensity.MODERATE,
                TriggerOutcomeType.EMERGENCY_PASSED),
            log(actionStart + 8 * DAY, TriggerPhaseOrigin.ACTION, "Stressed", "Desk", UrgeIntensity.MILD,
                TriggerOutcomeType.URGE_ONLY),
            log(actionStart + 8 * DAY + HOUR, TriggerPhaseOrigin.ACTION, "Emergency Craving", "On Device", UrgeIntensity.MODERATE,
                TriggerOutcomeType.EMERGENCY_GAVE_IN),
            log(actionStart + 3 * HOUR, TriggerPhaseOrigin.OBSERVATION, "Bored", "Bed", UrgeIntensity.MODERATE)
        )
        val config = HabitConfigEntity(
            habitName = "Test habit",
            phase = HabitPhase.ACTION,
            actionPhaseStartDateMillis = actionStart,
            targetMasteryDays = 66
        )
        val now = actionStart + 14 * DAY

        val all = buildInsightsData(logs, config, now, InsightOutcomeFilter.ALL_URGES)
        val actedOn = buildInsightsData(logs, config, now, InsightOutcomeFilter.ACTED_ON_ONLY)
        val logged = buildInsightsData(logs, config, now, InsightOutcomeFilter.LOGGED_ONLY)

        assertEquals(4, all.actionCount)
        assertEquals(1, all.weeklyTrend[0].urgeCount) // Recovery count remains quick-log-only.
        assertEquals(2, actedOn.actionCount)
        assertEquals(listOf(1, 1, 0), actedOn.weeklyTrend.map { it.urgeCount })
        assertEquals(listOf(null, null, null), actedOn.weeklyTrend.map { it.averageIntensity })
        assertEquals(listOf(0, 1, 0), logged.weeklyTrend.map { it.urgeCount })
        assertEquals(2, logged.actionCount) // Logged-only heatmap also includes EMERGENCY_PASSED.
        assertEquals("Bored", actedOn.topFeelings.single().name) // Emergency-only rows have no tags.
        assertEquals("Bored", logged.topFeelings.first().name)
    }

    @Test
    fun emergencyOutcomeWithFollowUpDetailsIsIncludedInLoggedUrgeTrends() {
        val actionStart = timestamp(day = 2, hour = 9)
        val detailedEmergency = log(
            actionStart + HOUR,
            TriggerPhaseOrigin.ACTION,
            "Anxious",
            "Desk",
            UrgeIntensity.STRONG,
            TriggerOutcomeType.EMERGENCY_PASSED
        )
        val config = HabitConfigEntity(
            habitName = "Test habit",
            phase = HabitPhase.ACTION,
            actionPhaseStartDateMillis = actionStart
        )

        val insights = buildInsightsData(
            logs = listOf(detailedEmergency),
            config = config,
            nowMillis = actionStart + DAY,
            filter = InsightOutcomeFilter.LOGGED_ONLY
        )

        assertEquals(1, insights.weeklyTrend.single().urgeCount)
        assertEquals(3f, insights.weeklyTrend.single().averageIntensity)
        assertEquals("Anxious", insights.topFeelings.single().name)
        assertEquals("Desk", insights.topLocations.single().name)
    }

    @Test
    fun trendTimelineContinuesAfterTheDefaultTenWeekMilestonePath() {
        val actionStart = timestamp(day = 2, hour = 9)
        val weekElevenLog = log(
            actionStart + 10 * WEEK + 2 * HOUR,
            TriggerPhaseOrigin.ACTION,
            "Bored",
            "Bed",
            UrgeIntensity.MILD
        )
        val config = HabitConfigEntity(
            habitName = "Test habit",
            phase = HabitPhase.ACTION,
            actionPhaseStartDateMillis = actionStart,
            targetMasteryDays = 66
        )

        val insights = buildInsightsData(
            logs = listOf(weekElevenLog),
            config = config,
            nowMillis = actionStart + 11 * WEEK
        )

        assertEquals(12, insights.weeklyTrend.size)
        assertEquals(1, insights.weeklyTrend[10].urgeCount)
    }

    private fun log(
        timestamp: Long,
        origin: TriggerPhaseOrigin,
        emotion: String,
        location: String,
        intensity: UrgeIntensity,
        outcome: TriggerOutcomeType = TriggerOutcomeType.URGE_ONLY
    ) = TriggerLogEntity(
        timestampMillis = timestamp,
        phaseOrigin = origin,
        emotionalState = emotion,
        location = location,
        intensity = intensity,
        outcomeType = outcome
    )

    private fun timestamp(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.FEBRUARY, day, hour, 0)
    }.timeInMillis

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * 60 * MINUTE
        const val WEEK = 7 * DAY
    }
}
