package org.exhabere.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.exhabere.data.db.AppDatabase
import org.exhabere.data.db.dao.ContextTagDao
import org.exhabere.data.db.dao.ArchivedHabitDao
import org.exhabere.data.db.dao.HabitConfigDao
import org.exhabere.data.db.dao.TriggerLogDao
import org.exhabere.data.preferences.OnboardingPreferences
import org.exhabere.data.repository.HabitRepository
import org.exhabere.data.repository.HabitRepositoryImpl
import org.exhabere.data.repository.HabitBackupRepository
import org.exhabere.data.repository.HabitBackupRepositoryImpl
import org.exhabere.data.repository.HabitArchiveRepository
import org.exhabere.data.repository.HabitArchiveRepositoryImpl
import org.exhabere.data.repository.TriggerLogRepository
import org.exhabere.data.repository.TriggerLogRepositoryImpl

/**
 * Pure Kotlin manual Dependency Injection container (`AppContainer`).
 * Follows the singleton container pattern without heavy annotation processors or reflection (Hilt/Dagger).
 * Guarantees 100% offline, local-only architecture with lazy initialization of dependencies.
 */
interface AppContainer {
    val database: AppDatabase
    val habitConfigDao: HabitConfigDao
    val triggerLogDao: TriggerLogDao
    val contextTagDao: ContextTagDao
    val archivedHabitDao: ArchivedHabitDao
    val habitRepository: HabitRepository
    val habitBackupRepository: HabitBackupRepository
    val habitArchiveRepository: HabitArchiveRepository
    val triggerLogRepository: TriggerLogRepository
    val onboardingPreferences: OnboardingPreferences
}

/**
 * Default implementation of [AppContainer] bound to application Context lifecycle.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val onboardingPreferences: OnboardingPreferences by lazy {
        OnboardingPreferences(context)
    }

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context, applicationScope)
    }

    override val habitConfigDao: HabitConfigDao by lazy {
        database.habitConfigDao()
    }

    override val triggerLogDao: TriggerLogDao by lazy {
        database.triggerLogDao()
    }

    override val contextTagDao: ContextTagDao by lazy {
        database.contextTagDao()
    }

    override val archivedHabitDao: ArchivedHabitDao by lazy {
        database.archivedHabitDao()
    }

    override val habitRepository: HabitRepository by lazy {
        HabitRepositoryImpl(
            habitConfigDao = habitConfigDao,
            triggerLogDao = triggerLogDao,
            contextTagDao = contextTagDao,
            database = database
        )
    }

    override val habitBackupRepository: HabitBackupRepository by lazy {
        HabitBackupRepositoryImpl(
            database = database,
            habitConfigDao = habitConfigDao,
            triggerLogDao = triggerLogDao,
            contextTagDao = contextTagDao
        )
    }

    override val habitArchiveRepository: HabitArchiveRepository by lazy {
        HabitArchiveRepositoryImpl(
            database = database,
            archivedHabitDao = archivedHabitDao,
            habitConfigDao = habitConfigDao,
            triggerLogDao = triggerLogDao,
            contextTagDao = contextTagDao
        )
    }

    override val triggerLogRepository: TriggerLogRepository by lazy {
        TriggerLogRepositoryImpl(
            triggerLogDao = triggerLogDao,
            contextTagDao = contextTagDao,
            habitConfigDao = habitConfigDao,
            database = database
        )
    }
}
