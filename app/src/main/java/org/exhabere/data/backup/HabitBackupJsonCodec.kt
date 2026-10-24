package org.exhabere.data.backup

import org.json.JSONArray
import org.json.JSONObject
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

/** Versioned JSON serialization for user-controlled ExHabere export/import files. */
object HabitBackupJsonCodec {
    const val FORMAT_NAME = "org.exhabere.habit-backup"
    private const val LEGACY_FORMAT_NAME = "org.openhabit.habit-backup"
    const val MIME_TYPE = "application/json"

    fun encode(backup: HabitBackup): String {
        HabitBackupValidator.validate(backup)
        return JSONObject()
            .put("format", FORMAT_NAME)
            .put("formatVersion", backup.formatVersion)
            .put("exportedAtMillis", backup.exportedAtMillis)
            .put("habitConfig", encodeConfig(backup.habitConfig))
            .put("triggerLogs", JSONArray().apply { backup.triggerLogs.forEach { put(encodeLog(it)) } })
            .put("contextTags", JSONArray().apply { backup.contextTags.forEach { put(encodeTag(it)) } })
            .toString(2)
    }

    fun decode(json: String): HabitBackup {
        try {
            val root = JSONObject(json)
            val formatName = root.getString("format")
            require(formatName == FORMAT_NAME || formatName == LEGACY_FORMAT_NAME) {
                "This file is not an ExHabere habit backup."
            }
            val version = root.getInt("formatVersion")
            require(version == HabitBackupValidator.CURRENT_FORMAT_VERSION) {
                "This backup version is not supported. Update ExHabere and try again."
            }
            val logsJson = root.getJSONArray("triggerLogs")
            val tagsJson = root.getJSONArray("contextTags")
            require(logsJson.length() <= HabitBackupValidator.MAX_TRIGGER_LOGS) {
                "The backup contains too many log entries."
            }
            require(tagsJson.length() <= HabitBackupValidator.MAX_CONTEXT_TAGS) {
                "The backup contains too many context tags."
            }
            return HabitBackup(
                formatVersion = version,
                exportedAtMillis = root.getLong("exportedAtMillis"),
                habitConfig = decodeConfig(root.getJSONObject("habitConfig")),
                triggerLogs = logsJson.toList { decodeLog(getJSONObject(it)) },
                contextTags = tagsJson.toList { decodeTag(getJSONObject(it)) }
            ).also(HabitBackupValidator::validate)
        } catch (failure: IllegalArgumentException) {
            throw failure
        } catch (failure: Exception) {
            throw IllegalArgumentException("This file is not a valid ExHabere habit backup.", failure)
        }
    }

    private fun encodeConfig(config: HabitConfigEntity) = JSONObject()
        .put("id", config.id)
        .put("habitName", config.habitName)
        .put("phase", config.phase.name)
        .put("createdAtMillis", config.createdAtMillis)
        .put("observationStartDateMillis", config.observationStartDateMillis)
        .put("actionPhaseStartDateMillis", config.actionPhaseStartDateMillis ?: JSONObject.NULL)
        .put("cleanStreakStartMillis", config.cleanStreakStartMillis ?: JSONObject.NULL)
        .put("defaultTimerMinutes", config.defaultTimerMinutes)
        .put("pauseOverlayPosition", config.pauseOverlayPosition.name)
        .put("targetObservationDays", config.targetObservationDays)
        .put("targetMasteryDays", config.targetMasteryDays)
        .put("replacementPlan", config.replacementPlan ?: JSONObject.NULL)
        .put("approvedReliefTechniques", config.approvedReliefTechniques.toJsonArray())

    private fun decodeConfig(json: JSONObject) = HabitConfigEntity(
        id = json.getLong("id"),
        habitName = json.getString("habitName"),
        phase = json.getEnum("phase"),
        createdAtMillis = json.getLong("createdAtMillis"),
        observationStartDateMillis = json.getLong("observationStartDateMillis"),
        actionPhaseStartDateMillis = json.getNullableLong("actionPhaseStartDateMillis"),
        cleanStreakStartMillis = json.getNullableLong("cleanStreakStartMillis"),
        defaultTimerMinutes = json.getInt("defaultTimerMinutes"),
        // Backups created before this setting existed retain the default centered position.
        pauseOverlayPosition = if (json.has("pauseOverlayPosition")) {
            json.getEnum("pauseOverlayPosition")
        } else {
            PauseOverlayPosition.MIDDLE
        },
        targetObservationDays = json.getInt("targetObservationDays"),
        targetMasteryDays = json.getInt("targetMasteryDays"),
        replacementPlan = json.getNullableString("replacementPlan"),
        approvedReliefTechniques = json.getJSONArray("approvedReliefTechniques").toList { getString(it) }
    )

    private fun encodeLog(log: TriggerLogEntity) = JSONObject()
        .put("id", log.id)
        .put("timestampMillis", log.timestampMillis)
        .put("phaseOrigin", log.phaseOrigin.name)
        .put("emotionalState", log.emotionalState)
        .put("location", log.location)
        .put("intensity", log.intensity.name)
        .put("outcomeType", log.outcomeType.name)
        .put("reliefMethodUsed", log.reliefMethodUsed ?: JSONObject.NULL)
        .put("notes", log.notes ?: JSONObject.NULL)

    private fun decodeLog(json: JSONObject) = TriggerLogEntity(
        id = json.getLong("id"),
        timestampMillis = json.getLong("timestampMillis"),
        phaseOrigin = json.getEnum("phaseOrigin"),
        emotionalState = json.getString("emotionalState"),
        location = json.getString("location"),
        intensity = json.getEnum("intensity"),
        outcomeType = json.getEnum("outcomeType"),
        reliefMethodUsed = json.getNullableString("reliefMethodUsed"),
        notes = json.getNullableString("notes")
    )

    private fun encodeTag(tag: ContextTagEntity) = JSONObject()
        .put("id", tag.id)
        .put("name", tag.name)
        .put("category", tag.category.name)
        .put("isCustom", tag.isCustom)
        .put("usageCount", tag.usageCount)
        .put("lastUsedMillis", tag.lastUsedMillis)

    private fun decodeTag(json: JSONObject) = ContextTagEntity(
        id = json.getLong("id"),
        name = json.getString("name"),
        category = json.getEnum("category"),
        isCustom = json.getBoolean("isCustom"),
        usageCount = json.getInt("usageCount"),
        lastUsedMillis = json.getLong("lastUsedMillis")
    )

    private inline fun <reified T : Enum<T>> JSONObject.getEnum(key: String): T =
        try {
            enumValueOf<T>(getString(key))
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid $key value", failure)
        }

    private fun JSONObject.getNullableLong(key: String): Long? {
        require(has(key)) { "Missing $key" }
        return if (isNull(key)) null else getLong(key)
    }

    private fun JSONObject.getNullableString(key: String): String? {
        require(has(key)) { "Missing $key" }
        return if (isNull(key)) null else getString(key)
    }

    private fun List<String>.toJsonArray() = JSONArray().also { array -> forEach(array::put) }

    private inline fun <T> JSONArray.toList(read: JSONArray.(Int) -> T): List<T> =
        List(length()) { index -> read(index) }
}
