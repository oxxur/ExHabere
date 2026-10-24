package org.exhabere.ui.action

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.exhabere.data.db.model.HabitPhase
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.data.db.model.UrgeIntensity
import org.exhabere.ui.components.QuickLogSheet
import org.exhabere.viewmodel.ActionUiState
import org.exhabere.viewmodel.EmergencyOption
import org.exhabere.viewmodel.MilestoneState
import org.exhabere.R
import org.exhabere.ui.localizedDefaultPlan
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionScreen(
    state: ActionUiState,
    onOpenSettings: () -> Unit,
    onOpenInsights: () -> Unit,
    onEmergency: () -> Unit,
    onLogUrge: (String, String, UrgeIntensity, TriggerOutcomeType, Boolean, Boolean) -> Unit,
    pendingEmergencyLogId: Long? = null,
    onUpdateEmergencyLog: (Long, String, String, UrgeIntensity, Boolean, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onEmergencyLogDetailsHandled: () -> Unit = {},
    onShowMastery: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showLogSheet by remember { mutableStateOf(false) }
    LaunchedEffect(pendingEmergencyLogId) {
        if (pendingEmergencyLogId != null) showLogSheet = true
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val cleanSince = state.config?.cleanStreakStartMillis
    val elapsed = cleanSince?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
    val cleanDays = (elapsed / MILLIS_PER_DAY).toInt()
    val cleanHours = ((elapsed % MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()
    val cleanMinutes = ((elapsed % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
    val targetDays = state.config?.targetMasteryDays ?: 66

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.config?.habitName?.takeUnless { it == "Doomscrolling" }
                            ?: stringResource(if (state.config == null) R.string.action_phase else R.string.default_habit_name)
                    )
                },
                actions = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) } }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MilestoneProgress(
                        cleanDays = cleanDays,
                        targetDays = targetDays,
                        milestones = state.milestones
                    )
                    Text(stringResource(R.string.current_clean_time), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.clean_time_days_hours, cleanDays, cleanHours),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        stringResource(R.string.clean_time_minutes_resistance, cleanMinutes, state.resistancePercent),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onEmergency,
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.emergency_support), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }

            OutlinedButton(onClick = { showLogSheet = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.log_an_urge_short))
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.replacement_plan_title), style = MaterialTheme.typography.titleMedium)
                    Text(state.config?.replacementPlan?.takeIf { it.isNotBlank() }.localizedDefaultPlan() ?: stringResource(R.string.add_plan_in_settings))
                }
            }

            OutlinedButton(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.view_insights))
            }

            if (cleanDays >= targetDays && state.config?.phase != HabitPhase.MASTERY) {
                OutlinedButton(onClick = onShowMastery, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.celebrate_milestone, targetDays))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLogSheet) {
        QuickLogSheet(
            emotions = state.emotions.map { it.name },
            locations = state.locations.map { it.name },
            editingEmergencyOutcome = pendingEmergencyLogId != null,
            onDismiss = {
                showLogSheet = false
                if (pendingEmergencyLogId != null) onEmergencyLogDetailsHandled()
            },
            onSave = { emotion, location, intensity, outcomeType, customEmotion, customLocation ->
                val emergencyLogId = pendingEmergencyLogId
                if (emergencyLogId != null) {
                    onUpdateEmergencyLog(emergencyLogId, emotion, location, intensity, customEmotion, customLocation)
                    onEmergencyLogDetailsHandled()
                } else {
                    onLogUrge(emotion, location, intensity, outcomeType, customEmotion, customLocation)
                }
                showLogSheet = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MilestoneProgress(
    cleanDays: Int,
    targetDays: Int,
    milestones: List<MilestoneState>
) {
    val coroutineScope = rememberCoroutineScope()
    val safeTarget = targetDays.coerceAtLeast(1)
    val progress = (cleanDays.toFloat() / safeTarget).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val tooltipEdgeMarginPx = with(density) { 8.dp.roundToPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || fontScale >= 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.milestone_progress), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.days_progress, cleanDays, targetDays),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.milestone_progress), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.days_progress, cleanDays, targetDays),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    val markerSize = (28.dp * fontScale).coerceAtMost(40.dp)
        .coerceAtLeast(28.dp)
    val labelHeight = 18.dp * fontScale
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(markerSize + 2.dp + labelHeight)
    ) {
        val labelWidth = (28.dp * fontScale).coerceAtMost(maxWidth / 3).coerceAtLeast(28.dp)
        val markerTravel = (maxWidth - markerSize).coerceAtLeast(0.dp)

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (markerSize / 2 - 2.dp).coerceAtLeast(8.dp))
                .fillMaxWidth()
                .padding(horizontal = markerSize / 2)
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        milestones.forEach { milestone ->
            val fraction = (milestone.day.toFloat() / safeTarget).coerceIn(0f, 1f)
            val markerOffset = markerTravel * fraction
            val milestoneName = milestoneName(milestone.day, safeTarget)
            val icon = milestoneIcon(milestone.day, safeTarget)
            val milestoneStatus = stringResource(if (milestone.completed) R.string.completed else R.string.upcoming)
            val milestoneDescription = stringResource(R.string.milestone_accessibility, milestone.day, milestoneName, milestoneStatus)
            val tooltipState = rememberTooltipState(isPersistent = true)
            val tooltipText = milestoneTooltip(milestone.day, safeTarget)
            var markerBoundsInWindow by remember(milestone.day) { mutableStateOf<IntRect?>(null) }
            val tooltipPositionProvider = remember(markerBoundsInWindow, tooltipEdgeMarginPx) {
                MilestoneTooltipPositionProvider(tooltipEdgeMarginPx, markerBoundsInWindow)
            }
            LaunchedEffect(tooltipState.transition.targetState) {
                if (tooltipState.transition.targetState) {
                    delay(MILESTONE_TOOLTIP_DURATION_MILLIS)
                    tooltipState.dismiss()
                }
            }

            TooltipBox(
                positionProvider = tooltipPositionProvider,
                tooltip = { PlainTooltip { Text(tooltipText) } },
                state = tooltipState,
                modifier = Modifier.align(Alignment.TopStart).offset(x = markerOffset).size(markerSize)
            ) {
                Box(
                    modifier = Modifier
                        .size(markerSize)
                        .background(
                            if (milestone.completed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (milestone.completed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable {
                            coroutineScope.launch {
                                if (tooltipState.isVisible) tooltipState.dismiss()
                                else tooltipState.show()
                            }
                        }
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            val left = position.x.roundToInt()
                            val top = position.y.roundToInt()
                            val bounds = IntRect(
                                left = left,
                                top = top,
                                right = left + coordinates.size.width,
                                bottom = top + coordinates.size.height
                            )
                            if (markerBoundsInWindow != bounds) markerBoundsInWindow = bounds
                        }
                        .semantics { contentDescription = milestoneDescription },
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = if (milestone.completed) 23.sp else 15.sp)
                }
            }

            val labelOffset = (markerOffset + markerSize / 2 - labelWidth / 2)
                .coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))
            val dayDescription = stringResource(R.string.day_number, milestone.day)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = labelOffset, y = markerSize + 2.dp)
                    .size(width = labelWidth, height = labelHeight)
                    .semantics { contentDescription = dayDescription },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    milestone.day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (milestone.completed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun milestoneName(day: Int, targetDays: Int): String = stringResource(when (day) {
    7 -> R.string.circuit_breaker
    21 -> R.string.structural_anchor
    targetDays -> R.string.mastery_baseline
    else -> R.string.milestone
})

@Composable
private fun milestoneTooltip(day: Int, targetDays: Int): String = stringResource(when (day) {
    7 -> R.string.circuit_breaker_tooltip
    21 -> R.string.structural_anchor_tooltip
    targetDays -> R.string.mastery_baseline_tooltip
    else -> R.string.milestone_tooltip
})

@Composable
private fun localizedReliefMethod(method: String): String = stringResource(when (method) {
    "Box Breathing Exercise" -> R.string.relief_method_box_breathing
    "Physiological Sigh" -> R.string.relief_method_physiological_sigh
    "Cold Water Reset" -> R.string.relief_method_cold_water
    else -> R.string.relief_method_grounding
})

private fun milestoneIcon(day: Int, targetDays: Int): String = when (day) {
    7 -> "⚡"
    21 -> "⚓"
    targetDays -> "🏆"
    else -> "★"
}

private class MilestoneTooltipPositionProvider(
    private val edgeMarginPx: Int,
    private val markerBoundsInWindow: IntRect?
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchor = markerBoundsInWindow ?: anchorBounds
        val centeredX = anchor.left + (anchor.width - popupContentSize.width) / 2
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val x = centeredX.coerceIn(edgeMarginPx, maxX)

        val below = anchor.bottom + edgeMarginPx
        val above = anchor.top - popupContentSize.height - edgeMarginPx
        val maxY = (windowSize.height - popupContentSize.height - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val y = if (below + popupContentSize.height <= windowSize.height - edgeMarginPx || above < edgeMarginPx) {
            below.coerceAtMost(maxY)
        } else {
            above.coerceAtLeast(edgeMarginPx)
        }

        return IntOffset(x, y)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EmergencyDecisionScreen(
    state: ActionUiState,
    onBack: () -> Unit,
    onSelectOption: (EmergencyOption) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onCommit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.urge_struck), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.choose_response), color = MaterialTheme.colorScheme.onSurfaceVariant)

            EmergencyChoiceCard(
                title = stringResource(R.string.immediate_relief),
                description = state.selectedReliefMethod?.let { localizedReliefMethod(it) } ?: stringResource(R.string.grounding_exercise),
                selected = state.selectedOption == EmergencyOption.RELIEF,
                onClick = { onSelectOption(EmergencyOption.RELIEF) }
            )
            EmergencyChoiceCard(
                title = stringResource(R.string.alternative_action),
                description = state.config?.replacementPlan?.takeIf { it.isNotBlank() }.localizedDefaultPlan() ?: stringResource(R.string.mindful_pause),
                selected = state.selectedOption == EmergencyOption.ALTERNATIVE,
                onClick = { onSelectOption(EmergencyOption.ALTERNATIVE) }
            )

            Text(stringResource(R.string.pause_duration), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 5, 10).forEach { minutes ->
                    FilterChip(
                        selected = state.selectedDurationMinutes == minutes,
                        onClick = { onSelectDuration(minutes) },
                        label = { Text(stringResource(R.string.minutes_short, minutes)) }
                    )
                }
            }
            Button(onClick = onCommit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.commit_start_pause), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmergencyChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) Text(stringResource(R.string.emergency_option_selected), color = MaterialTheme.colorScheme.primary)
        }
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
private const val MILESTONE_TOOLTIP_DURATION_MILLIS = 3_000L
