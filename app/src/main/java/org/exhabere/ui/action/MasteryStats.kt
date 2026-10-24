package org.exhabere.ui.action

import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin

/** Aggregate mastery details that can be derived from the intervention data currently stored. */
data class MasteryStats(
    val urgesResisted: Int,
    val topReliefMethod: String?,
    val topReliefMethodPercent: Int?,
    val reliefMethodSessionCount: Int,
    val alternativePlanSelectionCount: Int
)

fun calculateMasteryStats(
    logs: List<TriggerLogEntity>,
    approvedReliefMethods: List<String>,
    replacementPlan: String?
): MasteryStats {
    val emergencyLogs = logs.filter {
        it.phaseOrigin == TriggerPhaseOrigin.ACTION &&
            it.outcomeType in EMERGENCY_OUTCOMES
    }
    val plan = replacementPlan?.trim()?.takeIf(String::isNotEmpty)
    val normalizedReliefMethods = approvedReliefMethods
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { it.equals(plan, ignoreCase = true) }
        .toSet()

    val alternativeSelections = if (plan == null) {
        0
    } else {
        emergencyLogs.count { log ->
            log.reliefMethodUsed?.trim()?.equals(plan, ignoreCase = true) == true
        }
    }
    val reliefMethodCounts = emergencyLogs.mapNotNull { log ->
        val method = log.reliefMethodUsed?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        method.takeIf { candidate -> normalizedReliefMethods.any { it.equals(candidate, ignoreCase = true) } }
    }.groupingBy { it }.eachCount()
    val topMethod = reliefMethodCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
    val reliefSessionCount = reliefMethodCounts.values.sum()

    return MasteryStats(
        urgesResisted = emergencyLogs.count { it.outcomeType == TriggerOutcomeType.EMERGENCY_PASSED },
        topReliefMethod = topMethod?.key,
        topReliefMethodPercent = topMethod?.let { (it.value * 100 / reliefSessionCount).coerceIn(0, 100) },
        reliefMethodSessionCount = reliefSessionCount,
        alternativePlanSelectionCount = alternativeSelections
    )
}

private val EMERGENCY_OUTCOMES = setOf(
    TriggerOutcomeType.EMERGENCY_PASSED,
    TriggerOutcomeType.EMERGENCY_GAVE_IN
)
