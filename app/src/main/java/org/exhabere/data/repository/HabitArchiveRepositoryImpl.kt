package org.exhabere.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.exhabere.data.backup.HabitBackup
import org.exhabere.data.backup.HabitBackupJsonCodec
import org.exhabere.data.backup.HabitBackupValidator
import org.exhabere.data.db.AppDatabase
import org.exhabere.data.db.InitialHabitDefaults
import org.exhabere.data.db.dao.ArchivedHabitDao
import org.exhabere.data.db.dao.ContextTagDao
import org.exhabere.data.db.dao.HabitConfigDao
import org.exhabere.data.db.dao.TriggerLogDao
import org.exhabere.data.db.entity.ArchivedHabitEntity
import org.exhabere.data.db.entity.ArchivedHabitSummary
import org.exhabere.data.db.model.HabitPhase

class HabitArchiveRepositoryImpl(
    private val database: AppDatabase,
    private val archivedHabitDao: ArchivedHabitDao,
    private val habitConfigDao: HabitConfigDao,
    private val triggerLogDao: TriggerLogDao,
    private val contextTagDao: ContextTagDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HabitArchiveRepository {

    override fun getArchivedHabits(): Flow<List<ArchivedHabitSummary>> =
        archivedHabitDao.getSummariesFlow()

    override suspend fun archiveMasteredHabitAndStartFresh(): Long = withContext(ioDispatcher) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val config = requireNotNull(habitConfigDao.getHabitConfig()) {
                "There is no mastered habit to archive."
            }
            require(config.phase == HabitPhase.MASTERY) {
                "Only a mastered habit can be archived."
            }
            val backup = HabitBackup(
                formatVersion = HabitBackupValidator.CURRENT_FORMAT_VERSION,
                exportedAtMillis = now,
                habitConfig = config,
                triggerLogs = triggerLogDao.getAllLogs(),
                contextTags = contextTagDao.getAllTags()
            )
            val archiveId = archivedHabitDao.insert(
                ArchivedHabitEntity(
                    habitName = config.habitName,
                    archivedAtMillis = now,
                    backupJson = HabitBackupJsonCodec.encode(backup)
                )
            )

            triggerLogDao.deleteAllLogs()
            contextTagDao.clearCustomTags()
            contextTagDao.resetDefaultTagUsage()
            habitConfigDao.clearConfig()
            habitConfigDao.upsertHabitConfig(InitialHabitDefaults.habitConfig(now))
            archiveId
        }
    }

    override suspend fun getArchivedHabit(archiveId: Long): ArchivedHabitRecord? = withContext(ioDispatcher) {
        archivedHabitDao.getById(archiveId)?.let { entity ->
            ArchivedHabitRecord(
                id = entity.id,
                archivedAtMillis = entity.archivedAtMillis,
                backup = HabitBackupJsonCodec.decode(entity.backupJson)
            )
        }
    }
}
