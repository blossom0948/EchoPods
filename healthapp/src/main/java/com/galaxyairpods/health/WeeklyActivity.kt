package com.galaxyairpods.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.LayerBackdrop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val weeklyGoalOptions = listOf(60, 90, 120, 150, 210, 300)

internal enum class WorkoutGoalKind { NONE, DURATION, DISTANCE }

internal data class WorkoutGoalTarget(val kind: WorkoutGoalKind, val value: Long = 0L) {
    companion object {
        val NONE = WorkoutGoalTarget(WorkoutGoalKind.NONE)
    }
}

private fun workoutGoalOptions(type: WorkoutType): List<WorkoutGoalTarget> = buildList {
    add(WorkoutGoalTarget.NONE)
    listOf(15L, 20L, 30L, 45L, 60L).forEach { minutes ->
        add(WorkoutGoalTarget(WorkoutGoalKind.DURATION, minutes * 60_000L))
    }
    if (type.recordsGps) {
        listOf(1L, 3L, 5L, 10L).forEach { kilometers ->
            add(WorkoutGoalTarget(WorkoutGoalKind.DISTANCE, kilometers * 1_000L))
        }
    }
}

private fun workoutGoalLabel(target: WorkoutGoalTarget, isKorean: Boolean): String = when (target.kind) {
    WorkoutGoalKind.NONE -> if (isKorean) "목표 없음" else "No goal"
    WorkoutGoalKind.DURATION -> {
        val minutes = target.value / 60_000L
        if (isKorean) "${minutes}분" else "$minutes min"
    }
    WorkoutGoalKind.DISTANCE -> {
        val kilometers = target.value / 1_000L
        if (isKorean) "${kilometers}km" else "$kilometers km"
    }
}

