package com.galaxyairpods.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DailyStepCount(val date: LocalDate, val total: Long?)
data class HourlyStepCount(val hour: Int, val total: Long?)

data class SleepSessionSummary(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val totalSleepSeconds: Long,
    val awakeSeconds: Long,
    val lightSeconds: Long,
    val deepSeconds: Long,
    val remSeconds: Long,
)

data class SyncedExerciseSession(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val exerciseType: Int,
    val title: String?,
    val originPackage: String,
)

data class HealthConnectTodayState(
    val available: Boolean = false,
    val permissionCheckComplete: Boolean = false,
    val stepsPermissionGranted: Boolean = false,
    val sleepPermissionGranted: Boolean = false,
    val activeCaloriesPermissionGranted: Boolean = false,
    val distancePermissionGranted: Boolean = false,
    val exercisePermissionGranted: Boolean = false,
    val writePermissionsGranted: Boolean = false,
    val todaySteps: Long? = null,
    val hourlySteps: List<HourlyStepCount> = emptyList(),
    val todayActiveCaloriesKcal: Double? = null,
    val todayDistanceMeters: Double? = null,
    val recentSleepSeconds: Long? = null,
    val latestSleep: SleepSessionSummary? = null,
    val weekSteps: List<DailyStepCount> = emptyList(),
    val recentExerciseSessions: List<SyncedExerciseSession> = emptyList(),
    val readFailed: Boolean = false,
)

/** Reads only user-authorized Health Connect data; missing records remain unknown. */
object HealthConnectTodayReader {
    private val _state = MutableStateFlow(HealthConnectTodayState())
    val state = _state.asStateFlow()

    val readPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    private val writePermissions = setOf(
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
            _state.value = HealthConnectTodayState(permissionCheckComplete = true)
            return@withContext
        }

        try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val granted = client.permissionController.getGrantedPermissions()
            val hasSteps = HealthPermission.getReadPermission(StepsRecord::class) in granted
            val hasSleep = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
            val hasActiveCalories = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted
            val hasDistance = HealthPermission.getReadPermission(DistanceRecord::class) in granted
            val hasExercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted
            val hasWrites = granted.containsAll(writePermissions)
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val weekStartDate = LocalDate.now(zone).minusDays(6)
            val weekStart = weekStartDate.atStartOfDay(zone).toInstant()
            val sleepWindowStart = now.minus(24, ChronoUnit.HOURS)
            var readFailed = false

