package org.exhabere.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition

/**
 * Entity representing the single targeted habit configuration and progress state.
 * Supports the app's single-habit focus principle with local-only storage.
 */
@Entity(tableName = "habit_config")
data class HabitConfigEntity(
    @PrimaryKey
    val id: Long = 1L,

    @ColumnInfo(name = "habit_name")
    val habitName: String,

    @ColumnInfo(name = "current_phase")
    val phase: HabitPhase = HabitPhase.OBSERVATION,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),

    /**
     * Timestamp marking the start of Phase 1 (Observation).
     */
    @ColumnInfo(name = "observation_start_date_millis")
    val observationStartDateMillis: Long = System.currentTimeMillis(),

    /**
     * Timestamp when the user transitioned into Phase 2 (Action Phase).
     * Null if the user is still in Phase 1.
     */
    @ColumnInfo(name = "action_phase_start_date_millis")
    val actionPhaseStartDateMillis: Long? = null,

    /**
     * Anchor timestamp for the current clean time calculation.
     * Reset only when explicitly logged as gave-in or recalibrated.
     */
    @ColumnInfo(name = "clean_streak_start_millis")
    val cleanStreakStartMillis: Long? = null,

    /**
     * Default countdown timer duration in minutes (typically 2, 5, or 10 min; default 5 min).
     */
    @ColumnInfo(name = "default_timer_minutes")
    val defaultTimerMinutes: Int = 5,

    /** Preferred placement for the emergency pause overlay (defaults to the center). */
    @ColumnInfo(name = "pause_overlay_position", defaultValue = "'MIDDLE'")
    val pauseOverlayPosition: PauseOverlayPosition = PauseOverlayPosition.MIDDLE,

    /**
     * Target observation duration in days before recommending transition to Phase 2 (default: 7 days).
     */
    @ColumnInfo(name = "target_observation_days")
    val targetObservationDays: Int = 7,

    /**
     * User-configured clean-time milestone target in days (66 days by default; a reference, not a deadline).
     */
    @ColumnInfo(name = "target_mastery_days")
    val targetMasteryDays: Int = 66,

    /**
     * Pre-determined "If-Then" implementation intention replacement plan:
     * e.g., "When I feel Bored in Bed, I will Read 2 pages of a physical book."
     */
    @ColumnInfo(name = "replacement_plan")
    val replacementPlan: String? = null,

    /**
     * Pre-approved urge relief grounding techniques (Box Breathing, Mammalian Dive, etc.).
     */
    @ColumnInfo(name = "approved_relief_techniques")
    val approvedReliefTechniques: List<String> = emptyList()
)
