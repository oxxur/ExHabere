package org.exhabere.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.db.model.TagCategory
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.data.backup.HabitBackup
import org.exhabere.data.db.entity.TriggerLogEntity
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.repository.ArchivedHabitRecord
import org.exhabere.ui.action.ActionScreen
import org.exhabere.ui.action.EmergencyDecisionScreen
import org.exhabere.ui.action.MasteryScreen
import org.exhabere.ui.components.QuickLogSheet
import org.exhabere.ui.insights.InsightsScreen
import org.exhabere.ui.observation.ObservationScreen
import org.exhabere.ui.setup.PlanSetupScreen
import org.exhabere.ui.theme.ExHabereTheme
import org.exhabere.ui.welcome.WelcomeScreen
import org.exhabere.viewmodel.ActionUiState
import org.exhabere.viewmodel.EmergencyOption
import org.exhabere.ui.action.MasteryStats
import org.exhabere.ui.settings.SettingsScreen
import org.exhabere.viewmodel.ObservationUiState
import org.exhabere.viewmodel.PlanSetupUiState
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ComposeScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeFlowRequiresHabitNameAndCompletesWithTheEnteredName() {
        var completedName: String? = null
        compose.setContent {
            ExHabereTheme {
                WelcomeScreen(
                    initialHabitName = "",
                    isCompleting = false,
                    onComplete = { completedName = it }
                )
            }
        }

        compose.onNodeWithText("STEP 1 OF 3").assertExists()
        compose.onNodeWithText("Get Started").performClick()
        compose.onNodeWithText("STEP 2 OF 3").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput("Late-night scrolling")
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        compose.onNodeWithText("STEP 3 OF 3").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("STEP 2 OF 3").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("STEP 3 OF 3").assertExists()
        compose.onNodeWithText("Begin observing").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("Late-night scrolling", completedName)
    }

    @Test
    fun newHabitFlowStartsOnWelcomeStepTwo() {
        var completedName: String? = null
        compose.setContent {
            ExHabereTheme {
                WelcomeScreen(
                    initialHabitName = "",
                    startAtHabitName = true,
                    isCompleting = false,
                    onComplete = { completedName = it }
                )
            }
        }

        compose.onNodeWithText("STEP 2 OF 3").assertExists()
        compose.onNodeWithText("Name this habit").performTextInput("Late-night snacks")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("STEP 3 OF 3").assertExists()
        compose.onNodeWithText("Begin observing").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("Late-night snacks", completedName)
    }

    @Test
    fun observationQuickLogSavesSelectedTagsIntensityAndOutcome() {
        var saved: QuickLogResult? = null
        compose.setContent {
            ExHabereTheme {
                ObservationScreen(
                    state = observationState(),
                    onOpenSettings = {},
                    onOpenInsights = {},
                    onOpenPlanSetup = {},
                    onSaveLog = { emotion, location, intensity, outcome, customEmotion, customLocation ->
                        saved = QuickLogResult(
                            emotion, location, intensity, outcome, customEmotion, customLocation
                        )
                    }
                )
            }
        }

        compose.onNodeWithText("Log an urge").performClick()
        compose.onNodeWithText("Bored").performClick()
        compose.onNodeWithText("Bed").performClick()
        compose.onNodeWithText("Strong").performClick()
        compose.onNodeWithText("Engaged in habit").performClick()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(
            QuickLogResult(
                emotion = "Bored",
                location = "Bed",
                intensity = UrgeIntensity.STRONG,
                outcome = TriggerOutcomeType.ENGAGED_IN_HABIT,
                customEmotion = false,
                customLocation = false
            ),
            saved
        )
    }

    @Test
    fun actionQuickLogUsesTheSharedSheetAndSavesAnActionQuickLog() {
        var saved: QuickLogResult? = null
        compose.setContent {
            ExHabereTheme {
                ActionScreen(
                    state = ActionUiState(
                        config = HabitConfigEntity(
                            habitName = "Test habit",
                            phase = HabitPhase.ACTION,
                            cleanStreakStartMillis = 1_000L
                        ),
                        emotions = listOf(tag("Bored", TagCategory.EMOTION)),
                        locations = listOf(tag("Desk", TagCategory.LOCATION))
                    ),
                    onOpenSettings = {},
                    onOpenInsights = {},
                    onEmergency = {},
                    onLogUrge = { emotion, location, intensity, outcome, customEmotion, customLocation ->
                        saved = QuickLogResult(
                            emotion, location, intensity, outcome, customEmotion, customLocation
                        )
                    },
                    onShowMastery = {}
                )
            }
        }

        compose.onNodeWithText("+ Log an urge").performScrollTo().performClick()
        compose.onNodeWithText("Bored").performClick()
        compose.onNodeWithText("Desk").performClick()
        compose.onNodeWithText("Urge only").performClick()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(
            QuickLogResult(
                emotion = "Bored",
                location = "Desk",
                intensity = UrgeIntensity.MODERATE,
                outcome = TriggerOutcomeType.URGE_ONLY,
                customEmotion = false,
                customLocation = false
            ),
            saved
        )
    }

    @Test
    fun emergencyOutcomeCanBeEnrichedAfterTheOverlayClosesWithoutCreatingAnotherLog() {
        var updatedLogId: Long? = null
        var savedDetails: QuickLogResult? = null
        var handled = false
        compose.setContent {
            ExHabereTheme {
                ActionScreen(
                    state = ActionUiState(
                        config = HabitConfigEntity(habitName = "Test habit", phase = HabitPhase.ACTION),
                        emotions = listOf(tag("Anxious", TagCategory.EMOTION)),
                        locations = listOf(tag("Desk", TagCategory.LOCATION))
                    ),
                    onOpenSettings = {},
                    onOpenInsights = {},
                    onEmergency = {},
                    onLogUrge = { _, _, _, _, _, _ -> error("Must update the existing emergency log") },
                    pendingEmergencyLogId = 42L,
                    onUpdateEmergencyLog = { id, emotion, location, intensity, customEmotion, customLocation ->
                        updatedLogId = id
                        savedDetails = QuickLogResult(
                            emotion, location, intensity, TriggerOutcomeType.EMERGENCY_PASSED,
                            customEmotion, customLocation
                        )
                    },
                    onEmergencyLogDetailsHandled = { handled = true },
                    onShowMastery = {}
                )
            }
        }

        compose.onNodeWithText("Add details to this urge").assertExists()
        compose.onNodeWithText("What happened?").assertDoesNotExist()
        compose.onNodeWithText("Anxious").performClick()
        compose.onNodeWithText("Desk").performClick()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(42L, updatedLogId)
        assertEquals(
            QuickLogResult(
                "Anxious", "Desk", UrgeIntensity.MODERATE, TriggerOutcomeType.EMERGENCY_PASSED,
                false, false
            ),
            savedDetails
        )
        assertEquals(true, handled)
    }

    @Test
    fun actionMilestoneIconsShowTheirExplanatoryTooltips() {
        compose.setContent {
            ExHabereTheme {
                ActionScreen(
                    state = ActionUiState(
                        config = HabitConfigEntity(
                            habitName = "Test habit",
                            phase = HabitPhase.ACTION,
                            cleanStreakStartMillis = System.currentTimeMillis() - 22 * DAY_MILLIS
                        )
                    ),
                    onOpenSettings = {},
                    onOpenInsights = {},
                    onEmergency = {},
                    onLogUrge = { _, _, _, _, _, _ -> },
                    onShowMastery = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Day 7: Circuit Breaker, completed")
            .performScrollTo().performClick()
        compose.onNodeWithText("A first-week checkpoint recognizing your continued practice.")
            .assertExists()

        compose.onNodeWithContentDescription("Day 21: Structural Anchor, completed")
            .performScrollTo().performClick()
        compose.onNodeWithText("A three-week checkpoint recognizing continued practice; progress varies from person to person.")
            .assertExists()

        compose.onNodeWithContentDescription("Day 66: Mastery target, upcoming")
            .performScrollTo().performClick()
        compose.onNodeWithText("Your configured mastery target. Sixty-six days is a research reference, not a deadline.")
            .assertExists()
        val masteryIconNode = compose.onNodeWithContentDescription("Day 66: Mastery target, upcoming")
            .fetchSemanticsNode()
        val masteryTooltipNode = compose.onNodeWithText(
            "Your configured mastery target. Sixty-six days is a research reference, not a deadline."
        ).fetchSemanticsNode()
        val masteryIconCenterX = masteryIconNode.positionOnScreen.x + masteryIconNode.size.width / 2f
        val masteryTooltipCenterX = masteryTooltipNode.positionOnScreen.x + masteryTooltipNode.size.width / 2f
        val horizontalDistance = abs(masteryIconCenterX - masteryTooltipCenterX)
        assertTrue(
            "The tooltip should appear close to the tapped milestone icon; " +
                "icon center=$masteryIconCenterX, popup center=$masteryTooltipCenterX",
            horizontalDistance < with(compose.density) { 140.dp.toPx() }
        )
    }

    @Test
    fun quickLogPassesCustomTagsAndTheirCustomFlagsToTheSaveCallback() {
        var saved: QuickLogResult? = null
        compose.setContent {
            ExHabereTheme {
                QuickLogSheet(
                    emotions = listOf("Bored"),
                    locations = listOf("Bed"),
                    onDismiss = {},
                    onSave = { emotion, location, intensity, outcome, customEmotion, customLocation ->
                        saved = QuickLogResult(
                            emotion, location, intensity, outcome, customEmotion, customLocation
                        )
                    }
                )
            }
        }

        compose.onNodeWithText("Other feeling (optional)").performTextInput("Restless")
        compose.onNodeWithText("Other context (optional)").performTextInput("On commute")
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(
            QuickLogResult(
                emotion = "Restless",
                location = "On commute",
                intensity = UrgeIntensity.MODERATE,
                outcome = TriggerOutcomeType.URGE_ONLY,
                customEmotion = true,
                customLocation = true
            ),
            saved
        )
    }

    @Test
    fun quickLogCancelDismissesWithoutSaving() {
        var dismissed = false
        var saved = false
        compose.setContent {
            ExHabereTheme {
                QuickLogSheet(
                    emotions = listOf("Bored"),
                    locations = listOf("Bed"),
                    onDismiss = { dismissed = true },
                    onSave = { _, _, _, _, _, _ -> saved = true }
                )
            }
        }

        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(true, dismissed)
        assertEquals(false, saved)
    }

    @Test
    fun planSetupEnforcesRequiredFieldsAndCommitsTheSelectedDraft() {
        var committed = false
        compose.setContent {
            val draft = remember {
                mutableStateOf(
                    PlanSetupUiState(
                        config = HabitConfigEntity(habitName = "Test habit"),
                        isLoading = false,
                        replacementPlan = "",
                        selectedReliefTechniques = emptyList()
                    )
                )
            }
            ExHabereTheme {
                PlanSetupScreen(
                    state = draft.value,
                    onBack = {},
                    onPlanChange = { draft.value = draft.value.copy(replacementPlan = it) },
                    onChooseStarter = { draft.value = draft.value.copy(replacementPlan = it) },
                    onToggleRelief = { technique ->
                        val selected = draft.value.selectedReliefTechniques
                        draft.value = draft.value.copy(
                            selectedReliefTechniques = if (technique in selected) {
                                selected - technique
                            } else {
                                selected + technique
                            }
                        )
                    },
                    onSelectTimer = { draft.value = draft.value.copy(timerMinutes = it) },
                    onCommit = { committed = true }
                )
            }
        }

        val commitButton = compose.onNodeWithText("Commit & begin action phase")
        commitButton.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("My if-then plan").performScrollTo().performTextInput("When stressed, I will take a walk.")
        compose.onNodeWithText("Commit & begin action phase").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Box Breathing Exercise").performScrollTo().performClick()
        compose.onNodeWithText("10 min").performScrollTo().performClick()
        compose.onNodeWithText("Commit & begin action phase").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(true, committed)
    }

    @Test
    fun insightsExpandsOneSummaryAtATime() {
        compose.setContent {
            ExHabereTheme {
                InsightsScreen(
                    state = ActionUiState(
                        config = HabitConfigEntity(
                            habitName = "Test habit",
                            phase = HabitPhase.ACTION,
                            actionPhaseStartDateMillis = System.currentTimeMillis() - 2 * DAY_MILLIS
                        )
                    ),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Recovery trend").performClick()
        compose.onNodeWithText("Two aligned tracks: logged urge count above, average reported intensity below. Select a week for its values.")
            .assertExists()
        compose.onNodeWithText("Peak trigger windows").performScrollTo().performClick()
        compose.onNodeWithText("Observation baseline · 0 urges").performScrollTo().assertExists()
        compose.onAllNodesWithText(
            "Two aligned tracks: logged urge count above, average reported intensity below. Select a week for its values."
        ).assertCountEquals(0)
    }

    @Test
    fun insightsOutcomeFilterSelectionIsExclusive() {
        compose.setContent {
            ExHabereTheme {
                InsightsScreen(state = ActionUiState(), onBack = {})
            }
        }

        compose.onNodeWithText("All Urges").assertIsSelected()
        compose.onNodeWithText("Acted-On Only").performClick().assertIsSelected()
        compose.onNodeWithText("All Urges").assertIsNotSelected()
        compose.onNodeWithText("Logged Only").performClick().assertIsSelected()
        compose.onNodeWithText("Acted-On Only").assertIsNotSelected()
    }

    @Test
    fun emergencyDecisionPassesSelectedAlternativeAndDurationToCommit() {
        var committedOption: EmergencyOption? = null
        var committedDuration: Int? = null
        var committed = false
        compose.setContent {
            ExHabereTheme {
                EmergencyDecisionScreen(
                    state = ActionUiState(
                        config = HabitConfigEntity(
                            habitName = "Test habit",
                            phase = HabitPhase.ACTION,
                            replacementPlan = "Take a short walk."
                        )
                    ),
                    onBack = {},
                    onSelectOption = { committedOption = it },
                    onSelectDuration = { committedDuration = it },
                    onCommit = { committed = true }
                )
            }
        }

        compose.onNodeWithText("Alternative action").performClick()
        compose.onNodeWithText("10 min").performClick()

        assertEquals(EmergencyOption.ALTERNATIVE, committedOption)
        assertEquals(10, committedDuration)
        compose.onNodeWithText("Commit and start pause").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(true, committed)
    }

    @Test
    fun masteryCelebrationShowsAvailableStatsAndBothExplicitChoices() {
        var archiveAsMastered = false
        var continueTracking = false
        compose.setContent {
            ExHabereTheme {
                MasteryScreen(
                    targetDays = 66,
                    stats = MasteryStats(
                        urgesResisted = 42,
                        topReliefMethod = "Box Breathing Exercise",
                        topReliefMethodPercent = 65,
                        reliefMethodSessionCount = 20,
                        alternativePlanSelectionCount = 4
                    ),
                    replacementPlan = "Read two pages",
                    onArchiveAndStartNewHabit = { archiveAsMastered = true },
                    onContinueTracking = { continueTracking = true }
                )
            }
        }

        compose.onNodeWithText("66 DAYS CLEAN").assertExists()
        compose.onNodeWithText("Total urges resisted").performScrollTo().assertExists()
        compose.onNodeWithText("42").assertExists()
        compose.onNodeWithText("Box Breathing Exercise · 65% of 20 logged relief pauses")
            .performScrollTo().assertExists()
        compose.onNodeWithText("4 recorded pauses").performScrollTo().assertExists()
        compose.onNodeWithText(
            "The app records which response you selected, but not how much of an activity you completed."
        ).performScrollTo().assertExists()

        compose.onNodeWithText("Archive mastered habit & start a new one").performScrollTo().performClick()
        compose.onNodeWithText("Maintain active mastery tracker").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(true, archiveAsMastered)
        assertEquals(true, continueTracking)
    }

    @Test
    fun archivedHabitShowsMasteryStatsOnlyAfterItIsOpenedInSettings() {
        val archivedHabit = ArchivedHabitRecord(
            id = 4L,
            archivedAtMillis = System.currentTimeMillis(),
            backup = HabitBackup(
                formatVersion = 1,
                exportedAtMillis = System.currentTimeMillis(),
                habitConfig = HabitConfigEntity(
                    habitName = "Past habit",
                    phase = HabitPhase.MASTERY,
                    approvedReliefTechniques = listOf("Box Breathing Exercise")
                ),
                triggerLogs = listOf(
                    TriggerLogEntity(
                        id = 1L,
                        phaseOrigin = TriggerPhaseOrigin.ACTION,
                        emotionalState = "Emergency Craving",
                        location = "On Device",
                        outcomeType = TriggerOutcomeType.EMERGENCY_PASSED,
                        reliefMethodUsed = "Box Breathing Exercise"
                    )
                ),
                contextTags = emptyList()
            )
        )
        compose.setContent {
            val selected = remember { mutableStateOf<ArchivedHabitRecord?>(null) }
            ExHabereTheme {
                SettingsScreen(
                    config = HabitConfigEntity(habitName = "Current habit"),
                    customTags = emptyList(),
                    archivedHabits = listOf(
                        org.exhabere.data.db.entity.ArchivedHabitSummary(
                            id = archivedHabit.id,
                            habitName = "Past habit",
                            archivedAtMillis = archivedHabit.archivedAtMillis
                        )
                    ),
                    selectedArchivedHabit = selected.value,
                    onBack = {},
                    onSaveHabitName = {},
                    onSaveReplacementPlan = {},
                    onUpdateReliefTechniques = {},
                    onUpdateTimer = {},
                    onUpdatePauseOverlayPosition = {},
                    onUpdateObservationDays = {},
                    onUpdateMasteryDays = {},
                    onDeleteCustomTag = {},
                    onExportData = {},
                    onImportData = {},
                    onRevisitGuidedPlanSetup = {},
                    onReturnToObservation = {},
                    onReset = {},
                    onViewArchivedHabit = { selected.value = archivedHabit },
                    onCloseArchivedHabit = { selected.value = null }
                )
            }
        }

        compose.onAllNodesWithText("Total urges resisted").assertCountEquals(0)
        compose.onNodeWithText("Past habit", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Total urges resisted").performScrollTo().assertExists()
        compose.onNodeWithText("Box Breathing Exercise · 100% of 1 logged relief pauses")
            .performScrollTo().assertExists()
    }

    @Test
    fun settingsExposeAndSaveEveryPauseOverlayPosition() {
        var selectedPosition: PauseOverlayPosition? = null
        compose.setContent {
            ExHabereTheme {
                SettingsScreen(
                    config = HabitConfigEntity(habitName = "Current habit"),
                    customTags = emptyList(),
                    archivedHabits = emptyList(),
                    selectedArchivedHabit = null,
                    onBack = {},
                    onSaveHabitName = {},
                    onSaveReplacementPlan = {},
                    onUpdateReliefTechniques = {},
                    onUpdateTimer = {},
                    onUpdatePauseOverlayPosition = { selectedPosition = it },
                    onUpdateObservationDays = {},
                    onUpdateMasteryDays = {},
                    onDeleteCustomTag = {},
                    onExportData = {},
                    onImportData = {},
                    onRevisitGuidedPlanSetup = {},
                    onReturnToObservation = {},
                    onReset = {},
                    onViewArchivedHabit = {},
                    onCloseArchivedHabit = {}
                )
            }
        }

        listOf(
            "Timer duration",
            "Pause overlay position",
            "Observation target",
            "Mastery milestone",
            "Custom context tags",
            "Import / export"
        ).forEach { label ->
            compose.onAllNodesWithText(label).assertCountEquals(0)
        }

        compose.onNodeWithText("More settings").performScrollTo().performClick()
        listOf(
            "Timer duration",
            "Pause overlay position",
            "Observation target",
            "Mastery milestone",
            "Custom context tags",
            "Import / export"
        ).forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertExists()
        }
        listOf("Top", "Middle", "Bottom", "Full screen").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertExists()
        }
        compose.onNodeWithText("Full screen").performScrollTo().performClick()
        assertEquals(PauseOverlayPosition.FULL_SCREEN, selectedPosition)
    }

    private fun observationState() = ObservationUiState(
        config = HabitConfigEntity(habitName = "Test habit"),
        emotions = listOf(tag("Bored", TagCategory.EMOTION)),
        locations = listOf(tag("Bed", TagCategory.LOCATION)),
        isLoading = false
    )

    private fun tag(name: String, category: TagCategory) = ContextTagEntity(
        name = name,
        category = category
    )

    private data class QuickLogResult(
        val emotion: String,
        val location: String,
        val intensity: UrgeIntensity,
        val outcome: TriggerOutcomeType,
        val customEmotion: Boolean,
        val customLocation: Boolean
    )

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