@Composable
internal fun WorkoutGoalSelector(
    selectedType: WorkoutType,
    target: WorkoutGoalTarget,
    isKorean: Boolean,
    useMaterial: Boolean,
    onSelected: (WorkoutGoalTarget) -> Unit,
    backdrop: LayerBackdrop? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(selectedType) { workoutGoalOptions(selectedType) }
    val effectiveTarget = target.takeIf { candidate -> options.contains(candidate) } ?: WorkoutGoalTarget.NONE
    if (!useMaterial && backdrop != null) {
        HealthIosGlassControl(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            selected = effectiveTarget.kind != WorkoutGoalKind.NONE,
            cornerRadius = 16.dp,
            pressedScale = 0.985f,
            onClick = { expanded = true },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isKorean) "운동 목표" else "Workout goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(workoutGoalLabel(effectiveTarget, isKorean), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(if (isKorean) "변경  ›" else "Change  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Box(Modifier.fillMaxWidth()) {
            FilterChip(
                selected = effectiveTarget.kind != WorkoutGoalKind.NONE,
                enabled = enabled,
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                label = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isKorean) "운동 목표" else "Workout goal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(workoutGoalLabel(effectiveTarget, isKorean), fontWeight = FontWeight.SemiBold)
                        Text("  ▾", color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
            if (useMaterial) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(workoutGoalLabel(option, isKorean)) },
                            onClick = {
                                onSelected(option)
                                expanded = false
                            },
                            leadingIcon = if (option == effectiveTarget) ({ Text("✓", color = MaterialTheme.colorScheme.primary) }) else null,
                        )
                    }
                }
            }
        }
    }

    if (!useMaterial && expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        if (isKorean) "운동 목표" else "Workout goal",
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (option == effectiveTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = Role.RadioButton) {
                                    onSelected(option)
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                workoutGoalLabel(option, isKorean),
                                modifier = Modifier.weight(1f),
                                fontWeight = if (option == effectiveTarget) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (option == effectiveTarget) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkoutGoalProgress(
    target: WorkoutGoalTarget,
    workoutType: WorkoutType,
    elapsedMillis: Long,
    distanceMeters: Double,
    isKorean: Boolean,
) {
    if (target.kind == WorkoutGoalKind.NONE) return
    if (target.kind == WorkoutGoalKind.DISTANCE && !workoutType.recordsGps) return
    val current = if (target.kind == WorkoutGoalKind.DURATION) elapsedMillis.toDouble() else distanceMeters
    val targetValue = target.value.toDouble().coerceAtLeast(1.0)
    val progress = (current / targetValue).toFloat().coerceIn(0f, 1f)
    val currentLabel = if (target.kind == WorkoutGoalKind.DURATION) {
        val minutes = (elapsedMillis / 60_000L).coerceAtLeast(0L)
        if (isKorean) "${minutes}분" else "$minutes min"
    } else {
        goalDistanceLabel(distanceMeters, isKorean)
    }
    val goalLabel = workoutGoalLabel(target, isKorean)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val fill = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isKorean) "목표 진행" else "Goal progress", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$currentLabel / $goalLabel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 7.dp).height(6.dp)) {
            val radius = size.height / 2f
            drawRoundRect(track, cornerRadius = CornerRadius(radius))
            if (progress > 0f) drawRoundRect(fill, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(radius))
        }
        if (progress >= 1f) {
            Text(
                if (isKorean) "목표에 도달했습니다. 계속 운동하거나 종료할 수 있습니다." else "Goal reached. Continue or finish your workout when ready.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun goalDistanceLabel(meters: Double, isKorean: Boolean): String = when {
    meters >= 1_000.0 -> "%.1f km".format(Locale.getDefault(), meters / 1_000.0)
    meters > 0.0 -> "%.0f m".format(Locale.getDefault(), meters)
    else -> if (isKorean) "0m" else "0 m"
}

private data class ExerciseDay(
    val date: LocalDate,
    var activeMillis: Long = 0L,
    var sessions: Int = 0,
)

private data class ExerciseWeek(
    val days: List<ExerciseDay>,
    val activeMinutes: Int,
    val sessions: Int,
    val activeDays: Int,
)

@Composable
internal fun WeeklyExerciseSummaryCard(
    isKorean: Boolean,
    useMaterial: Boolean,
    localWorkouts: List<SavedWorkout>,
    sharedWorkouts: List<SyncedExerciseSession>,
    exercisePermissionGranted: Boolean,
    goalMinutes: Int,
    onGoalMinutesChanged: (Int) -> Unit,
    activeWorkoutStartedAtMillis: Long?,
    activeWorkoutElapsedMillis: Long,
) {
    val now = System.currentTimeMillis()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(now, zone) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
    val week = remember(localWorkouts, sharedWorkouts, activeWorkoutStartedAtMillis, activeWorkoutElapsedMillis, now, zone) {
        buildExerciseWeek(
            localWorkouts = localWorkouts,
            sharedWorkouts = sharedWorkouts,
            activeWorkoutStartedAtMillis = activeWorkoutStartedAtMillis,
            activeWorkoutElapsedMillis = activeWorkoutElapsedMillis,
            now = now,
            today = today,
            zone = zone,
        )
    }
    var goalMenuExpanded by remember { mutableStateOf(false) }
    val goal = goalMinutes.coerceIn(30, 600)
    val progress = (week.activeMinutes.toFloat() / goal).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val barColor = MaterialTheme.colorScheme.primary

    HealthCard(useMaterial) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isKorean) "주간 운동" else "Weekly activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isKorean) "최근 7일 · 완료 및 진행 중인 운동" else "Last 7 days · completed and active workouts",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton(onClick = { goalMenuExpanded = true }) {
                    Text(
                        if (isKorean) "목표 ${goal}분 ▾" else "${goal} min goal ▾",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (useMaterial) {
                    DropdownMenu(expanded = goalMenuExpanded, onDismissRequest = { goalMenuExpanded = false }) {
                        weeklyGoalOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(if (isKorean) "주 ${option}분" else "$option min / week") },
                                onClick = {
                                    onGoalMinutesChanged(option)
                                    goalMenuExpanded = false
                                },
                                leadingIcon = if (option == goal) ({ Text("✓", color = MaterialTheme.colorScheme.primary) }) else null,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                week.activeMinutes.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (isKorean) " / ${goal}분" else " / $goal min",
                modifier = Modifier.padding(start = 5.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (isKorean) "${week.sessions}회 · ${week.activeDays}일" else "${week.sessions} sessions · ${week.activeDays} days",
                modifier = Modifier.padding(bottom = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(7.dp)
                .semantics {
                    contentDescription = if (isKorean) "주간 운동 목표 ${week.activeMinutes}분 / ${goal}분" else "Weekly workout goal ${week.activeMinutes} of $goal minutes"
                },
        ) {
            val radius = size.height / 2f
            drawRoundRect(trackColor, cornerRadius = CornerRadius(radius))
            if (progress > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(size.width * progress, size.height),
                    cornerRadius = CornerRadius(radius),
                )
            }
        }
        Text(
            when {
                week.activeMinutes >= goal -> if (isKorean) "이번 주 목표를 달성했습니다." else "You reached this week's goal."
                week.sessions == 0 && !exercisePermissionGranted -> if (isKorean) "에코 헬스 운동은 바로 집계됩니다. 다른 앱 기록은 Health Connect 운동 권한을 허용하면 포함됩니다." else "Echo Health workouts count now. Allow exercise access to include workouts shared through Health Connect."
                week.sessions == 0 -> if (isKorean) "운동을 기록하면 주간 활동이 여기에 쌓입니다." else "Record a workout to start your weekly progress."
                else -> if (isKorean) "목표까지 ${goal - week.activeMinutes}분 남았습니다." else "${goal - week.activeMinutes} minutes to your goal."
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(74.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val maxMinutes = week.days.maxOfOrNull { it.activeMillis }?.coerceAtLeast(60_000L) ?: 60_000L
            week.days.forEach { day ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Canvas(
                        Modifier.fillMaxWidth().height(54.dp)
                            .semantics {
                                val minutes = (day.activeMillis / 60_000.0).roundToInt()
                                contentDescription = if (isKorean) "${day.date} 운동 ${minutes}분" else "Workout on ${day.date}: $minutes minutes"
                            },
                    ) {
                        val barWidth = size.width * 0.58f
                        val left = (size.width - barWidth) / 2f
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(barWidth / 2f),
                        )
                        val fraction = (day.activeMillis.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            val barHeight = (size.height * fraction).coerceAtLeast(3.dp.toPx())
                            drawRoundRect(
                                color = barColor.copy(alpha = if (day.date == today) 1f else 0.68f),
                                topLeft = Offset(left, size.height - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f),
                            )
                        }
                    }
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, if (isKorean) Locale.KOREAN else Locale.getDefault()),
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (!useMaterial && goalMenuExpanded) {
        Dialog(
            onDismissRequest = { goalMenuExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        if (isKorean) "주간 운동 목표" else "Weekly workout goal",
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    weeklyGoalOptions.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (option == goal) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = Role.RadioButton) {
                                    onGoalMinutesChanged(option)
                                    goalMenuExpanded = false
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isKorean) "주 ${option}분" else "$option minutes per week",
                                modifier = Modifier.weight(1f),
                                fontWeight = if (option == goal) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (option == goal) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun buildExerciseWeek(
    localWorkouts: List<SavedWorkout>,
    sharedWorkouts: List<SyncedExerciseSession>,
    activeWorkoutStartedAtMillis: Long?,
    activeWorkoutElapsedMillis: Long,
    now: Long,
    today: LocalDate,
    zone: ZoneId,
): ExerciseWeek {
    val days = (6L downTo 0L).map { today.minusDays(it) }.associateWith { ExerciseDay(it) }.toMutableMap()
    val distinctShared = sharedWorkouts.distinctBy { Triple(it.startEpochMillis, it.endEpochMillis, it.exerciseType) }

    fun record(startMillis: Long, endMillis: Long, activeDurationMillis: Long) {
        if (startMillis <= 0L || endMillis <= startMillis) return
        val date = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val day = days[date] ?: return
        val wallDuration = (endMillis - startMillis).coerceAtLeast(1L)
        day.activeMillis += activeDurationMillis.coerceIn(0L, wallDuration)
        day.sessions += 1
    }

    localWorkouts.forEach { workout ->
        val end = workout.endedAtEpochMillis.coerceAtMost(now)
        val wallDuration = (end - workout.startedAtEpochMillis).coerceAtLeast(0L)
        val duration = workout.activeDurationMillis?.coerceAtMost(wallDuration) ?: wallDuration
        record(workout.startedAtEpochMillis, end, duration)
    }
    distinctShared.forEach { session ->
        val end = session.endEpochMillis.coerceAtMost(now)
        record(session.startEpochMillis, end, (end - session.startEpochMillis).coerceAtLeast(0L))
    }
    activeWorkoutStartedAtMillis?.takeIf { activeWorkoutElapsedMillis > 0L }?.let { start ->
        record(start, now.coerceAtLeast(start + 1L), activeWorkoutElapsedMillis)
    }

    val orderedDays = days.values.toList()
    val totalMillis = orderedDays.sumOf { it.activeMillis }
    return ExerciseWeek(
        days = orderedDays,
        activeMinutes = (totalMillis / 60_000.0).roundToInt(),
        sessions = orderedDays.sumOf { it.sessions },
        activeDays = orderedDays.count { it.activeMillis >= 60_000L },
    )
}
