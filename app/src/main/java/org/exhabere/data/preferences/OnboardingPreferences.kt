package org.exhabere.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/** Stores first-run welcome completion on-device, independently of the current habit data. */
class OnboardingPreferences(context: Context) {
    private val dataStore = context.applicationContext.onboardingDataStore

    val welcomeComplete: Flow<Boolean?> = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { preferences -> preferences[WELCOME_COMPLETE] ?: false }

    suspend fun markWelcomeComplete() {
        dataStore.edit { preferences -> preferences[WELCOME_COMPLETE] = true }
    }

    private companion object {
        val WELCOME_COMPLETE = booleanPreferencesKey("welcome_complete")
    }
}
