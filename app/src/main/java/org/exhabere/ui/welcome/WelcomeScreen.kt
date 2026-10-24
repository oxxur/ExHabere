package org.exhabere.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import org.exhabere.R

private val DeepSlate = Color(0xFF0F172A)
private val SteelSurface = Color(0xFF334155)
private val Mint = Color(0xFF34D399)
private val HeadlineWhite = Color(0xFFF8FAFC)
private val BodyGray = Color(0xFFCBD5E1)
private val CaptionGray = Color(0xFF94A3B8)
private val StepGray = Color(0xFF64748B)
private val GraphicSurface = Color(0xFF1E293B)

private enum class WelcomeStep { PHILOSOPHY, HABIT_NAME, OBSERVATION }

@Composable
fun WelcomeScreen(
    initialHabitName: String,
    isCompleting: Boolean,
    onComplete: (String) -> Unit,
    startAtHabitName: Boolean = false
) {
    var step by rememberSaveable(startAtHabitName) {
        mutableStateOf(if (startAtHabitName) WelcomeStep.HABIT_NAME else WelcomeStep.PHILOSOPHY)
    }
    var habitName by rememberSaveable(initialHabitName) { mutableStateOf(initialHabitName) }
    val canContinue = habitName.trim().isNotEmpty() && !isCompleting

    BackHandler(enabled = step != WelcomeStep.PHILOSOPHY) {
        step = if (step == WelcomeStep.OBSERVATION) WelcomeStep.HABIT_NAME else WelcomeStep.PHILOSOPHY
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DeepSlate) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step == WelcomeStep.PHILOSOPHY) {
                    Spacer(Modifier.width(64.dp))
                } else {
                    TextButton(
                        onClick = {
                            step = if (step == WelcomeStep.OBSERVATION) {
                                WelcomeStep.HABIT_NAME
                            } else {
                                WelcomeStep.PHILOSOPHY
                            }
                        },
                        modifier = Modifier.width(64.dp)
                    ) {
                    Text(stringResource(R.string.back), color = Mint)
                    }
                }
                Text(
                    text = stringResource(R.string.step_of_three, step.ordinal + 1),
                    modifier = Modifier.weight(1f),
                    color = StepGray,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(64.dp))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "welcome onboarding step"
            ) { targetStep ->
                when (targetStep) {
                    WelcomeStep.PHILOSOPHY -> PhilosophyContent(
                        onContinue = { step = WelcomeStep.HABIT_NAME }
                    )
                    WelcomeStep.HABIT_NAME -> HabitNameContent(
                        habitName = habitName,
                        onHabitNameChange = { habitName = it.take(MAX_HABIT_NAME_LENGTH) },
                        onContinue = { if (canContinue) step = WelcomeStep.OBSERVATION }
                    )
                    WelcomeStep.OBSERVATION -> ObservationIntroContent(
                        onBegin = { if (canContinue) onComplete(habitName.trim()) },
                        isCompleting = isCompleting,
                        canContinue = canContinue
                    )
                }
            }

            PrivacyBadge(modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun PhilosophyContent(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        ExHabereAppIcon()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            color = HeadlineWhite,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.welcome_description),
            color = BodyGray,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        JourneyCard()
        Spacer(Modifier.height(22.dp))
        PrimaryActionButton(text = stringResource(R.string.get_started), onClick = onContinue)
    }
}

@Composable
private fun ExHabereAppIcon() {
    val appIconDescription = stringResource(R.string.app_icon_description)
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = appIconDescription
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = Modifier.size(156.dp)
    )
}

@Composable
private fun JourneyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GraphicSurface)
            .border(1.dp, SteelSurface, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(
            text = stringResource(R.string.journey_title),
            color = HeadlineWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        JourneyRow("1", stringResource(R.string.journey_observe), stringResource(R.string.journey_observe_detail))
        JourneyRow("2", stringResource(R.string.journey_plan), stringResource(R.string.journey_plan_detail))
        JourneyRow("3", stringResource(R.string.journey_rewire), stringResource(R.string.journey_rewire_detail))
    }
}

@Composable
private fun JourneyRow(number: String, title: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(SteelSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = HeadlineWhite, fontWeight = FontWeight.SemiBold)
            Text(detail, color = CaptionGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HabitNameContent(
    habitName: String,
    onHabitNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.one_habit_at_a_time),
            color = HeadlineWhite,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.habit_question),
            color = BodyGray,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = habitName,
            onValueChange = onHabitNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.name_this_habit)) },
            placeholder = { Text(stringResource(R.string.habit_name_example)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onContinue() })
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.habit_name_guidance),
            color = CaptionGray,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        PrimaryActionButton(
            text = stringResource(R.string.continue_action),
            enabled = habitName.trim().isNotEmpty(),
            onClick = onContinue
        )
    }
}

@Composable
private fun ObservationIntroContent(
    onBegin: () -> Unit,
    isCompleting: Boolean,
    canContinue: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.start_by_noticing),
            color = HeadlineWhite,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.observation_intro),
            color = BodyGray,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GraphicSurface)
                .border(1.dp, SteelSurface, RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.your_only_goal), color = Mint, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.observation_goal),
                color = BodyGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(28.dp))
        PrimaryActionButton(
            text = stringResource(if (isCompleting) R.string.starting else R.string.begin_observing),
            enabled = canContinue,
            onClick = onBegin
        )
    }
}

@Composable
private fun PrivacyBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(DeepSlate)
            .border(1.dp, SteelSurface, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.privacy_badge),
            color = Mint,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Mint,
            contentColor = DeepSlate,
            disabledContainerColor = Mint.copy(alpha = 0.45f),
            disabledContentColor = DeepSlate.copy(alpha = 0.75f)
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

private const val MAX_HABIT_NAME_LENGTH = 48
