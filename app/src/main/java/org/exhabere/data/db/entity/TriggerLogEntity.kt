package org.exhabere.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

/**
 * Entity storing individual urge logs and emergency intervention events.
 * Indexed by timestamp, phase origin, and outcome for fast analytics and heatmap queries.
 */
@Entity(
    tableName = "trigger_logs",
    indices = [
        Index(value = ["timestamp_millis"]),
        Index(value = ["phase_origin"]),
        Index(value = ["outcome_type"]),
        Index(value = ["emotional_state"]),
        Index(value = ["location"])
    ]
)
data class TriggerLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "phase_origin")
    val phaseOrigin: TriggerPhaseOrigin,

    @ColumnInfo(name = "emotional_state")
    val emotionalState: String,

    @ColumnInfo(name = "location")
    val location: String,

    @ColumnInfo(name = "intensity")
    val intensity: UrgeIntensity = UrgeIntensity.MODERATE,

    @ColumnInfo(name = "outcome_type")
    val outcomeType: TriggerOutcomeType = TriggerOutcomeType.URGE_ONLY,

    @ColumnInfo(name = "relief_method_used")
    val reliefMethodUsed: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
