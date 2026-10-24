package org.exhabere.ui.action

import org.junit.Assert.assertEquals
import org.junit.Test
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin

class MasteryStatsTest {

    @Test
    fun masteryStatsUseActionInterventionsAndOnlyReportStoredSelections() {
        val stats = calculateMasteryStats(
            logs = listOf(
                emergencyLog("Box Breathing", TriggerOutcomeType.EMERGENCY_PASSED),
                emergencyLog("Box Breathing", TriggerOutcomeType.EMERGENCY_PASSED),
                emergencyLog("Physiological Sigh", TriggerOutcomeType.EMERGENCY_GAVE_IN),
                emergencyLog("Read two pages", TriggerOutcomeType.EMERGENCY_PASSED),
                emergencyLog("Read two pages", TriggerOutcomeType.EMERGENCY_GAVE_IN),
                emergencyLog("Box Breathing", TriggerOutcomeType.EMERGENCY_PASSED, TriggerPhaseOrigin.OBSERVATION),
                emergencyLog("Box Breathing", TriggerOutcomeType.URGE_ONLY)
            ),
            approvedReliefMethods = listOf("Box Breathing", "Physiological Sigh"),
            replacementPlan = "Read two pages"
        )

        assertEquals(3, stats.urgesResisted)
        assertEquals("Box Breathing", stats.topReliefMethod)
        assertEquals(66, stats.topReliefMethodPercent)
        assertEquals(3, stats.reliefMethodSessionCount)
        assertEquals(2, stats.alternativePlanSelectionCount)
    }

    @Test
    fun masteryStatsUseNeutralValuesWhenHistoryHasNoRecognizedInterventions() {
        val stats = calculateMasteryStats(
            logs = listOf(emergencyLog("Older method", TriggerOutcomeType.EMERGENCY_PASSED)),
            approvedReliefMethods = listOf("Box Breathing"),
            replacementPlan = null
        )

        assertEquals(1, stats.urgesResisted)
        assertEquals(null, stats.topReliefMethod)
        assertEquals(null, stats.topReliefMethodPercent)
        assertEquals(0, stats.reliefMethodSessionCount)
        assertEquals(0, stats.alternativePlanSelectionCount)
    }

    private fun emergencyLog(
        method: String,
        outcome: TriggerOutcomeType,
        origin: TriggerPhaseOrigin = TriggerPhaseOrigin.ACTION
    ) = TriggerLogEntity(
        phaseOrigin = origin,
        emotionalState = "Test",
        location = "Test",
        outcomeType = outcome,
        reliefMethodUsed = method
    )
}
