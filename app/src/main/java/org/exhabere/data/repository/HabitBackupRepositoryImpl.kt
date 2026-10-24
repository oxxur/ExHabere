package org.exhabere.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.exhabere.data.backup.HabitBackup
import org.exhabere.data.backup.HabitBackupValidator
import org.exhabere.data.db.AppDatabase
import org.exhabere.data.db.dao.ContextTagDao
import org.exhabere.data.db.dao.HabitConfigDao
import org.exhabere.data.db.dao.TriggerLogDao

class HabitBackupRepositoryImpl(
    private val database: AppDatabase,
    private val habitConfigDao: HabitConfigDao,
    private val triggerLogDao: TriggerLogDao,
    private val contextTagDao: ContextTagDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HabitBackupRepository {

    override suspend fun createBackup(exportedAtMillis: Long): HabitBackup = withContext(ioDispatcher) {
        database.withTransaction {
            val config = requireNotNull(habitConfigDao.getHabitConfig()) {
                "There is no habit to export yet."
            }
            HabitBackup(
                formatVersion = HabitBackupValidator.CURRENT_FORMAT_VERSION,
                exportedAtMillis = exportedAtMillis,
                habitConfig = config,
                triggerLogs = triggerLogDao.getAllLogs(),
                contextTags = contextTagDao.getAllTags()
            ).also(HabitBackupValidator::validate)
        }
    }

    override suspend fun restoreBackup(backup: HabitBackup): Unit = withContext(ioDispatcher) {
        HabitBackupValidator.validate(backup)
        database.withTransaction {
            triggerLogDao.deleteAllLogs()
            contextTagDao.deleteAll()
            habitConfigDao.clearConfig()
            habitConfigDao.upsertHabitConfig(backup.habitConfig)
            contextTagDao.insertTags(backup.contextTags)
            triggerLogDao.insertLogs(backup.triggerLogs)
        }
    }
}
