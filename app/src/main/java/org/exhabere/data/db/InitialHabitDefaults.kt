package org.exhabere.data.db

import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TagCategory

/** Shared first-habit defaults used by database creation, reset, and archive-and-start-new. */
object InitialHabitDefaults {
    fun tags(): List<ContextTagEntity> = listOf(
        "Bored" to TagCategory.EMOTION,
        "Stressed" to TagCategory.EMOTION,
        "Tired" to TagCategory.EMOTION,
        "Anxious" to TagCategory.EMOTION,
        "Lonely" to TagCategory.EMOTION,
        "Neutral" to TagCategory.EMOTION,
        "Bed" to TagCategory.LOCATION,
        "Desk" to TagCategory.LOCATION,
        "Couch" to TagCategory.LOCATION,
        "Transit" to TagCategory.LOCATION,
        "Social" to TagCategory.LOCATION
    ).map { (name, category) ->
        ContextTagEntity(name = name, category = category, isCustom = false, usageCount = 0)
    }

    fun habitConfig(nowMillis: Long = System.currentTimeMillis()) = HabitConfigEntity(
        id = 1L,
        habitName = "Doomscrolling",
        phase = HabitPhase.OBSERVATION,
        createdAtMillis = nowMillis,
        observationStartDateMillis = nowMillis,
        actionPhaseStartDateMillis = null,
        cleanStreakStartMillis = null,
        defaultTimerMinutes = 5,
        targetObservationDays = 7,
        targetMasteryDays = 66,
        replacementPlan = "When I feel Bored in Bed, I will Read 2 pages of a physical book.",
        approvedReliefTechniques = listOf(
            "Box Breathing Exercise",
            "Physiological Sigh",
            "Cold Water Reset",
            "5-4-3-2-1 Sensory Grounding"
        )
    )
}
