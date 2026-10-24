package org.exhabere.viewmodel

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin

class UiStateTest {

    @Test
    fun observationPeriodCompletesAtConfiguredTargetWithoutGoingPastLastDay() {
        val start = 1_000L
        val oneDay = 24L * 60L * 60L * 1_000L
        val state = ObservationUiState(
            config = HabitConfigEntity(
                habitName = "Test",
                observationStartDateMillis = start,
                targetObservationDays = 7
            )
        )

        assertFalse(state.isObservationPeriodComplete(start + 7 * oneDay - 1))
        assertTrue(state.isObservationPeriodComplete(start + 7 * oneDay))
        val expired = ObservationUiState(
            config = state.config!!.copy(observationStartDateMillis = System.currentTimeMillis() - 8 * oneDay)
        )
        assertEquals(7, expired.observationDay)
    }

    @Test
    fun observationSummaryTrimsTagsAndFindsPeakLocalHour() {
        val logs = listOf(
            observationLog(" Bored ", " Bed ", hour = 22),
            observationLog("Bored", "Bed", hour = 22),
            observationLog("Stressed", "Desk", hour = 18)
        )

        val state = ObservationUiState(logs = logs)

        assertEquals("Bored", state.primaryEmotion)
        assertEquals("Bed", state.primaryLocation)
        assertEquals(22, state.peakHour)
    }

    @Test
    fun observationHeatmapGroupsUrgesByLocalWeekdayAndFourHourPeriod() {
        val state = ObservationUiState(
            logs = listOf(
                observationLog("Bored", "Bed", day = 2, hour = 9),
                observationLog("Bored", "Bed", day = 2, hour = 10),
                observationLog("Stressed", "Desk", day = 3, hour = 22),
                observationLog("Emergency", "Phone", day = 4, hour = 22, outcomeType = TriggerOutcomeType.EMERGENCY_PASSED)
            )
        )

        assertEquals(3, state.loggedUrgeCount)
        assertEquals("Bored", state.primaryEmotion)
        assertEquals(66, state.primaryEmotionPercent)
        assertEquals("Bed", state.primaryLocation)
        assertEquals(66, state.primaryLocationPercent)
        assertEquals(2, state.weekdayTimeHeatmap[0][2]) // Monday, 8 a.m.–noon
        assertEquals(1, state.weekdayTimeHeatmap[1][5]) // Tuesday, 8 p.m.–midnight
        assertEquals(0, state.weekdayTimeHeatmap[2][5]) // Resisted outcomes are not urge logs
    }

    @Test
    fun actionHeatmapIncludesPassedAndGaveInEventsButExcludesOtherPhases() {
        val state = ActionUiState(
            logs = listOf(
                actionLog(TriggerOutcomeType.EMERGENCY_PASSED, day = 2, hour = 21),
                actionLog(TriggerOutcomeType.EMERGENCY_GAVE_IN, day = 2, hour = 22),
                actionLog(TriggerOutcomeType.URGE_ONLY, day = 2, hour = 21),
                observationLog("Bored", "Bed", day = 2, hour = 21)
            )
        )

        assertEquals(3, state.actionUrgeCount)
        assertEquals(21, state.actionPeakHour)
        assertEquals(3, state.actionWeekdayTimeHeatmap[0][5]) // Monday, 8 p.m.–midnight
        assertEquals(0, state.actionWeekdayTimeHeatmap[0][2])
        assertEquals(1, state.observationBaselineCount)
        assertEquals("Bored", state.observationBaselinePrimaryEmotion)
        assertEquals(100, state.observationBaselinePrimaryEmotionPercent)
        assertEquals("Bed", state.observationBaselinePrimaryLocation)
        assertEquals(1, state.observationBaselineWeekdayTimeHeatmap[0][5])
    }

    @Test
    fun resistanceRateCountsOnlyEmergencyOutcomes() {
        val state = ActionUiState(
            logs = listOf(
                actionLog(TriggerOutcomeType.EMERGENCY_PASSED),
                actionLog(TriggerOutcomeType.EMERGENCY_PASSED),
                actionLog(TriggerOutcomeType.EMERGENCY_GAVE_IN),
                actionLog(TriggerOutcomeType.URGE_ONLY),
                observationLog("Bored", "Bed", hour = 21, outcomeType = TriggerOutcomeType.EMERGENCY_PASSED)
            )
        )

        assertEquals(66, state.resistancePercent)
    }

    @Test
    fun masteryMilestonesReflectCleanTime() {
        val config = HabitConfigEntity(
            habitName = "Test habit",
            phase = HabitPhase.ACTION,
            cleanStreakStartMillis = System.currentTimeMillis() - 22L * MILLIS_PER_DAY,
            targetMasteryDays = 66
        )

        val milestones = ActionUiState(config = config).milestones

        assertEquals(listOf(true, true, false), milestones.map { it.completed })
    }

