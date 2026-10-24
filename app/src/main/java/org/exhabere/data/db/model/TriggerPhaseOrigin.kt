package org.exhabere.data.db.model

/** Phase in which a trigger was first recorded; independent of the habit's current phase. */
enum class TriggerPhaseOrigin {
    OBSERVATION,
    ACTION
}
