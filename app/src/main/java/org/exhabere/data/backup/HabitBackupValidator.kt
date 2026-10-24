package org.exhabere.data.backup

import java.util.Locale

object HabitBackupValidator {
    const val CURRENT_FORMAT_VERSION = 1
    const val MAX_TRIGGER_LOGS = 250_000
    const val MAX_CONTEXT_TAGS = 10_000
    private const val MAX_USER_TEXT_LENGTH = 10_000

    fun validate(backup: HabitBackup) {
        require(backup.formatVersion == CURRENT_FORMAT_VERSION) { "This backup version is not supported." }
        require(backup.exportedAtMillis >= 0L) { "The backup has an invalid export date." }
        require(backup.triggerLogs.size <= MAX_TRIGGER_LOGS) { "The backup contains too many log entries." }
        require(backup.contextTags.size <= MAX_CONTEXT_TAGS) { "The backup contains too many context tags." }

        val config = backup.habitConfig
        require(config.id == 1L) { "The backup does not contain a valid habit configuration." }
        require(config.habitName.isNotBlank() && config.habitName.length <= 80) {
            "The backup contains an invalid habit name."
        }
        require(config.defaultTimerMinutes in setOf(2, 5, 10)) { "The backup contains an invalid timer setting." }
        require(config.targetObservationDays in 1..30) { "The backup contains an invalid observation target." }
        require(config.targetMasteryDays in 21..365) { "The backup contains an invalid mastery target." }
        require(config.replacementPlan.orEmpty().length <= 300) { "The backup contains an invalid replacement plan." }
        require(config.approvedReliefTechniques.all { it.isNotBlank() && it.length <= 160 }) {
            "The backup contains an invalid relief-method list."
        }

        require(backup.triggerLogs.all { log ->
            log.id > 0L && log.emotionalState.length <= MAX_USER_TEXT_LENGTH && log.location.length <= MAX_USER_TEXT_LENGTH &&
                (log.reliefMethodUsed?.length ?: 0) <= 200 && (log.notes?.length ?: 0) <= 20_000
        }) { "The backup contains an invalid trigger log." }
        require(backup.triggerLogs.map { it.id }.distinct().size == backup.triggerLogs.size) {
            "The backup contains duplicate trigger-log IDs."
        }

        require(backup.contextTags.all { tag ->
            tag.id > 0L && tag.name.isNotBlank() && tag.name.length <= MAX_USER_TEXT_LENGTH && tag.usageCount >= 0
        }) { "The backup contains an invalid context tag." }
        require(backup.contextTags.map { it.id }.distinct().size == backup.contextTags.size) {
            "The backup contains duplicate context-tag IDs."
        }
        val tagKeys = backup.contextTags.map { "${it.category.name}:${it.name.trim().lowercase(Locale.ROOT)}" }
        require(tagKeys.distinct().size == tagKeys.size) { "The backup contains duplicate context tags." }
    }
}
