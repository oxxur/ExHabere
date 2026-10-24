package org.exhabere.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.exhabere.data.db.dao.ContextTagDao
import org.exhabere.data.db.dao.ArchivedHabitDao
import org.exhabere.data.db.dao.HabitConfigDao
import org.exhabere.data.db.dao.TriggerLogDao
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.ArchivedHabitEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity

@Database(
    entities = [
        HabitConfigEntity::class,
        TriggerLogEntity::class,
        ContextTagEntity::class,
        ArchivedHabitEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitConfigDao(): HabitConfigDao
    abstract fun triggerLogDao(): TriggerLogDao
    abstract fun contextTagDao(): ContextTagDao
    abstract fun archivedHabitDao(): ArchivedHabitDao

    companion object {
        const val DATABASE_NAME = "exhabere_local.db"

        /** Renames the log's phase/outcome concepts while retaining every existing row and id. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE trigger_logs_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp_millis INTEGER NOT NULL,
                        phase_origin TEXT NOT NULL,
                        emotional_state TEXT NOT NULL,
                        location TEXT NOT NULL,
                        intensity TEXT NOT NULL,
                        outcome_type TEXT NOT NULL,
                        relief_method_used TEXT,
                        notes TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO trigger_logs_v2 (
                        id, timestamp_millis, phase_origin, emotional_state, location,
                        intensity, outcome_type, relief_method_used, notes
                    )
                    SELECT id, timestamp_millis,
                        CASE phase_type WHEN 'OBSERVATION' THEN 'OBSERVATION' ELSE 'ACTION' END,
                        emotional_state, location, intensity,
                        CASE outcome
                            WHEN 'HABIT_ENGAGED' THEN 'ENGAGED_IN_HABIT'
                            WHEN 'URGE_PASSED' THEN 'EMERGENCY_PASSED'
                            WHEN 'GAVE_IN' THEN 'EMERGENCY_GAVE_IN'
                            ELSE 'URGE_ONLY'
                        END,
                        relief_method_used, notes
                    FROM trigger_logs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE trigger_logs")
                db.execSQL("ALTER TABLE trigger_logs_v2 RENAME TO trigger_logs")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_logs_timestamp_millis ON trigger_logs(timestamp_millis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_logs_phase_origin ON trigger_logs(phase_origin)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_logs_outcome_type ON trigger_logs(outcome_type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_logs_emotional_state ON trigger_logs(emotional_state)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_logs_location ON trigger_logs(location)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS archived_habits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        habit_name TEXT NOT NULL,
                        archived_at_millis INTEGER NOT NULL,
                        backup_json TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_archived_habits_archived_at_millis " +
                        "ON archived_habits(archived_at_millis)"
                )
            }
        }

        /** Adds the configurable pause-window placement, defaulting existing installs to center. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE habit_config ADD COLUMN pause_overlay_position TEXT NOT NULL DEFAULT 'MIDDLE'"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, coroutineScope).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context, coroutineScope: CoroutineScope): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(DatabasePrepopulateCallback(coroutineScope))
                .build()
        }

        /**
         * Prepopulates standard research-backed tags and initial habit defaults on first database creation.
         */
        private class DatabasePrepopulateCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial default data asynchronously
                scope.launch {
                    INSTANCE?.let { database ->
                        seedDefaultTags(database.contextTagDao())
                        seedInitialHabit(database.habitConfigDao())
                    }
                }
            }

            private suspend fun seedDefaultTags(tagDao: ContextTagDao) {
                tagDao.insertTags(InitialHabitDefaults.tags())
            }

            private suspend fun seedInitialHabit(configDao: HabitConfigDao) {
                configDao.upsertHabitConfig(InitialHabitDefaults.habitConfig())
            }
        }
    }
}
