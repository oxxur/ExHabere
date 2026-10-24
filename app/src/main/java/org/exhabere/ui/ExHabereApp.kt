package org.exhabere.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.TriggerPhaseOrigin
import org.exhabere.data.backup.HabitBackup
import org.exhabere.data.backup.HabitBackupJsonCodec
import org.exhabere.di.AppContainer
import org.exhabere.service.LockoutOverlayService
import org.exhabere.ui.action.ActionScreen
import org.exhabere.ui.action.EmergencyDecisionScreen
import org.exhabere.ui.action.MasteryScreen
import org.exhabere.ui.action.calculateMasteryStats
import org.exhabere.ui.insights.InsightsScreen
import org.exhabere.ui.observation.ObservationScreen
import org.exhabere.ui.setup.PlanSetupScreen
import org.exhabere.ui.settings.SettingsScreen
import org.exhabere.ui.welcome.WelcomeScreen
import org.exhabere.viewmodel.ActionViewModel
import org.exhabere.viewmodel.EmergencyLaunchRequest
import org.exhabere.viewmodel.ObservationViewModel
import org.exhabere.viewmodel.PlanSetupViewModel
import org.exhabere.viewmodel.SettingsViewModel
import org.exhabere.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Destination { HOME, SETTINGS, MASTERY, NEW_HABIT_NAME, PLAN_SETUP, INSIGHTS }