    @Test
    fun primaryTriggerPatternHandlesEmptyAndSingleObservationLists() {
        val empty = emptyList<TriggerLogEntity>().primaryTriggerPattern()
        assertEquals(null, empty.emotion)
        assertEquals(null, empty.location)
        assertEquals(null, empty.peakHour)
        assertEquals(0, empty.totalObservations)
        assertEquals(true, empty.isSparse)

        val single = listOf(observationLog(" Bored ", " Bed ", hour = 22)).primaryTriggerPattern()
        assertEquals("Bored", single.emotion)
        assertEquals("Bed", single.location)
        assertEquals(1, single.coOccurrenceCount)
        assertEquals(22, single.peakHour)
        assertEquals(true, single.isSparse)
    }

    @Test
    fun primaryTriggerPatternBreaksTiesDeterministicallyAndIgnoresOtherOrigins() {
        val logs = listOf(
            observationLog("Stressed", "Desk", hour = 22),
            observationLog("Bored", "Bed", hour = 10),
            observationLog("Stressed", "Desk", hour = 22),
            observationLog("Bored", "Bed", hour = 10),
            actionLog(TriggerOutcomeType.URGE_ONLY, hour = 10)
        )

        val pattern = logs.primaryTriggerPattern()

        assertEquals("Bored", pattern.emotion)
        assertEquals("Bed", pattern.location)
        assertEquals(2, pattern.coOccurrenceCount)
        assertEquals(4, pattern.totalObservations)
        assertEquals(10, pattern.peakHour)
        assertEquals(false, pattern.isSparse)
    }

    @Test
    fun peakHourWindowUsesReadableLocalTwelveHourFormatting() {
        assertEquals("10:00 PM – 11:00 PM", formatPeakHourWindow(22, Locale.US))
        assertEquals("11:00 PM – 12:00 AM", formatPeakHourWindow(23, Locale.US))
    }

    @Test
    fun planSetupCommitRequiresAPlanAndAtLeastOneReliefTechnique() {
        val config = HabitConfigEntity(habitName = "Test habit")
        assertEquals(false, PlanSetupUiState(config = config, replacementPlan = " ").canCommit)
        assertEquals(
            false,
            PlanSetupUiState(config = config, replacementPlan = "A plan", selectedReliefTechniques = emptyList()).canCommit
        )
        assertEquals(
            true,
            PlanSetupUiState(
                config = config,
                isLoading = false,
                replacementPlan = "A plan",
                selectedReliefTechniques = listOf("Box Breathing Exercise")
            ).canCommit
        )
    }

    @Test
    fun planSetupUsesSparseFallbackForVeryShortObservationPeriods() {
        val pattern = PrimaryTriggerPattern(
            emotion = "Bored",
            location = "Bed",
            coOccurrenceCount = 3,
            totalObservations = 3,
            emotionPercent = 100,
            locationPercent = 100,
            peakHour = 22
        )

        val shortObservation = PlanSetupUiState(
            config = HabitConfigEntity(
                habitName = "Test habit",
                observationStartDateMillis = System.currentTimeMillis()
            ),
            triggerPattern = pattern
        )
        val longerObservation = PlanSetupUiState(
            config = HabitConfigEntity(
                habitName = "Test habit",
                observationStartDateMillis = System.currentTimeMillis() - 2L * MILLIS_PER_DAY
            ),
            triggerPattern = pattern
        )

        assertEquals(true, shortObservation.shouldUseSparseFallback)
        assertEquals(false, longerObservation.shouldUseSparseFallback)
    }

    private fun observationLog(
        emotion: String,
        location: String,
        day: Int = 5,
        hour: Int,
        outcomeType: TriggerOutcomeType = TriggerOutcomeType.URGE_ONLY
    ) = TriggerLogEntity(
        timestampMillis = Calendar.getInstance().apply {
            set(2026, Calendar.FEBRUARY, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis,
        phaseOrigin = org.exhabere.data.db.model.TriggerPhaseOrigin.OBSERVATION,
        emotionalState = emotion,
        location = location,
        outcomeType = outcomeType
    )

    private fun actionLog(outcomeType: TriggerOutcomeType, day: Int = 5, hour: Int = 12) = TriggerLogEntity(
        timestampMillis = Calendar.getInstance().apply {
            set(2026, Calendar.FEBRUARY, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis,
        phaseOrigin = TriggerPhaseOrigin.ACTION,
        emotionalState = "Test",
        location = "Test",
        outcomeType = outcomeType
    )

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}
