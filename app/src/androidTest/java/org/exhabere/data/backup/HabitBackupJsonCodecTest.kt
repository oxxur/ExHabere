package org.exhabere.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.model.PauseOverlayPosition
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitBackupJsonCodecTest {
    @Test
    fun roundTripsConfiguredPauseOverlayPosition() {
        val backup = HabitBackup(
            formatVersion = HabitBackupValidator.CURRENT_FORMAT_VERSION,
            exportedAtMillis = 10L,
            habitConfig = HabitConfigEntity(
                habitName = "Habit",
                pauseOverlayPosition = PauseOverlayPosition.BOTTOM
            ),
            triggerLogs = emptyList(),
            contextTags = emptyList()
        )

        val restored = HabitBackupJsonCodec.decode(HabitBackupJsonCodec.encode(backup))

        assertEquals(PauseOverlayPosition.BOTTOM, restored.habitConfig.pauseOverlayPosition)
    }

    @Test
    fun olderBackupWithoutPositionDefaultsToMiddle() {
        val backup = HabitBackup(
            formatVersion = HabitBackupValidator.CURRENT_FORMAT_VERSION,
            exportedAtMillis = 10L,
            habitConfig = HabitConfigEntity(habitName = "Habit"),
            triggerLogs = emptyList(),
            contextTags = emptyList()
        )
        val json = JSONObject(HabitBackupJsonCodec.encode(backup))
        json.getJSONObject("habitConfig").remove("pauseOverlayPosition")

        val restored = HabitBackupJsonCodec.decode(json.toString())

        assertEquals(PauseOverlayPosition.MIDDLE, restored.habitConfig.pauseOverlayPosition)
    }
}
