package org.exhabere.data.db

import androidx.room.TypeConverter
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

/**
 * Room type converters for mapping domain enums and lists to SQLite primitives.
 */
class Converters {

    @TypeConverter
    fun fromPauseOverlayPosition(value: PauseOverlayPosition?): String? = value?.name

    @TypeConverter
    fun toPauseOverlayPosition(value: String?): PauseOverlayPosition =
        value?.let { runCatching { PauseOverlayPosition.valueOf(it) }.getOrDefault(PauseOverlayPosition.MIDDLE) }
            ?: PauseOverlayPosition.MIDDLE

    @TypeConverter
    fun fromHabitPhase(value: HabitPhase?): String? = value?.name

    @TypeConverter
    fun toHabitPhase(value: String?): HabitPhase? =
        value?.let { runCatching { HabitPhase.valueOf(it) }.getOrDefault(HabitPhase.OBSERVATION) }

    @TypeConverter
    fun fromUrgeIntensity(value: UrgeIntensity?): String? = value?.name

    @TypeConverter
    fun toUrgeIntensity(value: String?): UrgeIntensity? =
        value?.let { runCatching { UrgeIntensity.valueOf(it) }.getOrDefault(UrgeIntensity.MODERATE) }

    @TypeConverter
    fun fromTagCategory(value: TagCategory?): String? = value?.name

    @TypeConverter
    fun toTagCategory(value: String?): TagCategory? =
        value?.let { runCatching { TagCategory.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromTriggerPhaseOrigin(value: TriggerPhaseOrigin?): String? = value?.name

    @TypeConverter
    fun toTriggerPhaseOrigin(value: String?): TriggerPhaseOrigin? =
        value?.let { runCatching { TriggerPhaseOrigin.valueOf(it) }.getOrDefault(TriggerPhaseOrigin.OBSERVATION) }

    @TypeConverter
    fun fromTriggerOutcomeType(value: TriggerOutcomeType?): String? = value?.name

    @TypeConverter
    fun toTriggerOutcomeType(value: String?): TriggerOutcomeType? =
        value?.let { runCatching { TriggerOutcomeType.valueOf(it) }.getOrDefault(TriggerOutcomeType.URGE_ONLY) }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return null
        return list.joinToString(separator = "\u001F") // Unit separator character to avoid collision
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrEmpty()) return emptyList()
        return data.split("\u001F")
    }
}
