package org.exhabere.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun v1TriggerLogsMigrateWithoutLosingHistory() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """INSERT INTO trigger_logs
                    (id, timestamp_millis, phase_type, emotional_state, location, intensity, outcome, relief_method_used, notes)
                    VALUES (1, 1000, 'OBSERVATION', 'Bored', 'Bed', 'MILD', 'URGE_LOGGED', NULL, NULL)"""
            )
            execSQL(
                """INSERT INTO trigger_logs
                    (id, timestamp_millis, phase_type, emotional_state, location, intensity, outcome, relief_method_used, notes)
                    VALUES (2, 2000, 'OBSERVATION', 'Stressed', 'Desk', 'STRONG', 'HABIT_ENGAGED', NULL, 'legacy note')"""
            )
            execSQL(
                """INSERT INTO trigger_logs
                    (id, timestamp_millis, phase_type, emotional_state, location, intensity, outcome, relief_method_used, notes)
                    VALUES (3, 3000, 'ACTION', 'Emergency Craving', 'On Device', 'MODERATE', 'URGE_PASSED', 'Breathing', NULL)"""
            )
            execSQL(
                """INSERT INTO trigger_logs
                    (id, timestamp_millis, phase_type, emotional_state, location, intensity, outcome, relief_method_used, notes)
                    VALUES (4, 4000, 'MASTERY', 'Emergency Craving', 'On Device', 'MODERATE', 'GAVE_IN', NULL, NULL)"""
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        ).use { db ->
            db.query("SELECT id, phase_origin, outcome_type, notes FROM trigger_logs ORDER BY id").use { cursor ->
                assertEquals(4, cursor.count)
                val expected = listOf(
                    listOf("1", "OBSERVATION", "URGE_ONLY", null),
                    listOf("2", "OBSERVATION", "ENGAGED_IN_HABIT", "legacy note"),
                    listOf("3", "ACTION", "EMERGENCY_PASSED", null),
                    listOf("4", "ACTION", "EMERGENCY_GAVE_IN", null)
                )
                expected.forEach { row ->
                    assertEquals(true, cursor.moveToNext())
                    assertEquals(row[0], cursor.getString(0))
                    assertEquals(row[1], cursor.getString(1))
                    assertEquals(row[2], cursor.getString(2))
                    if (row[3] == null) assertEquals(true, cursor.isNull(3))
                    else assertEquals(row[3], cursor.getString(3))
                }
            }
            db.query("SELECT COUNT(*) FROM archived_habits").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("PRAGMA table_info(habit_config)").use { cursor ->
                var foundPosition = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "pause_overlay_position") {
                        foundPosition = true
                        assertEquals("'MIDDLE'", cursor.getString(4))
                    }
                }
                assertEquals(true, foundPosition)
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "trigger-log-migration"
    }
}
