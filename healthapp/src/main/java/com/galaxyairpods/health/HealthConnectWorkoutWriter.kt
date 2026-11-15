package com.galaxyairpods.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import java.time.Instant
import java.time.ZoneId
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import android.location.Location

class HealthConnectWorkoutWriter(context: Context) {
    private val appContext = context.applicationContext

    suspend fun write(summary: CompanionWorkoutSummary) {
        require(summary.startedAtEpochMillis > 0L && summary.endedAtEpochMillis > summary.startedAtEpochMillis) {
            "Workout interval is invalid"
        }
        check(HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE) {
            "Health Connect is unavailable"
        }

        val client = HealthConnectClient.getOrCreate(appContext)
        val required = setOf(
            androidx.health.connect.client.permission.HealthPermission.getWritePermission(HeartRateRecord::class),
            androidx.health.connect.client.permission.HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
        check(client.permissionController.getGrantedPermissions().containsAll(required)) {
            "Health Connect permissions are missing"
        }

        val zone = ZoneId.systemDefault()
        fun instant(epochMillis: Long) = Instant.ofEpochMilli(epochMillis)
        fun offset(epochMillis: Long) = zone.rules.getOffset(instant(epochMillis))

        val validSamples = summary.samples
            .filter {
                it.bpm in MIN_BPM..MAX_BPM &&
                    it.capturedAtEpochMillis >= summary.startedAtEpochMillis &&
                    it.capturedAtEpochMillis < summary.endedAtEpochMillis
            }
            .distinctBy { it.capturedAtEpochMillis }
            .sortedBy { it.capturedAtEpochMillis }
        val sessionId = "echohealth_workout_${summary.startedAtEpochMillis}"
        val phone = Device(
            type = Device.TYPE_PHONE,
            manufacturer = "Echo",
            model = "Echo Health",
        )
        val heartRateRecords = validSamples
            .groupBy { it.capturedAtEpochMillis / MILLIS_PER_MINUTE }
            .values
            .map { minuteSamples ->
                val first = minuteSamples.first().capturedAtEpochMillis
                val last = minuteSamples.last().capturedAtEpochMillis
                val recordEnd = maxOf(first + 1, last + 1)
                HeartRateRecord(
                    startTime = instant(first),
                    startZoneOffset = offset(first),
                    endTime = instant(recordEnd),
                    endZoneOffset = offset(recordEnd),
                    samples = minuteSamples.map { sample ->
                        HeartRateRecord.Sample(
                            time = instant(sample.capturedAtEpochMillis),
                            beatsPerMinute = sample.bpm.toLong(),
                        )
                    },
                    metadata = Metadata.activelyRecorded(
                        phone,
                        "${sessionId}_hr_${first / MILLIS_PER_MINUTE}",
                        1L,
                    ),
                )
            }

        val routePermissionGranted = androidx.health.connect.client.permission.HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE in
            client.permissionController.getGrantedPermissions()
        val route = summary.route
            .filter { point ->
                point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0 &&
                    point.capturedAtEpochMillis in summary.startedAtEpochMillis until summary.endedAtEpochMillis &&
                    point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..MAX_ROUTE_ACCURACY_METERS
            }
            .distinctBy { it.capturedAtEpochMillis }
            .sortedBy { it.capturedAtEpochMillis }
            .take(MAX_ROUTE_POINTS)
        val exerciseRoute = if (routePermissionGranted && route.size >= MIN_ROUTE_POINTS) {
            ExerciseRoute(route.map { point ->
                ExerciseRoute.Location(
                    time = instant(point.capturedAtEpochMillis),
                    latitude = point.latitude,
                    longitude = point.longitude,
                    horizontalAccuracy = Length.meters(point.accuracyMeters.toDouble()),
                    verticalAccuracy = null,
                    altitude = point.altitudeMeters?.let(Length::meters),
                )
            })
        } else null

        val exerciseLaps = summary.laps
            .filter { lap ->
                lap.startedAtEpochMillis >= summary.startedAtEpochMillis &&
                    lap.endedAtEpochMillis <= summary.endedAtEpochMillis &&
                    lap.endedAtEpochMillis > lap.startedAtEpochMillis &&
                    lap.distanceMeters > 0.0 && lap.distanceMeters.isFinite()
            }
            .sortedBy { it.startedAtEpochMillis }
            .fold(mutableListOf<ExerciseLap>() to 0L) { (valid, previousEnd), lap ->
                if (lap.startedAtEpochMillis < previousEnd) return@fold valid to previousEnd
                valid += ExerciseLap(
                    instant(lap.startedAtEpochMillis),
                    instant(lap.endedAtEpochMillis),
                    Length.meters(lap.distanceMeters),
                )
                valid to lap.endedAtEpochMillis
            }
            .first

        val grantedPermissions = client.permissionController.getGrantedPermissions()
        val distanceRecord = summary.distanceMeters
            ?.takeIf { it > 0.0 && it.isFinite() }
            ?.takeIf {
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(DistanceRecord::class) in grantedPermissions
            }
            ?.let { meters ->
                DistanceRecord(
                    startTime = instant(summary.startedAtEpochMillis),
                    startZoneOffset = offset(summary.startedAtEpochMillis),
                    endTime = instant(summary.endedAtEpochMillis),
                    endZoneOffset = offset(summary.endedAtEpochMillis),
                    distance = Length.meters(meters),
                    metadata = Metadata.activelyRecorded(phone, "${sessionId}_distance", 1L),
                )
            }

        val speedRecord = if (HealthPermission.getWritePermission(SpeedRecord::class) in grantedPermissions) {
            val speedSamples = route.zipWithNext().mapNotNull { (previous, current) ->
                val elapsedSeconds = (current.capturedAtEpochMillis - previous.capturedAtEpochMillis) / 1_000.0
                if (elapsedSeconds <= 0.0 || elapsedSeconds > MAX_SPEED_SAMPLE_GAP_SECONDS) return@mapNotNull null
                val result = FloatArray(1)
                Location.distanceBetween(previous.latitude, previous.longitude, current.latitude, current.longitude, result)
                val metersPerSecond = result[0] / elapsedSeconds
                val maximum = if (summary.workoutType == WorkoutType.CYCLING.key) MAX_CYCLING_SPEED_MPS else MAX_WALK_RUN_SPEED_MPS
                if (!metersPerSecond.isFinite() || metersPerSecond !in MIN_RECORDED_SPEED_MPS..maximum) return@mapNotNull null
                SpeedRecord.Sample(
                    time = instant(current.capturedAtEpochMillis),
                    speed = Velocity.metersPerSecond(metersPerSecond),
                )
            }.distinctBy { it.time }.sortedBy { it.time }
            speedSamples.takeIf { it.isNotEmpty() }?.let { samples ->
                SpeedRecord(
                    startTime = samples.first().time,
                    startZoneOffset = offset(samples.first().time.toEpochMilli()),
                    endTime = samples.last().time.plusMillis(1),
                    endZoneOffset = offset(samples.last().time.toEpochMilli() + 1L),
                    samples = samples,
                    metadata = Metadata.activelyRecorded(phone, "${sessionId}_speed", 1L),
                )
            }
        } else null

        val session = ExerciseSessionRecord(
            startTime = instant(summary.startedAtEpochMillis),
            startZoneOffset = offset(summary.startedAtEpochMillis),
            endTime = instant(summary.endedAtEpochMillis),
            endZoneOffset = offset(summary.endedAtEpochMillis),
            exerciseType = WorkoutType.fromKey(summary.workoutType).healthConnectType,
            title = "EchoPods · ${WorkoutType.fromKey(summary.workoutType).englishLabel}",
            laps = exerciseLaps,
            exerciseRoute = exerciseRoute,
            metadata = Metadata.activelyRecorded(phone, "${sessionId}_session", 1L),
        )
        val records = buildList<Record> {
            addAll(heartRateRecords)
            distanceRecord?.let { add(it) }
            speedRecord?.let { add(it) }
            add(session)
        }
        client.insertRecords(records)
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MIN_BPM = 30
        private const val MAX_BPM = 220
        private const val MAX_ROUTE_ACCURACY_METERS = 50f
        private const val MAX_ROUTE_POINTS = 1_500
        private const val MIN_ROUTE_POINTS = 2
        private const val MIN_RECORDED_SPEED_MPS = 0.2
        private const val MAX_WALK_RUN_SPEED_MPS = 12.0
        private const val MAX_CYCLING_SPEED_MPS = 45.0
        private const val MAX_SPEED_SAMPLE_GAP_SECONDS = 15.0
    }
}