            val steps = if (hasSteps) runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now),
                    ),
                )[StepsRecord.COUNT_TOTAL]
            }.onFailure { readFailed = true }.getOrNull() else null

            val calories = if (hasActiveCalories) runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now),
                    ),
                )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            }.onFailure { readFailed = true }.getOrNull() else null

            val distance = if (hasDistance) runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now),
                    ),
                )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
            }.onFailure { readFailed = true }.getOrNull() else null

            val sleepSeconds = if (hasSleep) runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, now),
                    ),
                )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.seconds
            }.onFailure { readFailed = true }.getOrNull() else null

            val weekSteps = if (hasSteps) runCatching {
                val grouped = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(weekStart, now),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                ).associate { bucket ->
                    bucket.startTime.atZone(zone).toLocalDate() to bucket.result[StepsRecord.COUNT_TOTAL]
                }
                (0L..6L).map { dayOffset ->
                    val day = weekStartDate.plusDays(dayOffset)
                    DailyStepCount(day, grouped[day]?.coerceAtLeast(0L))
                }
            }.onFailure { readFailed = true }.getOrDefault(emptyList()) else emptyList()

            val hourlySteps = if (hasSteps && steps != null) runCatching {
                val grouped = client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now),
                        timeRangeSlicer = Duration.ofHours(1),
                    ),
                ).associate { bucket ->
                    Duration.between(todayStart, bucket.startTime).toHours().toInt() to
                        bucket.result[StepsRecord.COUNT_TOTAL]?.coerceAtLeast(0L)
                }
                val completedHours = (Duration.between(todayStart, now).toHours().toInt() + 1).coerceIn(1, 25)
                (0 until completedHours).map { slot ->
                    val hour = todayStart.plus(Duration.ofHours(slot.toLong())).atZone(zone).hour
                    HourlyStepCount(hour, grouped[slot] ?: 0L)
                }
            }.onFailure { readFailed = true }.getOrDefault(emptyList()) else emptyList()

            val latestSleep = if (hasSleep) runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, now),
                        ascendingOrder = false,
                        pageSize = 20,
                    ),
                ).records.maxByOrNull { it.endTime }
                    ?.let { session ->
                        fun stageSeconds(stage: Int) = session.stages
                            .filter { it.stage == stage }
                            .sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()).coerceAtLeast(0L) / 1_000L }
                        val light = stageSeconds(SleepSessionRecord.STAGE_TYPE_LIGHT)
                        val deep = stageSeconds(SleepSessionRecord.STAGE_TYPE_DEEP)
                        val rem = stageSeconds(SleepSessionRecord.STAGE_TYPE_REM)
                        val genericSleep = stageSeconds(SleepSessionRecord.STAGE_TYPE_SLEEPING)
                        val awake = stageSeconds(SleepSessionRecord.STAGE_TYPE_AWAKE) +
                            stageSeconds(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED) +
                            stageSeconds(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
                        val stagedSleep = light + deep + rem + genericSleep
                        SleepSessionSummary(
                            startEpochMillis = session.startTime.toEpochMilli(),
                            endEpochMillis = session.endTime.toEpochMilli(),
                            totalSleepSeconds = stagedSleep.takeIf { it > 0L }
                                ?: (session.endTime.toEpochMilli() - session.startTime.toEpochMilli() - awake * 1_000L).coerceAtLeast(0L) / 1_000L,
                            awakeSeconds = awake,
                            lightSeconds = light,
                            deepSeconds = deep,
                            remSeconds = rem,
                        )
                    }
            }.onFailure { readFailed = true }.getOrNull() else null

            val syncedSessions = if (hasExercise) runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(weekStart, now),
                        ascendingOrder = false,
                        pageSize = 30,
                    ),
                ).records.asSequence()
                    .filter { it.metadata.dataOrigin.packageName != appContext.packageName }
                    .map {
                        SyncedExerciseSession(
                            startEpochMillis = it.startTime.toEpochMilli(),
                            endEpochMillis = it.endTime.toEpochMilli(),
                            exerciseType = it.exerciseType,
                            title = it.title?.takeIf(String::isNotBlank),
                            originPackage = it.metadata.dataOrigin.packageName,
                        )
                    }
                    .toList()
            }.onFailure { readFailed = true }.getOrDefault(emptyList()) else emptyList()

            _state.value = HealthConnectTodayState(
                available = true,
                permissionCheckComplete = true,
                stepsPermissionGranted = hasSteps,
                sleepPermissionGranted = hasSleep,
                activeCaloriesPermissionGranted = hasActiveCalories,
                distancePermissionGranted = hasDistance,
                exercisePermissionGranted = hasExercise,
                writePermissionsGranted = hasWrites,
                todaySteps = steps?.takeIf { it >= 0L },
                hourlySteps = hourlySteps,
                todayActiveCaloriesKcal = calories?.takeIf { it.isFinite() && it >= 0.0 },
                todayDistanceMeters = distance?.takeIf { it.isFinite() && it >= 0.0 },
                recentSleepSeconds = sleepSeconds?.takeIf { it > 0L },
                latestSleep = latestSleep,
                weekSteps = weekSteps,
                recentExerciseSessions = syncedSessions,
                readFailed = readFailed,
            )
        } catch (_: Exception) {
            _state.value = HealthConnectTodayState(
                available = true,
                permissionCheckComplete = true,
                readFailed = true,
            )
        }
    }
}
