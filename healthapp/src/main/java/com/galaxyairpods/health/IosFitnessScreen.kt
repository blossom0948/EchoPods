package com.galaxyairpods.health

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val MoveRing = Color(0xFFFF5268)
private const val ECHO_MOVE_GOAL_KCAL = 500

@Composable
internal fun IosFitnessHeader(
    title: String,
    subtitle: String?,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                FitnessBackButton(onBack)
                Spacer(Modifier.size(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FitnessBackButton(onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (motionEnabled) spring(dampingRatio = 0.86f, stiffness = 700f) else tween(0),
        label = "fitness_back_press",
    )
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.size(38.dp).scale(scale).clip(CircleShape).clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = if (locale.language == "ko") "뒤로" else "Back",
            )
        }
    }
}

@Composable
private fun FitnessSecondaryButton(
    backdrop: LayerBackdrop,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    HealthIosGlassControl(
        backdrop = backdrop,
        modifier = modifier.heightIn(min = 44.dp),
        cornerRadius = 24.dp,
        onClick = onClick,
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

internal fun fitnessCurrentDate(isKorean: Boolean): String {
    val locale = if (isKorean) Locale.KOREAN else Locale.getDefault()
    val pattern = if (isKorean) "M월 d일 EEEE" else "EEEE, MMM d"
    return SimpleDateFormat(pattern, locale).format(Date())
}

@Composable
internal fun IosFitnessTodayScreen(
    backdrop: LayerBackdrop,
    wide: Boolean,
    isKorean: Boolean,
    overview: HealthConnectTodayState,
    healthConnectAvailable: Boolean,
    onConnectHealthData: () -> Unit,
    stepGoal: Int,
    onStepGoalChanged: (Int) -> Unit,
    weeklyExerciseGoalMinutes: Int,
    onWeeklyExerciseGoalChanged: (Int) -> Unit,
    activeWorkoutStartedAtMillis: Long?,
    echoPodsInstalled: Boolean,
    selectedWorkoutType: WorkoutType,
    isConnected: Boolean,
    active: Boolean,
    paused: Boolean,
    locationTracking: WorkoutTrackingData,
    currentBpm: Int?,
    status: String,
    elapsed: Long,
    averageBpm: Int?,
    maxBpm: Int?,
    sampleCount: Int,
    recentSamples: List<CompanionHeartRateSample>,
    history: List<SavedWorkout>,
    workoutGoalTarget: WorkoutGoalTarget,
    onWorkoutGoalSelected: (WorkoutGoalTarget) -> Unit,
    onWorkoutTypeSelected: (WorkoutType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenEchoPods: () -> Unit,
    onOpenWorkoutTab: () -> Unit,
    onOpenHistory: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val completedWorkoutMillis = history.sumOf { workout ->
        if (Instant.ofEpochMilli(workout.startedAtEpochMillis).atZone(zone).toLocalDate() == today) {
            workout.activeDurationMillis ?: (workout.endedAtEpochMillis - workout.startedAtEpochMillis).coerceAtLeast(0L)
        } else 0L
    }
    val activeToday = activeWorkoutStartedAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
    } == true
    val exerciseMinutes = ((completedWorkoutMillis + if (active && activeToday) elapsed else 0L) / 60_000L)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FitnessQuickInsights(
            isKorean = isKorean,
            steps = overview.todaySteps,
            activeCaloriesKcal = overview.todayActiveCaloriesKcal,
            currentBpm = currentBpm,
        )
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FitnessActivitySummaryCard(
                        isKorean = isKorean,
                        activeCaloriesKcal = overview.todayActiveCaloriesKcal,
                        steps = overview.todaySteps,
                        exerciseMinutes = exerciseMinutes,
                        sleepSeconds = overview.recentSleepSeconds,
                        distanceMeters = overview.todayDistanceMeters,
                        hourlySteps = overview.hourlySteps,
                        weekSteps = overview.weekSteps,
                        stepGoal = stepGoal,
                        onStepGoalChanged = onStepGoalChanged,
                        activeCaloriesPermissionGranted = overview.activeCaloriesPermissionGranted,
                    )
                    overview.latestSleep?.let { FitnessSleepStagesCard(it, isKorean, useMaterial = false) }
                    WeeklyExerciseSummaryCard(
                        isKorean = isKorean,
                        useMaterial = false,
                        localWorkouts = history,
                        sharedWorkouts = overview.recentExerciseSessions,
                        exercisePermissionGranted = overview.exercisePermissionGranted,
                        goalMinutes = weeklyExerciseGoalMinutes,
                        onGoalMinutesChanged = onWeeklyExerciseGoalChanged,
                        activeWorkoutStartedAtMillis = activeWorkoutStartedAtMillis,
                        activeWorkoutElapsedMillis = if (active) elapsed else 0L,
                    )
                    FitnessHealthConnectCard(backdrop, overview, healthConnectAvailable, isKorean, onConnectHealthData)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FitnessWorkoutActionCard(
                        backdrop, isKorean, selectedWorkoutType, isConnected, echoPodsInstalled, active, paused, locationTracking, elapsed,
                        workoutGoalTarget, onWorkoutGoalSelected,
                        onWorkoutTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods,
                    )
                    HeartRateCard(
                        currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm,
                        sampleCount, recentSamples, isKorean, false,
                    )
                    FitnessRecentSessionsCard(history.take(3), isKorean, onOpenHistory)
                }
            }
        } else {
            FitnessActivitySummaryCard(
                isKorean = isKorean,
                activeCaloriesKcal = overview.todayActiveCaloriesKcal,
                steps = overview.todaySteps,
                exerciseMinutes = exerciseMinutes,
                sleepSeconds = overview.recentSleepSeconds,
                distanceMeters = overview.todayDistanceMeters,
                hourlySteps = overview.hourlySteps,
                weekSteps = overview.weekSteps,
                stepGoal = stepGoal,
                onStepGoalChanged = onStepGoalChanged,
                activeCaloriesPermissionGranted = overview.activeCaloriesPermissionGranted,
            )
            overview.latestSleep?.let { FitnessSleepStagesCard(it, isKorean, useMaterial = false) }
            WeeklyExerciseSummaryCard(
                isKorean = isKorean,
                useMaterial = false,
                localWorkouts = history,
                sharedWorkouts = overview.recentExerciseSessions,
                exercisePermissionGranted = overview.exercisePermissionGranted,
                goalMinutes = weeklyExerciseGoalMinutes,
                onGoalMinutesChanged = onWeeklyExerciseGoalChanged,
                activeWorkoutStartedAtMillis = activeWorkoutStartedAtMillis,
                activeWorkoutElapsedMillis = if (active) elapsed else 0L,
            )
            FitnessWorkoutActionCard(
                backdrop, isKorean, selectedWorkoutType, isConnected, echoPodsInstalled, active, paused, locationTracking, elapsed,
                workoutGoalTarget, onWorkoutGoalSelected,
                onWorkoutTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods,
            )
            HeartRateCard(
                currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm,
                sampleCount, recentSamples, isKorean, false,
            )
            FitnessHealthConnectCard(backdrop, overview, healthConnectAvailable, isKorean, onConnectHealthData)
            FitnessRecentSessionsCard(history.take(3), isKorean, onOpenHistory)
            FitnessSecondaryButton(
                backdrop = backdrop,
                text = if (isKorean) "운동 화면 열기" else "Open workout",
                modifier = Modifier.align(Alignment.End),
                onClick = onOpenWorkoutTab,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun IosFitnessWorkoutScreen(
    backdrop: LayerBackdrop,
    isKorean: Boolean,
    echoPodsInstalled: Boolean,
    selectedWorkoutType: WorkoutType,
    isConnected: Boolean,
    active: Boolean,
    paused: Boolean,
    locationTracking: WorkoutTrackingData,
    currentBpm: Int?,
    status: String,
    elapsed: Long,
    averageBpm: Int?,
    maxBpm: Int?,
    sampleCount: Int,
    recentSamples: List<CompanionHeartRateSample>,
    workoutGoalTarget: WorkoutGoalTarget,
    onWorkoutGoalSelected: (WorkoutGoalTarget) -> Unit,
    onWorkoutTypeSelected: (WorkoutType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenEchoPods: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FitnessWorkoutActionCard(
            backdrop, isKorean, selectedWorkoutType, isConnected, echoPodsInstalled, active, paused, locationTracking, elapsed,
            workoutGoalTarget, onWorkoutGoalSelected,
            onWorkoutTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods,
        )
        HeartRateCard(
            currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm,
            sampleCount, recentSamples, isKorean, false,
        )
        HealthCard(false) {
            Text(if (isKorean) "운동 기록" else "Workout recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (isKorean) "운동 시간과 종류는 심박 센서 없이도 저장됩니다. AirPods 심박수는 지원되는 모델과 펌웨어에서만 실험적으로 표시됩니다." else "Workout type and duration are saved without a heart-rate sensor. AirPods heart rate remains experimental and depends on model and firmware support.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun IosFitnessBottomNavigation(
    selected: String,
    isKorean: Boolean,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit,
) {
    val items = listOf(
        Triple("TODAY", Icons.Outlined.Home, if (isKorean) "요약" else "Summary"),
        Triple("WORKOUT", Icons.AutoMirrored.Outlined.DirectionsRun, if (isKorean) "운동" else "Workout"),
        Triple("HISTORY", Icons.Outlined.History, if (isKorean) "기록" else "History"),
    )
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 350.dp)
                .fillMaxWidth(0.82f)
                .shadow(18.dp, CircleShape, clip = false)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    highlight = { com.kyant.backdrop.highlight.Highlight.Ambient.copy(alpha = if (dark) 1f else 0.2f) },
                    effects = {
                        vibrancy()
                        blur(5.dp.toPx())
                        lens(
                            refractionHeight = 8.dp.toPx(),
                            refractionAmount = 18.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = false,
                        )
                    },
                    onDrawSurface = {
                        drawRect(if (dark) Color(0xFF26262B).copy(alpha = 0.68f) else Color.White.copy(alpha = 0.70f))
                    },
                )
                .border(
                    1.dp,
                    if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.88f),
                    CircleShape,
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items.forEach { (key, icon, label) ->
                val active = selected == key
                HealthIosGlassControl(
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    selected = active,
                    surfaceOpacity = if (active) 0.40f else 0.03f,
                    tint = colors.primary,
                    cornerRadius = 24.dp,
                    contentAlignment = Alignment.Center,
                    role = Role.Tab,
                    onClick = { onSelected(key) },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (active) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) colors.primary else colors.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FitnessQuickInsights(
    isKorean: Boolean,
    steps: Long?,
    activeCaloriesKcal: Double?,
    currentBpm: Int?,
) {
    val formatter = remember(isKorean) {
        NumberFormat.getIntegerInstance(if (isKorean) Locale.KOREA else Locale.getDefault())
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        QuickInsightChip(
            title = if (isKorean) "걸음" else "Steps",
            value = steps?.let { formatter.format(it) } ?: "—",
            icon = Icons.AutoMirrored.Outlined.DirectionsRun,
            accent = Color(0xFF4E9BE8),
        )
        QuickInsightChip(
            title = if (isKorean) "활동" else "Fitness",
            value = activeCaloriesKcal?.let { "${formatter.format(it.roundToInt())} kcal" } ?: "—",
            icon = Icons.Outlined.FitnessCenter,
            accent = MoveRing,
        )
        QuickInsightChip(
            title = if (isKorean) "심박수" else "Heart",
            value = currentBpm?.let { "$it bpm" } ?: "—",
            icon = Icons.Outlined.FavoriteBorder,
            accent = Color(0xFFFF5268),
        )
    }
}

@Composable
private fun QuickInsightChip(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FitnessActivitySummaryCard(
    isKorean: Boolean,
    activeCaloriesKcal: Double?,
    steps: Long?,
    exerciseMinutes: Long,
    sleepSeconds: Long?,
    distanceMeters: Double?,
    hourlySteps: List<HourlyStepCount>,
    weekSteps: List<DailyStepCount>,
    stepGoal: Int,
    onStepGoalChanged: (Int) -> Unit,
    activeCaloriesPermissionGranted: Boolean,
) {
    val numberFormat = remember(isKorean) { NumberFormat.getIntegerInstance(if (isKorean) Locale.KOREA else Locale.getDefault()) }
    val calorieValue = activeCaloriesKcal?.let { numberFormat.format(it.roundToInt()) } ?: "—"
    val stepValue = steps?.let { numberFormat.format(it) } ?: "—"
    val exerciseValue = exerciseMinutes.toString() + if (isKorean) "분" else " min"
    val sleepValue = sleepSeconds?.let { formatFitnessSleep(it, isKorean) } ?: "—"
    val distanceValue = distanceMeters?.let { formatFitnessDistance(it, isKorean) } ?: "—"
    val moveProgress = activeCaloriesKcal?.let { (it / ECHO_MOVE_GOAL_KCAL).toFloat().coerceIn(0f, 1f) }
    HealthCard(false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "활동 에너지" else "Active energy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (isKorean) "오늘의 움직임" else "Your activity today",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (activeCaloriesKcal != null) "$calorieValue / $ECHO_MOVE_GOAL_KCAL kcal" else "— / $ECHO_MOVE_GOAL_KCAL kcal",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MoveRing,
                )
            }
            MoveActivityRing(
                caloriesKcal = activeCaloriesKcal,
                progress = moveProgress,
                valueLabel = calorieValue,
                isKorean = isKorean,
                ringSize = 120.dp,
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FitnessSummaryStat(Modifier.weight(1f), if (isKorean) "걸음" else "Steps", stepValue)
                FitnessSummaryStat(Modifier.weight(1f), if (isKorean) "운동" else "Workout", exerciseValue)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FitnessSummaryStat(Modifier.weight(1f), if (isKorean) "수면" else "Sleep", sleepValue)
                FitnessSummaryStat(Modifier.weight(1f), if (isKorean) "거리" else "Distance", distanceValue)
            }
        }
        StepGoalPicker(stepGoal, isKorean, useMaterial = false, onSelected = onStepGoalChanged)
        FitnessHourlyStepsChart(hourlySteps, isKorean)
        FitnessWeeklyStepsChart(weekSteps, isKorean)
        Text(
            when {
                activeCaloriesKcal != null -> if (isKorean) "활동 에너지는 Health Connect 기록입니다. 걸음·수면은 동기화된 데이터, 운동은 에코 헬스 기록입니다." else "Active energy is imported from Health Connect. Steps and sleep are synced data; workouts are Echo Health sessions."
                !activeCaloriesPermissionGranted -> if (isKorean) "활동 에너지 링은 Health Connect 권한을 허용한 뒤 실제 기록이 있을 때만 채워집니다." else "The activity ring fills only with real records after Health Connect access is allowed."
                else -> if (isKorean) "오늘 동기화된 활동 에너지 기록이 없습니다. 걸음·수면은 동기화된 데이터, 운동은 에코 헬스 기록입니다." else "No active-energy record is synced for today. Steps and sleep are synced data; workouts are Echo Health sessions."
            },
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FitnessWeeklyStepsChart(data: List<DailyStepCount>, isKorean: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val values = data.map { it.total }
    val maxSteps = values.filterNotNull().maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val average = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isKorean) "최근 7일 걸음" else "Steps · last 7 days", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                average?.let { if (isKorean) "평균 ${NumberFormat.getIntegerInstance(Locale.KOREA).format(it)}" else "Avg ${NumberFormat.getIntegerInstance().format(it)}" } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data.isEmpty()) {
            Text(
                if (isKorean) "걸음 데이터 권한을 허용하면 주간 추세가 표시됩니다." else "Allow step access to see your weekly trend.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Row(Modifier.fillMaxWidth().height(88.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            data.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                        val trackWidth = size.width * 0.56f
                        val left = (size.width - trackWidth) / 2f
                        val barHeight = day.total?.let { (size.height * (it.toFloat() / maxSteps).coerceIn(0.05f, 1f)) } ?: 0f
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(trackWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
                        )
                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = MoveRing.copy(alpha = if (day.date == LocalDate.now()) 1f else 0.68f),
                                topLeft = Offset(left, size.height - barHeight),
                                size = Size(trackWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
                            )
                        }
                    }
                    val dayLabel = if (isKorean) day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.KOREAN)
                    else day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale)
                    Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun FitnessSleepStagesCard(sleep: SleepSessionSummary, isKorean: Boolean, useMaterial: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val totalLabel = formatFitnessSleep(sleep.totalSleepSeconds, isKorean)
    val stages = listOf(
        Triple(if (isKorean) "깸" else "Awake", sleep.awakeSeconds, Color(0xFFFF9F0A)),
        Triple(if (isKorean) "얕은 수면" else "Light", sleep.lightSeconds, Color(0xFF35C2D1)),
        Triple(if (isKorean) "깊은 수면" else "Deep", sleep.deepSeconds, Color(0xFF4F7CF3)),
        Triple("REM", sleep.remSeconds, Color(0xFFB16CF1)),
    )
    val hasStages = stages.any { it.second > 0L }
    HealthCard(useMaterial) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "최근 수면" else "Recent sleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(totalLabel, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                SimpleDateFormat(if (isKorean) "M/d" else "MMM d", if (isKorean) Locale.KOREAN else locale).format(Date(sleep.endEpochMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasStages) {
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(9.dp)) {
                val total = stages.sumOf { it.second }.coerceAtLeast(1L).toFloat()
                var x = 0f
                stages.filter { it.second > 0L }.forEach { (_, seconds, color) ->
                    val width = size.width * (seconds / total)
                    drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
                    x += width
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.filter { it.second > 0L }.forEach { (label, seconds, color) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatFitnessSleep(seconds, isKorean), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        } else {
            Text(
                if (isKorean) "수면 시간은 동기화됐지만, 수면 단계는 데이터 제공 앱에서 공유되지 않았습니다." else "Sleep duration synced, but the source app did not share stage details.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatFitnessDistance(meters: Double, isKorean: Boolean): String {
    val locale = if (isKorean) Locale.KOREA else Locale.getDefault()
    return if (meters >= 1_000.0) "${"%.1f".format(locale, meters / 1_000.0)} km" else "${meters.roundToInt()} m"
}

@Composable
private fun MoveActivityRing(
    caloriesKcal: Double?,
    progress: Float?,
    valueLabel: String,
    isKorean: Boolean,
    ringSize: Dp = 204.dp,
) {
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val animatedProgress = moveRingProgress(progress, motionEnabled)
    Box(
        Modifier.size(ringSize).semantics {
            contentDescription = if (isKorean) "활동 에너지 ${valueLabel}킬로칼로리, 목표 ${ECHO_MOVE_GOAL_KCAL}킬로칼로리" else "Active energy $valueLabel kilocalories, goal $ECHO_MOVE_GOAL_KCAL kilocalories"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = if (ringSize < 150.dp) 10.dp.toPx() else 17.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val diameter = radius * 2f
            val origin = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = MoveRing.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = origin,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (caloriesKcal != null && animatedProgress > 0.002f) {
                drawArc(
                    color = MoveRing,
                    startAngle = -90f,
                    sweepAngle = 359.5f * animatedProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = origin,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = if (caloriesKcal == null) "—" else valueLabel,
                transitionSpec = {
                    val duration = if (motionEnabled) 180 else 0
                    fadeIn(tween(duration)) togetherWith fadeOut(tween(if (duration > 0) 120 else 0))
                },
                label = "move_energy_value",
            ) { value ->
                Text(value, fontSize = if (ringSize < 150.dp) 23.sp else 34.sp, lineHeight = if (ringSize < 150.dp) 27.sp else 38.sp, fontWeight = FontWeight.Bold)
            }
            Text("kcal", style = if (ringSize < 150.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun moveRingProgress(target: Float?, motionEnabled: Boolean): Float {
    val animation = androidx.compose.runtime.remember { Animatable(0f) }
    LaunchedEffect(target, motionEnabled) {
        if (target == null) {
            animation.snapTo(0f)
        } else {
            animation.animateTo(
                target.coerceIn(0f, 1f),
                animationSpec = tween(
                    durationMillis = if (motionEnabled) 640 else 0,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    return animation.value
}

@Composable
private fun FitnessSummaryStat(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Column(modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val duration = if (ValueAnimator.areAnimatorsEnabled()) 180 else 0
                fadeIn(tween(duration)) togetherWith fadeOut(tween(if (duration > 0) 120 else 0))
            },
            label = "fitness_metric_value",
        ) { animatedValue ->
            Text(animatedValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FitnessWorkoutActionCard(
    backdrop: LayerBackdrop,
    isKorean: Boolean,
    selectedWorkoutType: WorkoutType,
    isConnected: Boolean,
    echoPodsInstalled: Boolean,
    active: Boolean,
    paused: Boolean,
    locationTracking: WorkoutTrackingData,
    elapsed: Long,
    workoutGoalTarget: WorkoutGoalTarget,
    onWorkoutGoalSelected: (WorkoutGoalTarget) -> Unit,
    onWorkoutTypeSelected: (WorkoutType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenEchoPods: () -> Unit,
) {
    var showWorkoutPicker by remember { mutableStateOf(false) }
    HealthCard(false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "운동" else "Workout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (active) {
                        (if (paused) (if (isKorean) "일시정지 · " else "Paused · ") else (if (isKorean) "운동 진행 중 · " else "In progress · ")) + formatFitnessDuration(elapsed)
                    } else if (isKorean) "기록할 운동을 선택하세요" else "Choose a workout to record",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        HealthIosGlassControl(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !active,
            selected = true,
            surfaceOpacity = 0.18f,
            tint = MaterialTheme.colorScheme.primary,
            cornerRadius = 16.dp,
            pressedScale = 0.985f,
            onClick = { showWorkoutPicker = true },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(if (isKorean) "운동 종류" else "Workout type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selectedWorkoutType.label(isKorean), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (isKorean) "변경  ›" else "Change  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(14.dp))
        WorkoutGoalSelector(
            selectedType = selectedWorkoutType,
            target = workoutGoalTarget,
            isKorean = isKorean,
            useMaterial = false,
            onSelected = onWorkoutGoalSelected,
            backdrop = backdrop,
            enabled = !active,
        )
        Spacer(Modifier.height(12.dp))
        if (active) {
            WorkoutProgressCard(locationTracking, selectedWorkoutType, elapsed, paused, isKorean, useMaterial = false, workoutGoalTarget = workoutGoalTarget, backdrop = backdrop)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FitnessPrimaryButton(
                    backdrop = backdrop,
                    text = if (paused) (if (isKorean) "운동 재개" else "Resume") else (if (isKorean) "일시정지" else "Pause"),
                    destructive = false,
                    modifier = Modifier.weight(1f),
                    onClick = if (paused) onResume else onPause,
                )
                FitnessPrimaryButton(
                    backdrop = backdrop,
                    text = if (isKorean) "운동 종료" else "Finish",
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                )
            }
        } else {
            FitnessPrimaryButton(
                backdrop = backdrop,
                text = if (isKorean) "${selectedWorkoutType.label(true)} 시작" else "Start ${selectedWorkoutType.label(false).lowercase()}",
                destructive = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onStart,
            )
        }
        Text(
            if (isKorean) "운동 기록은 이어폰 없이도 저장됩니다. 심박수 측정은 호환되는 AirPods가 연결된 경우에만 시도합니다." else "Workout tracking works without earbuds. Heart rate is attempted only when compatible AirPods are connected.",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isConnected && echoPodsInstalled) {
            FitnessSecondaryButton(
                backdrop = backdrop,
                text = if (isKorean) "에코팟 열기" else "Open EchoPods",
                modifier = Modifier.align(Alignment.End),
                onClick = onOpenEchoPods,
            )
        } else if (!echoPodsInstalled) {
            Text(if (isKorean) "에코팟을 설치하면 별도 연결 없이 자동 연동됩니다." else "EchoPods links automatically when installed.", modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showWorkoutPicker) {
        WorkoutTypePickerDialog(
            selected = selectedWorkoutType,
            isKorean = isKorean,
            onDismiss = { showWorkoutPicker = false },
            onSelected = {
                onWorkoutTypeSelected(it)
                showWorkoutPicker = false
            },
        )
    }
}

@Composable
private fun WorkoutTypePickerDialog(
    selected: WorkoutType,
    isKorean: Boolean,
    onDismiss: () -> Unit,
    onSelected: (WorkoutType) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isKorean) "운동 선택" else "Choose workout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (isKorean) "활동 종류를 고르세요" else "Select an activity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(if (isKorean) "닫기" else "Close") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(420.dp)) {
                    items(WorkoutType.entries) { type ->
                        val isSelected = type == selected
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = Role.RadioButton) { onSelected(type) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(type.label(isKorean), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FitnessWorkoutSegment(
    backdrop: LayerBackdrop,
    modifier: Modifier,
    type: WorkoutType,
    selected: Boolean,
    isKorean: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val selectedTextColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (type == WorkoutType.OTHER && !isKorean) "Other" else type.label(isKorean)
    HealthIosGlassControl(
        backdrop = backdrop,
        modifier = modifier,
        selected = selected,
        enabled = enabled,
        cornerRadius = 20.dp,
        role = Role.RadioButton,
        onClick = onClick,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = selectedTextColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FitnessPrimaryButton(
    backdrop: LayerBackdrop,
    text: String,
    destructive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    HealthIosGlassControl(
        backdrop = backdrop,
        modifier = modifier.heightIn(min = 54.dp),
        selected = true,
        tint = if (destructive) Color(0xFFFF453A) else MaterialTheme.colorScheme.primary,
        cornerRadius = 16.dp,
        pressedScale = 0.985f,
        onClick = onClick,
    ) {
        Text(
            text,
            color = if (destructive) Color(0xFFFF453A) else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FitnessHealthConnectCard(
    backdrop: LayerBackdrop,
    overview: HealthConnectTodayState,
    available: Boolean,
    isKorean: Boolean,
    onConnect: () -> Unit,
) {
    HealthCard(false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "건강 데이터" else "Health data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !available -> if (isKorean) "Health Connect를 사용할 수 없습니다." else "Health Connect is unavailable."
                        !overview.stepsPermissionGranted || !overview.sleepPermissionGranted || !overview.activeCaloriesPermissionGranted || !overview.distancePermissionGranted || !overview.exercisePermissionGranted -> if (isKorean) "걸음·수면·활동·거리·운동 기록 권한이 필요합니다." else "Allow access to read steps, sleep, activity, distance, and workouts."
                        overview.readFailed -> if (isKorean) "동기화를 확인한 뒤 다시 열어주세요." else "Check sync and reopen this screen."
                        else -> if (isKorean) "Health Connect에서 동기화된 기록을 읽습니다." else "Reading synced records from Health Connect."
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (available && (!overview.stepsPermissionGranted || !overview.sleepPermissionGranted || !overview.activeCaloriesPermissionGranted || !overview.distancePermissionGranted || !overview.exercisePermissionGranted)) {
                FitnessSecondaryButton(backdrop = backdrop, text = if (isKorean) "허용" else "Allow", onClick = onConnect)
            }
        }
        Text(
            if (isKorean) "이 값은 Echo Health가 직접 측정하지 않습니다. Samsung Health 등 제공 앱이 Health Connect에 공유한 활동 에너지·걸음·수면·거리·운동 기록을 읽습니다." else "Echo Health does not measure these values. It reads active energy, steps, sleep, distance, and workouts that source apps share through Health Connect.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FitnessRecentSessionsCard(
    history: List<SavedWorkout>,
    isKorean: Boolean,
    onOpenHistory: (Long) -> Unit,
) {
    HealthCard(false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isKorean) "최근 운동" else "Recent workouts", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (history.isEmpty()) {
            Text(if (isKorean) "운동을 마치면 여기에 표시됩니다." else "Completed workouts appear here.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            history.forEach { WorkoutHistoryRow(it, isKorean, onOpenHistory) }
        }
    }
}

private fun formatFitnessSleep(seconds: Long, isKorean: Boolean): String {
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return if (isKorean) {
        if (hours > 0L) hours.toString() + "시간 " + minutes + "분" else minutes.toString() + "분"
    } else {
        if (hours > 0L) hours.toString() + "h " + minutes + "m" else minutes.toString() + "m"
    }
}

private fun formatFitnessDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
