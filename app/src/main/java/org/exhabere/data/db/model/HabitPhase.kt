package org.exhabere.data.db.model

/**
 * Represents the current operational phase of the habit breaking journey.
 */
enum class HabitPhase {
    /**
     * Phase 1: Observation Phase (suggested ~7 days).
     * Focuses on frictionless trigger and urge logging without pressure to quit cold-turkey.
     */
    OBSERVATION,

    /**
     * Phase 2: Action Phase.
     * Clean tracker active with real-time emergency intervention and floating lockout friction.
     */
    ACTION,

    /**
     * Phase 3: Mastery.
     * Achieved when the configured clean-time target is reached (66 days by default).
     */
    MASTERY
}
