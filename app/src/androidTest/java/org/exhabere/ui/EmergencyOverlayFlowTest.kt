package org.exhabere.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.exhabere.ExHabereApplication
import org.exhabere.MainActivity
import org.exhabere.R
import org.exhabere.data.db.InitialHabitDefaults
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.di.AppContainer
import org.exhabere.service.LockoutOverlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real service overlay, its outcome action, and the follow-up form on the app Activity. */
@RunWith(AndroidJUnit4::class)
class EmergencyOverlayFlowTest {

    @Test
    fun choosingEmergencyOutcomeClosesOverlayAndOpensDetailsForThatSameLog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as ExHabereApplication
        val container = application.container
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val overlayPermissionWasGranted = Settings.canDrawOverlays(context)
        var scenario: ActivityScenario<MainActivity>? = null
        var originalConfig: HabitConfigEntity? = null
        var originalTags: List<ContextTagEntity> = emptyList()
        var eventId: Long? = null
        var startedAtMillis = System.currentTimeMillis()

        try {
            grantOverlayPermission(device, context.packageName)
            originalConfig = awaitInitialConfig(container)
            originalTags = container.contextTagDao.getAllTags()
            container.contextTagDao.insertTags(InitialHabitDefaults.tags())
            val actionStartMillis = System.currentTimeMillis()
            container.habitRepository.saveHabitConfig(
                HabitConfigEntity(
                    habitName = "Emergency overlay test",
                    phase = HabitPhase.ACTION,
                    actionPhaseStartDateMillis = actionStartMillis,
                    cleanStreakStartMillis = actionStartMillis,
                    replacementPlan = "Use a short pause",
                    approvedReliefTechniques = listOf("Box Breathing Exercise")
                )
            )
            container.onboardingPreferences.markWelcomeComplete()
            startedAtMillis = System.currentTimeMillis()

            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(
                "The Action screen did not appear",
                device.wait(Until.hasObject(By.text(context.getString(R.string.log_an_urge_short))), UI_TIMEOUT_MILLIS)
            )

            scenario.onActivity { activity ->
                LockoutOverlayService.start(
                    activity,
                    method = "Test pause",
                    endAtMillis = System.currentTimeMillis() - 1_000L
                )
            }

            val passedLabel = context.getString(R.string.urge_passed)
            assertTrue(
                "The completed emergency overlay did not appear",
                device.wait(Until.hasObject(By.text(passedLabel)), UI_TIMEOUT_MILLIS)
            )
            device.findObject(By.text(passedLabel)).click()

            val followUpTitle = context.getString(R.string.add_emergency_urge_details)
            assertTrue(
                "The app did not open the follow-up details form after the overlay closed",
                device.wait(Until.hasObject(By.text(followUpTitle)), UI_TIMEOUT_MILLIS)
            )
            assertTrue("The emergency overlay should be closed", !device.hasObject(By.text(passedLabel)))
            val emotionTag = device.wait(Until.findObject(By.text("Bored")), UI_TIMEOUT_MILLIS)
            assertTrue("The feeling tag was not available", emotionTag != null)
            emotionTag!!.click()
            swipeUp(device)

            val locationTag = device.wait(Until.findObject(By.text("Bed")), UI_TIMEOUT_MILLIS)
            assertTrue("The location tag was not available", locationTag != null)
            locationTag!!.click()

            swipeUp(device)
            val saveLabel = context.getString(R.string.save_log)
            assertTrue(
                "The follow-up save button was not available",
                device.wait(Until.hasObject(By.text(saveLabel)), UI_TIMEOUT_MILLIS)
            )
            device.findObject(By.text(saveLabel)).click()

            val saved = awaitEmergencyLog(container, startedAtMillis)
            eventId = saved.id
            assertEquals(TriggerPhaseOrigin.ACTION, saved.phaseOrigin)
            assertEquals(TriggerOutcomeType.EMERGENCY_PASSED, saved.outcomeType)
            assertEquals("Bored", saved.emotionalState)
            assertEquals("Bed", saved.location)
            assertEquals(UrgeIntensity.MODERATE, saved.intensity)
            assertEquals(
                "The follow-up must enrich the existing event, not create a second urge",
                1,
                container.triggerLogDao.getRecentLogs(50).count { it.timestampMillis >= startedAtMillis }
            )
        } finally {
            context.stopService(Intent(context, LockoutOverlayService::class.java))
            runCatching {
                scenario?.onActivity { it.finishAndRemoveTask() }
                scenario?.close()
            }
            try {
                val testLogs = container.triggerLogDao.getRecentLogs(100)
                    .filter { it.timestampMillis >= startedAtMillis || it.id == eventId }
                for (log in testLogs) container.triggerLogDao.deleteLog(log)
                container.contextTagDao.deleteAll()
                container.contextTagDao.insertTags(originalTags)
                val configToRestore = originalConfig
                if (configToRestore != null) container.habitConfigDao.upsertHabitConfig(configToRestore)
            } catch (_: Exception) {
                // Preserve the test failure if cleanup cannot restore its temporary app state.
            }
            restoreOverlayPermission(device, context.packageName, overlayPermissionWasGranted)
        }
    }

    private suspend fun awaitInitialConfig(container: AppContainer): HabitConfigEntity {
        container.habitConfigDao.getHabitConfig()?.let { return it }
        repeat(100) {
            delay(100)
            container.habitConfigDao.getHabitConfig()?.let { return it }
        }
        error("The app's initial habit configuration was not created")
    }

    private suspend fun awaitEmergencyLog(
        container: AppContainer,
        sinceMillis: Long
    ): TriggerLogEntity {
        repeat(100) {
            container.triggerLogDao.getRecentLogs(100)
                .firstOrNull { it.timestampMillis >= sinceMillis }
                ?.let { return it }
            delay(100)
        }
        error("The emergency outcome was not saved")
    }

    private fun grantOverlayPermission(device: UiDevice, packageName: String) {
        device.executeShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
    }

    private fun restoreOverlayPermission(device: UiDevice, packageName: String, wasGranted: Boolean) {
        val mode = if (wasGranted) "allow" else "default"
        device.executeShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW $mode")
    }

    private fun swipeUp(device: UiDevice) {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 4 / 5,
            device.displayWidth / 2,
            device.displayHeight / 3,
            12
        )
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 15_000L
    }
}