@Composable
fun ExHabereApp(
    container: AppContainer,
    onStartOverlay: (EmergencyLaunchRequest) -> Unit,
    onRequestOverlayPermission: (EmergencyLaunchRequest) -> Unit,
    pendingEmergencyLogId: Long? = null,
    onEmergencyLogDetailsHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val observationViewModel: ObservationViewModel = viewModel(
        factory = ObservationViewModel.Factory(container.habitRepository, container.triggerLogRepository)
    )
    val actionViewModel: ActionViewModel = viewModel(
        factory = ActionViewModel.Factory(container.habitRepository, container.triggerLogRepository)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.habitRepository,
            container.triggerLogRepository,
            container.habitBackupRepository,
            container.habitArchiveRepository
        )
    )
    val planSetupViewModel: PlanSetupViewModel = viewModel(
        factory = PlanSetupViewModel.Factory(container.habitRepository, container.triggerLogRepository)
    )
    val observationState by observationViewModel.uiState.collectAsState()
    val actionState by actionViewModel.uiState.collectAsState()
    val customTags by settingsViewModel.customTags.collectAsState()
    val archivedHabits by settingsViewModel.archivedHabits.collectAsState()
    val selectedArchivedHabit by settingsViewModel.selectedArchivedHabit.collectAsState()
    val planSetupState by planSetupViewModel.uiState.collectAsState()
    val onboardingComplete by container.onboardingPreferences.welcomeComplete.collectAsState(initial = null)
    val config = observationState.config ?: actionState.config
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var planSetupReturnDestination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var permissionRequest by remember { mutableStateOf<EmergencyLaunchRequest?>(null) }
    var pendingRestore by remember { mutableStateOf<HabitBackup?>(null) }
    var isCompletingWelcome by remember { mutableStateOf(false) }
    var isArchivingMastery by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(HabitBackupJsonCodec.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = settingsViewModel.createHabitBackupJson()
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IOException(context.getString(R.string.export_write_failed))
                    }
                    Toast.makeText(context, context.getString(R.string.toast_exported), Toast.LENGTH_SHORT).show()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.toast_export_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val backup = withContext(Dispatchers.IO) {
                        val contents = context.contentResolver.openInputStream(uri)?.use {
                            it.readUtf8Limited(context.getString(R.string.import_too_large))
                        } ?: throw IOException(context.getString(R.string.import_read_failed))
                        settingsViewModel.parseHabitBackup(contents)
                    }
                    pendingRestore = backup
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Toast.makeText(context, context.getString(R.string.toast_import_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(actionViewModel) {
        actionViewModel.launchRequests.collect { request ->
            if (LockoutOverlayService.hasOverlayPermission(context)) onStartOverlay(request)
            else permissionRequest = request
        }
    }
    LaunchedEffect(observationViewModel) {
        observationViewModel.messages.collect { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(
        observationState.config?.phase,
        observationState.config?.observationStartDateMillis,
        observationState.config?.targetObservationDays
    ) {
        observationViewModel.advanceToActionIfObservationComplete()
    }
    LaunchedEffect(actionViewModel) {
        actionViewModel.messages.collect { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.messages.collect { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(planSetupViewModel) {
        planSetupViewModel.commitEvents.collect { destination = Destination.HOME }
    }
    LaunchedEffect(config?.phase) {
        if (config?.phase == HabitPhase.MASTERY && destination == Destination.HOME) {
            destination = Destination.MASTERY
        }
    }

    BackHandler(enabled = destination != Destination.HOME || actionState.isEmergencyVisible) {
        when {
            actionState.isEmergencyVisible -> actionViewModel.dismissEmergency()
            destination == Destination.PLAN_SETUP -> destination = planSetupReturnDestination
            destination == Destination.SETTINGS -> destination = Destination.HOME
            destination == Destination.MASTERY -> destination = Destination.HOME
            destination == Destination.NEW_HABIT_NAME -> destination = Destination.HOME
            destination == Destination.INSIGHTS -> destination = Destination.HOME
        }
    }

    if (config == null || onboardingComplete == null) {
        CircularProgressIndicator()
    } else if (destination == Destination.NEW_HABIT_NAME) {
        WelcomeScreen(
            initialHabitName = "",
            startAtHabitName = true,
            isCompleting = isCompletingWelcome,
            onComplete = { habitName ->
                if (!isCompletingWelcome) {
                    isCompletingWelcome = true
                    coroutineScope.launch {
                        try {
                            container.habitRepository.updateHabitName(habitName)
                            settingsViewModel.closeArchivedHabit()
                            destination = Destination.HOME
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_new_habit_save_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isCompletingWelcome = false
                        }
                    }
                }
            }
        )
    } else if (onboardingComplete == false) {
        WelcomeScreen(
            initialHabitName = if (config.habitName == "Doomscrolling") {
                context.getString(R.string.default_habit_name)
            } else config.habitName,
            isCompleting = isCompletingWelcome,
            onComplete = { habitName ->
                if (!isCompletingWelcome) {
                    isCompletingWelcome = true
                    coroutineScope.launch {
                        try {
                            container.habitRepository.updateHabitName(habitName)
                            container.onboardingPreferences.markWelcomeComplete()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_setup_save_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isCompletingWelcome = false
                        }
                    }
                }
            }
        )
    } else if (destination == Destination.MASTERY) {
        MasteryScreen(
            targetDays = config.targetMasteryDays,
            stats = calculateMasteryStats(
                logs = actionState.logs,
                approvedReliefMethods = config.approvedReliefTechniques,
                replacementPlan = config.replacementPlan
            ),
            replacementPlan = config.replacementPlan,
            isArchiving = isArchivingMastery,
            onContinueTracking = { destination = Destination.HOME },
            onArchiveAndStartNewHabit = {
                if (!isArchivingMastery) {
                    isArchivingMastery = true
                    coroutineScope.launch {
                        try {
                            settingsViewModel.archiveMasteredHabit()
                            settingsViewModel.closeArchivedHabit()
                            destination = Destination.NEW_HABIT_NAME
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_archive_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isArchivingMastery = false
                        }
                    }
                }
            }
        )
    } else if (destination == Destination.SETTINGS) {
        SettingsScreen(
            config = config,
            customTags = customTags,
            archivedHabits = archivedHabits,
            selectedArchivedHabit = selectedArchivedHabit,
            onBack = {
                settingsViewModel.closeArchivedHabit()
                destination = Destination.HOME
            },
            onSaveHabitName = settingsViewModel::updateHabitName,
            onSaveReplacementPlan = settingsViewModel::updateReplacementPlan,
            onUpdateReliefTechniques = settingsViewModel::updateReliefTechniques,
            onUpdateTimer = settingsViewModel::updateTimer,
            onUpdatePauseOverlayPosition = settingsViewModel::updatePauseOverlayPosition,
            onUpdateObservationDays = settingsViewModel::updateObservationDays,
            onUpdateMasteryDays = settingsViewModel::updateMasteryDays,
            onDeleteCustomTag = settingsViewModel::deleteCustomTag,
            onExportData = {
                exportBackupLauncher.launch(createBackupFileName(config.habitName))
            },
            onImportData = {
                importBackupLauncher.launch(arrayOf("*/*"))
            },
            onRevisitGuidedPlanSetup = {
                planSetupViewModel.beginSetup()
                planSetupReturnDestination = Destination.SETTINGS
                destination = Destination.PLAN_SETUP
            },
            onReturnToObservation = {
                settingsViewModel.returnToObservation()
                destination = Destination.HOME
            },
            onReset = {
                settingsViewModel.resetHabit()
                destination = Destination.HOME
            },
            onViewArchivedHabit = settingsViewModel::showArchivedHabit,
            onCloseArchivedHabit = settingsViewModel::closeArchivedHabit
        )
    } else if (destination == Destination.PLAN_SETUP) {
        PlanSetupScreen(
            state = planSetupState,
            onBack = { destination = planSetupReturnDestination },
            onPlanChange = planSetupViewModel::updateReplacementPlan,
            onChooseStarter = planSetupViewModel::updateReplacementPlan,
            onToggleRelief = planSetupViewModel::toggleReliefTechnique,
            onSelectTimer = planSetupViewModel::selectTimer,
            onCommit = planSetupViewModel::commitPlan
        )
    } else if (destination == Destination.INSIGHTS) {
        InsightsScreen(
            state = actionState,
            onBack = { destination = Destination.HOME }
        )
    } else if (config.phase == HabitPhase.OBSERVATION) {
        ObservationScreen(
            state = observationState,
            onOpenSettings = { destination = Destination.SETTINGS },
            onOpenInsights = { destination = Destination.INSIGHTS },
            onOpenPlanSetup = {
                planSetupViewModel.beginSetup()
                planSetupReturnDestination = Destination.HOME
                destination = Destination.PLAN_SETUP
            },
            onSaveLog = { emotion, location, intensity, outcomeType, customEmotion, customLocation ->
                observationViewModel.recordLog(
                    emotion, location, intensity, outcomeType, customEmotion, customLocation
                )
            }
        )
    } else if (actionState.isEmergencyVisible) {
        EmergencyDecisionScreen(
            state = actionState,
            onBack = actionViewModel::dismissEmergency,
            onSelectOption = actionViewModel::selectOption,
            onSelectDuration = actionViewModel::selectDuration,
            onCommit = actionViewModel::commitEmergency
        )
    } else {
        ActionScreen(
            state = actionState,
            onOpenSettings = { destination = Destination.SETTINGS },
            onOpenInsights = { destination = Destination.INSIGHTS },
            onEmergency = actionViewModel::beginEmergency,
            onLogUrge = { emotion, location, intensity, outcomeType, customEmotion, customLocation ->
                actionViewModel.recordLog(
                    emotion, location, intensity, outcomeType, customEmotion, customLocation
                )
            },
            pendingEmergencyLogId = pendingEmergencyLogId,
            onUpdateEmergencyLog = { logId, emotion, location, intensity, customEmotion, customLocation ->
                actionViewModel.addEmergencyLogDetails(
                    logId, emotion, location, intensity, customEmotion, customLocation
                )
            },
            onEmergencyLogDetailsHandled = onEmergencyLogDetailsHandled,
            onShowMastery = {
                actionViewModel.markMastered()
                destination = Destination.MASTERY
            }
        )
    }

    permissionRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { permissionRequest = null },
            title = { Text(stringResource(R.string.pause_permission_title)) },
            text = {
                Text(stringResource(R.string.pause_permission_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionRequest = null
                    onRequestOverlayPermission(request)
                }) { Text(stringResource(R.string.continue_to_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { permissionRequest = null }) { Text(stringResource(R.string.not_now)) }
            }
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_title, backup.habitConfig.habitName)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_warning,
                        config?.habitName ?: stringResource(R.string.current_habit),
                        backup.triggerLogs.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                settingsViewModel.restoreHabitBackup(backup)
                            }
                            destination = if (backup.habitConfig.phase == HabitPhase.MASTERY) {
                                Destination.MASTERY
                            } else {
                                Destination.HOME
                            }
                            Toast.makeText(context, context.getString(R.string.toast_restored), Toast.LENGTH_SHORT).show()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_restore_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.replace_current_data)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private fun createBackupFileName(habitName: String): String {
    val safeHabitName = habitName.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_')
        .take(48).ifBlank { "habit" }
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "ExHabere-$safeHabitName-$date.json"
}

private fun InputStream.readUtf8Limited(tooLargeMessage: String, maxBytes: Int = MAX_BACKUP_FILE_BYTES): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw IOException(tooLargeMessage)
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

private const val MAX_BACKUP_FILE_BYTES = 50 * 1024 * 1024
