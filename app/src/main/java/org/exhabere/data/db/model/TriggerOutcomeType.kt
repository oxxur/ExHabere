package org.exhabere.data.db.model

/** Distinguishes a quick urge log from the two outcomes of an emergency intervention. */
enum class TriggerOutcomeType {
    URGE_ONLY,
    ENGAGED_IN_HABIT,
    EMERGENCY_PASSED,
    EMERGENCY_GAVE_IN
}
