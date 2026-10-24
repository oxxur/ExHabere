package org.exhabere.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerPhaseOrigin

class HabitBackupValidatorTest {

    @Test
    fun acceptsACompleteSingleHabitSnapshot() {
        val backup = HabitBackup(
            formatVersion = HabitBackupValidator.CURRENT_FORMAT_VERSION,
            exportedAtMillis = 100L,
            habitConfig = HabitConfigEntity(habitName = "Habit"),
            triggerLogs = listOf(
                TriggerLogEntity(
                    id = 1L,
                    timestampMillis = 90L,
                    phaseOrigin = TriggerPhaseOrigin.OBSERVATION,
                    emotionalState = "Bored",
                    location = "Bed"
                )
            ),
            contextTags = listOf(
                ContextTagEntity(id = 1L, name = "Bored", category = TagCategory.EMOTION)
            )
        )

        HabitBackupValidator.validate(backup)

        assertEquals(1, backup.triggerLogs.size)
    }

    @Test
    fun rejectsUnknownFormatVersionAndDuplicateContextTags() {
        val valid = HabitBackup(
            formatVersion = HabitBackupValidator.CURRENT_FORMAT_VERSION,
            exportedAtMillis = 100L,
            habitConfig = HabitConfigEntity(habitName = "Habit"),
            triggerLogs = emptyList(),
            contextTags = listOf(ContextTagEntity(id = 1L, name = "Bored", category = TagCategory.EMOTION))
        )

        assertThrows(IllegalArgumentException::class.java) {
            HabitBackupValidator.validate(valid.copy(formatVersion = 99))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitBackupValidator.validate(
                valid.copy(
                    contextTags = valid.contextTags +
                        ContextTagEntity(id = 2L, name = "bored", category = TagCategory.EMOTION)
                )
            )
        }
    }
}
