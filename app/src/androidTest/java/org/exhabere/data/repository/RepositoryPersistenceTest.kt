package org.exhabere.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.exhabere.data.backup.HabitBackupJsonCodec
import org.exhabere.data.db.AppDatabase
import org.exhabere.data.db.InitialHabitDefaults
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity

@RunWith(AndroidJUnit4::class)
class RepositoryPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var triggerLogRepository: TriggerLogRepositoryImpl
    private lateinit var habitBackupRepository: HabitBackupRepositoryImpl
    private lateinit var habitArchiveRepository: HabitArchiveRepositoryImpl

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).build()
        triggerLogRepository = TriggerLogRepositoryImpl(
            triggerLogDao = database.triggerLogDao(),
            contextTagDao = database.contextTagDao(),
            habitConfigDao = database.habitConfigDao(),
            database = database
        )
        habitBackupRepository = HabitBackupRepositoryImpl(
            database = database,
            habitConfigDao = database.habitConfigDao(),
            triggerLogDao = database.triggerLogDao(),
            contextTagDao = database.contextTagDao()
        )
        habitArchiveRepository = HabitArchiveRepositoryImpl(
            database = database,
            archivedHabitDao = database.archivedHabitDao(),
            habitConfigDao = database.habitConfigDao(),
            triggerLogDao = database.triggerLogDao(),
            contextTagDao = database.contextTagDao()
        )
        database.habitConfigDao().upsertHabitConfig(testConfig())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun recordLogPersistsLogAndUpdatesBuiltInAndCustomTags() = runBlocking {
        database.contextTagDao().insertTag(tag("Bored", TagCategory.EMOTION))

        val logId = triggerLogRepository.recordLog(
            log = testLog(emotion = "  Bored ", location = " Home "),
            isCustomEmotion = false,
            isCustomLocation = true
        )

        val savedLog = database.triggerLogDao().getRecentLogs(1).single()
        assertEquals(logId, savedLog.id)
        assertEquals("  Bored ", savedLog.emotionalState)
        assertEquals(1, database.contextTagDao().findTagByName(TagCategory.EMOTION, "Bored")?.usageCount)

        val savedCustomTag = database.contextTagDao().findTagByName(TagCategory.LOCATION, "Home")
        assertNotNull(savedCustomTag)
        assertTrue(savedCustomTag!!.isCustom)
        assertEquals(1, savedCustomTag.usageCount)
    }

    @Test
    fun failedLogInsertRollsBackTagWrites() = runBlocking {
        val existingLocation = tag("Desk", TagCategory.LOCATION, usageCount = 3, lastUsedMillis = 42L)
        database.contextTagDao().insertTag(existingLocation)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_trigger_log_insert
            BEFORE INSERT ON trigger_logs
            BEGIN
                SELECT RAISE(ABORT, 'injected log insert failure');
            END
            """.trimIndent()
        )

        val failure = runCatching {
            triggerLogRepository.recordLog(
                log = testLog(emotion = "Fresh emotion", location = "Desk"),
                isCustomEmotion = true,
                isCustomLocation = false
            )
        }.exceptionOrNull()

        assertNotNull("The injected trigger should reject the log insert", failure)
        assertNull(database.contextTagDao().findTagByName(TagCategory.EMOTION, "Fresh emotion"))
        val unchangedLocation = database.contextTagDao().findTagByName(TagCategory.LOCATION, "Desk")
        assertEquals(3, unchangedLocation?.usageCount)
        assertEquals(42L, unchangedLocation?.lastUsedMillis)
        assertTrue(database.triggerLogDao().getRecentLogs(10).isEmpty())
    }

    @Test
    fun gaveInOutcomeIsPersistedAndResetsCleanStreak() = runBlocking {
        val oldStreakStart = 123L
        database.habitConfigDao().upsertHabitConfig(
            testConfig(phase = HabitPhase.ACTION, cleanStreakStartMillis = oldStreakStart)
        )

        triggerLogRepository.recordEmergencyOutcome(
            reliefMethod = "Box Breathing",
            outcomeType = TriggerOutcomeType.EMERGENCY_GAVE_IN,
            notes = "A difficult evening"
        )

        val updatedConfig = database.habitConfigDao().getHabitConfig()
        assertNotNull(updatedConfig)
        assertTrue(updatedConfig!!.cleanStreakStartMillis!! > oldStreakStart)

        val savedLog = database.triggerLogDao().getRecentLogs(1).single()
        assertEquals(TriggerPhaseOrigin.ACTION, savedLog.phaseOrigin)
        assertEquals(TriggerOutcomeType.EMERGENCY_GAVE_IN, savedLog.outcomeType)
        assertEquals("Box Breathing", savedLog.reliefMethodUsed)
        assertEquals("A difficult evening", savedLog.notes)
    }

    @Test
    fun emergencyDetailsUpdateTheExistingOutcomeInsteadOfCreatingAnotherUrge() = runBlocking {
        val logId = triggerLogRepository.recordEmergencyOutcome(
            reliefMethod = "Box Breathing",
            outcomeType = TriggerOutcomeType.EMERGENCY_PASSED
        )

        triggerLogRepository.addEmergencyLogDetails(
            logId = logId,
            emotion = "Anxious",
            location = "Desk",
            intensity = UrgeIntensity.STRONG
        )

        val logs = database.triggerLogDao().getRecentLogs(10)
        assertEquals(1, logs.size)
        assertEquals(TriggerOutcomeType.EMERGENCY_PASSED, logs.single().outcomeType)
        assertEquals("Anxious", logs.single().emotionalState)
        assertEquals("Desk", logs.single().location)
        assertEquals(UrgeIntensity.STRONG, logs.single().intensity)
        assertEquals(1, database.contextTagDao().findTagByName(TagCategory.EMOTION, "Anxious")?.usageCount)
        assertEquals(1, database.contextTagDao().findTagByName(TagCategory.LOCATION, "Desk")?.usageCount)
    }

    @Test
    fun actionEngagementResetsCleanTimeButUrgeOnlyDoesNot() = runBlocking {
        val cleanSince = 123L
        database.habitConfigDao().upsertHabitConfig(
            testConfig(phase = HabitPhase.ACTION, cleanStreakStartMillis = cleanSince)
        )

        triggerLogRepository.recordLog(
            log = TriggerLogEntity(
                phaseOrigin = TriggerPhaseOrigin.ACTION,
                emotionalState = "Stressed",
                location = "Desk",
                outcomeType = TriggerOutcomeType.URGE_ONLY
            )
        )

        assertEquals(cleanSince, database.habitConfigDao().getHabitConfig()?.cleanStreakStartMillis)
        triggerLogRepository.recordLog(
            log = TriggerLogEntity(
                phaseOrigin = TriggerPhaseOrigin.ACTION,
                emotionalState = "Stressed",
                location = "Desk",
                outcomeType = TriggerOutcomeType.ENGAGED_IN_HABIT
            )
        )
        assertTrue(database.habitConfigDao().getHabitConfig()!!.cleanStreakStartMillis!! > cleanSince)
        val savedLog = database.triggerLogDao().getRecentLogs(1).single()
        assertEquals(TriggerPhaseOrigin.ACTION, savedLog.phaseOrigin)
        assertEquals(TriggerOutcomeType.ENGAGED_IN_HABIT, savedLog.outcomeType)
    }

    @Test
    fun observationEngagementDoesNotResetActionCleanTime() = runBlocking {
        val cleanSince = 123L
        database.habitConfigDao().upsertHabitConfig(
            testConfig(phase = HabitPhase.OBSERVATION, cleanStreakStartMillis = cleanSince)
        )

        triggerLogRepository.recordLog(
            log = TriggerLogEntity(
                phaseOrigin = TriggerPhaseOrigin.OBSERVATION,
                emotionalState = "Stressed",
                location = "Desk",
                outcomeType = TriggerOutcomeType.ENGAGED_IN_HABIT
            )
        )

        assertEquals(cleanSince, database.habitConfigDao().getHabitConfig()?.cleanStreakStartMillis)
    }

    @Test
    fun returningThroughObservationPreservesExistingCleanTime() = runBlocking {
        val cleanSince = 123L
        val repository = habitRepository()
        database.habitConfigDao().upsertHabitConfig(
            testConfig(phase = HabitPhase.ACTION, cleanStreakStartMillis = cleanSince)
        )

        repository.transitionToPhase(HabitPhase.OBSERVATION)
        assertEquals(cleanSince, database.habitConfigDao().getHabitConfig()?.cleanStreakStartMillis)
        repository.transitionToPhase(HabitPhase.ACTION)

        assertEquals(cleanSince, database.habitConfigDao().getHabitConfig()?.cleanStreakStartMillis)
    }

    @Test
    fun pauseOverlayPositionIsPersistedInHabitConfiguration() = runBlocking {
        val repository = habitRepository()

        repository.updatePauseOverlayPosition(PauseOverlayPosition.BOTTOM)

        assertEquals(PauseOverlayPosition.BOTTOM, database.habitConfigDao().getHabitConfig()?.pauseOverlayPosition)
    }

    @Test
    fun guidedPlanCommitSavesSettingsStartsActionAndPreservesObservationHistory() = runBlocking {
        val firstObservation = testLog(emotion = "Bored", location = "Bed")
        val secondObservation = testLog(emotion = "Stressed", location = "Desk")
        database.triggerLogDao().insertLog(firstObservation)
        database.triggerLogDao().insertLog(secondObservation)
        val repository = habitRepository()

        repository.commitGuidedPlan(
            plan = "When I feel bored in bed, I will read two pages.",
            techniques = listOf("Box Breathing Exercise", "Cold Water Reset"),
            timerMinutes = 10
        )

        val committed = database.habitConfigDao().getHabitConfig()!!
        assertEquals(HabitPhase.ACTION, committed.phase)
        assertNotNull(committed.actionPhaseStartDateMillis)
        assertEquals(committed.actionPhaseStartDateMillis, committed.cleanStreakStartMillis)
        assertEquals("When I feel bored in bed, I will read two pages.", committed.replacementPlan)
        assertEquals(listOf("Box Breathing Exercise", "Cold Water Reset"), committed.approvedReliefTechniques)
        assertEquals(10, committed.defaultTimerMinutes)
        assertEquals(2, database.triggerLogDao().getRecentLogs(10).size)

        val cleanTime = committed.cleanStreakStartMillis
        repository.commitGuidedPlan("Updated plan", listOf("Physiological Sigh"), 2)
        assertEquals(cleanTime, database.habitConfigDao().getHabitConfig()?.cleanStreakStartMillis)
        assertEquals(2, database.triggerLogDao().getRecentLogs(10).size)
    }

    @Test
    fun failedGuidedPlanPhaseTransitionRollsBackPlanAndSettings() = runBlocking {
        val original = testConfig().copy(
            replacementPlan = "Existing plan",
            approvedReliefTechniques = listOf("Physiological Sigh"),
            defaultTimerMinutes = 5
        )
        database.habitConfigDao().upsertHabitConfig(original)
        database.triggerLogDao().insertLog(testLog())
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_action_phase_transition
            BEFORE UPDATE OF current_phase ON habit_config
            WHEN NEW.current_phase = 'ACTION'
            BEGIN
                SELECT RAISE(ABORT, 'injected phase transition failure');
            END
            """.trimIndent()
        )

        val failure = runCatching {
            habitRepository().commitGuidedPlan("New plan", listOf("Cold Water Reset"), 10)
        }.exceptionOrNull()

        assertNotNull("The injected trigger should reject the phase transition", failure)
        val unchanged = database.habitConfigDao().getHabitConfig()!!
        assertEquals(HabitPhase.OBSERVATION, unchanged.phase)
        assertEquals("Existing plan", unchanged.replacementPlan)
        assertEquals(listOf("Physiological Sigh"), unchanged.approvedReliefTechniques)
        assertEquals(5, unchanged.defaultTimerMinutes)
        assertEquals(1, database.triggerLogDao().getRecentLogs(10).size)
    }

    @Test
    fun resetClearsHistoryAndCustomTagsAndRestoresDefaults() = runBlocking {
        database.habitConfigDao().upsertHabitConfig(
            testConfig(phase = HabitPhase.ACTION, cleanStreakStartMillis = 123L)
        )
        database.contextTagDao().insertTag(tag("Bored", TagCategory.EMOTION, usageCount = 4))
        database.contextTagDao().insertTag(
            tag("Late night", TagCategory.EMOTION, isCustom = true, usageCount = 2)
        )
        database.triggerLogDao().insertLog(testLog())

        habitRepository().resetAllHabitData()

        assertTrue(database.triggerLogDao().getRecentLogs(10).isEmpty())
        assertNull(database.contextTagDao().findTagByName(TagCategory.EMOTION, "Late night"))
        assertEquals(0, database.contextTagDao().findTagByName(TagCategory.EMOTION, "Bored")?.usageCount)

        val restoredConfig = database.habitConfigDao().getHabitConfig()
        assertNotNull(restoredConfig)
        assertEquals("Doomscrolling", restoredConfig!!.habitName)
        assertEquals(HabitPhase.OBSERVATION, restoredConfig.phase)
        assertNull(restoredConfig.cleanStreakStartMillis)
        assertEquals(5, restoredConfig.defaultTimerMinutes)
        assertEquals(66, restoredConfig.targetMasteryDays)
    }

    @Test
    fun archiveMasteredHabitPreservesCompleteSnapshotAndStartsFreshObservation() = runBlocking {
        val mastered = testConfig(habitName = "Late-night scrolling", phase = HabitPhase.MASTERY).copy(
            cleanStreakStartMillis = 123L,
            replacementPlan = "Read two pages",
            approvedReliefTechniques = listOf("Box Breathing Exercise")
        )
        database.habitConfigDao().upsertHabitConfig(mastered)
        database.contextTagDao().insertTag(tag("Bored", TagCategory.EMOTION, usageCount = 3, lastUsedMillis = 77L))
        database.contextTagDao().insertTag(
            tag("After work", TagCategory.LOCATION, isCustom = true, usageCount = 2, lastUsedMillis = 88L)
        )
        database.triggerLogDao().insertLog(
            testLog("Bored", "After work").copy(
                timestampMillis = 99L,
                phaseOrigin = TriggerPhaseOrigin.ACTION,
                outcomeType = TriggerOutcomeType.EMERGENCY_PASSED,
                reliefMethodUsed = "Box Breathing Exercise"
            )
        )

        val archiveId = habitArchiveRepository.archiveMasteredHabitAndStartFresh()
        val archivedEntity = database.archivedHabitDao().getById(archiveId)
        assertNotNull(archivedEntity)
        val archived = habitArchiveRepository.getArchivedHabit(archiveId)
        assertNotNull(archived)
        assertEquals(mastered, archived!!.backup.habitConfig)
        assertEquals(1, archived.backup.triggerLogs.size)
        assertEquals(TriggerOutcomeType.EMERGENCY_PASSED, archived.backup.triggerLogs.single().outcomeType)
        assertEquals("After work", archived.backup.contextTags.single { it.isCustom }.name)
        assertEquals("Late-night scrolling", database.archivedHabitDao().getSummariesFlow().first().single().habitName)

        val freshConfig = database.habitConfigDao().getHabitConfig()!!
        assertEquals(InitialHabitDefaults.habitConfig(freshConfig.createdAtMillis), freshConfig)
        assertTrue(database.triggerLogDao().getRecentLogs(10).isEmpty())
        assertNull(database.contextTagDao().findTagByName(TagCategory.LOCATION, "After work"))
        assertEquals(0, database.contextTagDao().findTagByName(TagCategory.EMOTION, "Bored")?.usageCount)
    }

    @Test
    fun failedArchiveAndResetRollsBackArchiveAndKeepsMasteredData() = runBlocking {
        val mastered = testConfig(habitName = "Keep mastered", phase = HabitPhase.MASTERY)
        database.habitConfigDao().upsertHabitConfig(mastered)
        database.contextTagDao().insertTag(tag("Private", TagCategory.EMOTION, isCustom = true))
        database.triggerLogDao().insertLog(testLog())
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_archive_fresh_config
            BEFORE INSERT ON habit_config
            BEGIN
                SELECT RAISE(ABORT, 'injected fresh config insert failure');
            END
            """.trimIndent()
        )

        val failure = runCatching { habitArchiveRepository.archiveMasteredHabitAndStartFresh() }.exceptionOrNull()

        assertNotNull("The fresh-config insert should roll back the archive transaction", failure)
        assertEquals(mastered, database.habitConfigDao().getHabitConfig())
        assertEquals(1, database.triggerLogDao().getRecentLogs(10).size)
        assertNotNull(database.contextTagDao().findTagByName(TagCategory.EMOTION, "Private"))
        assertTrue(habitArchiveRepository.getArchivedHabits().first().isEmpty())
    }

    @Test
    fun failedResetRollsBackDeletedData() = runBlocking {
        database.habitConfigDao().upsertHabitConfig(testConfig(habitName = "Keep this habit"))
        database.contextTagDao().insertTag(tag("Personal", TagCategory.EMOTION, isCustom = true))
        database.triggerLogDao().insertLog(testLog())
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_habit_config_insert
            BEFORE INSERT ON habit_config
            BEGIN
                SELECT RAISE(ABORT, 'injected config insert failure');
            END
            """.trimIndent()
        )

        val failure = runCatching { habitRepository().resetAllHabitData() }.exceptionOrNull()

        assertNotNull("The injected trigger should reject the reset", failure)
        assertEquals("Keep this habit", database.habitConfigDao().getHabitConfig()?.habitName)
        assertEquals(1, database.triggerLogDao().getRecentLogs(10).size)
        assertNotNull(database.contextTagDao().findTagByName(TagCategory.EMOTION, "Personal"))
    }

    @Test
    fun backupJsonRoundTripRestoresTheEntireHabitAtomically() = runBlocking {
        val originalConfig = testConfig(habitName = "Original habit", phase = HabitPhase.ACTION).copy(
            actionPhaseStartDateMillis = 456L,
            cleanStreakStartMillis = 789L,
            replacementPlan = "When stressed, take a walk.",
            approvedReliefTechniques = listOf("Box Breathing Exercise"),
            pauseOverlayPosition = PauseOverlayPosition.TOP
        )
        database.habitConfigDao().upsertHabitConfig(originalConfig)
        database.contextTagDao().insertTag(tag("Bored", TagCategory.EMOTION, usageCount = 3, lastUsedMillis = 42L))
        database.contextTagDao().insertTag(
            tag("After work", TagCategory.LOCATION, isCustom = true, usageCount = 2, lastUsedMillis = 43L)
        )
        database.triggerLogDao().insertLog(
            testLog("Bored", "After work").copy(
                timestampMillis = 44L,
                phaseOrigin = TriggerPhaseOrigin.ACTION,
                intensity = org.exhabere.data.db.model.UrgeIntensity.STRONG,
                outcomeType = TriggerOutcomeType.ENGAGED_IN_HABIT,
                notes = "Private note"
            )
        )

        val exported = habitBackupRepository.createBackup(exportedAtMillis = 1_000L)
        val encodedExport = HabitBackupJsonCodec.encode(exported)
        val importedFile = HabitBackupJsonCodec.decode(encodedExport)
        assertEquals(exported, importedFile)
        val legacyExport = encodedExport.replace(
            HabitBackupJsonCodec.FORMAT_NAME,
            "org.openhabit.habit-backup"
        )
        assertEquals(exported, HabitBackupJsonCodec.decode(legacyExport))

        database.habitConfigDao().upsertHabitConfig(testConfig(habitName = "Replace me"))
        database.triggerLogDao().deleteAllLogs()
        database.contextTagDao().deleteAll()
        database.contextTagDao().insertTag(tag("Other", TagCategory.EMOTION, isCustom = true))

        habitBackupRepository.restoreBackup(importedFile)

        assertEquals(originalConfig, database.habitConfigDao().getHabitConfig())
        assertEquals(exported.triggerLogs, database.triggerLogDao().getAllLogs())
        assertEquals(exported.contextTags, database.contextTagDao().getAllTags())
    }

    @Test
    fun failedBackupRestoreRollsBackAllReplacedData() = runBlocking {
        val currentConfig = testConfig(habitName = "Keep current habit")
        database.habitConfigDao().upsertHabitConfig(currentConfig)
        database.contextTagDao().insertTag(tag("Keep tag", TagCategory.EMOTION, isCustom = true))
        database.triggerLogDao().insertLog(testLog())
        val currentBackup = habitBackupRepository.createBackup(exportedAtMillis = 1_000L)
        val replacement = currentBackup.copy(
            habitConfig = currentConfig.copy(habitName = "Replacement habit"),
            triggerLogs = currentBackup.triggerLogs.map { it.copy(id = it.id + 100L) }
        )
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_backup_restore
            BEFORE INSERT ON trigger_logs
            BEGIN
                SELECT RAISE(ABORT, 'injected backup restore failure');
            END
            """.trimIndent()
        )

        val failure = runCatching { habitBackupRepository.restoreBackup(replacement) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(currentConfig, database.habitConfigDao().getHabitConfig())
        assertEquals(currentBackup.triggerLogs, database.triggerLogDao().getAllLogs())
        assertEquals(currentBackup.contextTags, database.contextTagDao().getAllTags())
    }

    @Test
    fun backupReaderRejectsUnrecognizedFormatsBeforeRestore() {
        val failure = runCatching {
            HabitBackupJsonCodec.decode("""{"format":"not-an-exhabere-backup","formatVersion":1}""")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private fun habitRepository() = HabitRepositoryImpl(
        habitConfigDao = database.habitConfigDao(),
        triggerLogDao = database.triggerLogDao(),
        contextTagDao = database.contextTagDao(),
        database = database
    )

    private fun tag(
        name: String,
        category: TagCategory,
        isCustom: Boolean = false,
        usageCount: Int = 0,
        lastUsedMillis: Long = 0L
    ) = ContextTagEntity(
        name = name,
        category = category,
        isCustom = isCustom,
        usageCount = usageCount,
        lastUsedMillis = lastUsedMillis
    )

    private fun testConfig(
        habitName: String = "Test habit",
        phase: HabitPhase = HabitPhase.OBSERVATION,
        cleanStreakStartMillis: Long? = null
    ) = HabitConfigEntity(
        habitName = habitName,
        phase = phase,
        cleanStreakStartMillis = cleanStreakStartMillis
    )

    private fun testLog(
        emotion: String = "Calm",
        location: String = "Home"
    ) = TriggerLogEntity(
        phaseOrigin = TriggerPhaseOrigin.OBSERVATION,
        emotionalState = emotion,
        location = location
    )
}
