package com.galaxyairpods.health

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedWorkout(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val type: String,
    val samples: List<CompanionHeartRateSample>,
    val activeDurationMillis: Long? = null,
    val distanceMeters: Double? = null,
    val route: List<WorkoutLocationSample> = emptyList(),
    val laps: List<WorkoutLap> = emptyList(),
) {
    val averageBpm: Int? get() = samples.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
    val maxBpm: Int? get() = samples.maxOfOrNull { it.bpm }
}

/** A small private on-device history; Health Connect export stays separately optional. */
object WorkoutHistoryStore {
    private const val PREFS = "workout_history"
    private const val KEY_HISTORY = "sessions_v1"
    private const val MAX_SESSIONS = 40
    private const val MAX_LOCAL_POINTS = 500
    private const val DUPLICATE_START_WINDOW_MS = 10_000L
    private val mutableHistory = MutableStateFlow<List<SavedWorkout>>(emptyList())
    val history = mutableHistory.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HISTORY, null)
        mutableHistory.value = decode(encoded)
    }

    @Synchronized
    fun append(context: Context, summary: CompanionWorkoutSummary) {
        val points = downsample(summary.samples)
        val saved = SavedWorkout(
            startedAtEpochMillis = summary.startedAtEpochMillis,
            endedAtEpochMillis = summary.endedAtEpochMillis,
            type = summary.workoutType,
            samples = points,
            activeDurationMillis = summary.activeDurationMillis,
            distanceMeters = summary.distanceMeters,
            route = downsampleRoute(summary.route),
            laps = summary.laps.take(MAX_LAPS),
        )
        val matching = mutableHistory.value.firstOrNull {
            kotlin.math.abs(it.startedAtEpochMillis - saved.startedAtEpochMillis) <= DUPLICATE_START_WINDOW_MS
        }
        val merged = if (matching == null) saved else {
            val preferSaved = saved.samples.size >= matching.samples.size
            val richer = if (preferSaved) saved else matching
            richer.copy(
                startedAtEpochMillis = minOf(saved.startedAtEpochMillis, matching.startedAtEpochMillis),
                endedAtEpochMillis = maxOf(saved.endedAtEpochMillis, matching.endedAtEpochMillis),
                samples = if (preferSaved) saved.samples else matching.samples,
                activeDurationMillis = maxOf(saved.activeDurationMillis ?: 0L, matching.activeDurationMillis ?: 0L).takeIf { it > 0L },
                distanceMeters = maxOf(saved.distanceMeters ?: 0.0, matching.distanceMeters ?: 0.0).takeIf { it > 0.0 },
                route = if (saved.route.size >= matching.route.size) saved.route else matching.route,
                laps = if (saved.laps.size >= matching.laps.size) saved.laps else matching.laps,
            )
        }
        val updated = (listOf(merged) + mutableHistory.value.filterNot { it == matching })
            .take(MAX_SESSIONS)
        mutableHistory.value = updated
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, encode(updated))
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
        mutableHistory.value = emptyList()
    }

    private fun downsample(samples: List<CompanionHeartRateSample>): List<CompanionHeartRateSample> {
        if (samples.size <= MAX_LOCAL_POINTS) return samples
        val stride = (samples.lastIndex.toFloat() / (MAX_LOCAL_POINTS - 1)).coerceAtLeast(1f)
        return (0 until MAX_LOCAL_POINTS).map { index -> samples[(index * stride).toInt().coerceAtMost(samples.lastIndex)] }
    }

    private fun encode(sessions: List<SavedWorkout>): String = JSONArray().apply {
        sessions.forEach { session ->
            put(JSONObject().apply {
                put("start", session.startedAtEpochMillis)
                put("end", session.endedAtEpochMillis)
                put("type", session.type)
                session.activeDurationMillis?.let { put("active_duration", it) }
                session.distanceMeters?.let { put("distance_m", it) }
                put("points", JSONArray().apply {
                    session.samples.forEach { sample ->
                        put(JSONObject().put("bpm", sample.bpm).put("time", sample.capturedAtEpochMillis))
                    }
                })
                put("route", JSONArray().apply {
                    session.route.forEach { point ->
                        put(JSONObject().apply {
                            put("lat", point.latitude)
                            put("lon", point.longitude)
                            put("alt", point.altitudeMeters ?: JSONObject.NULL)
                            put("accuracy", point.accuracyMeters.toDouble())
                            put("time", point.capturedAtEpochMillis)
                        })
                    }
                })
                put("laps", JSONArray().apply {
                    session.laps.forEach { lap ->
                        put(JSONObject().apply {
                            put("start", lap.startedAtEpochMillis)
                            put("end", lap.endedAtEpochMillis)
                            put("active_duration", lap.activeDurationMillis)
                            put("distance", lap.distanceMeters)
                        })
                    }
                })
            })
        }
    }.toString()

    private fun decode(encoded: String?): List<SavedWorkout> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val start = item.optLong("start")
                val end = item.optLong("end")
                if (start <= 0L || end <= start) return@mapNotNull null
                val points = item.optJSONArray("points") ?: JSONArray()
                val samples = (0 until points.length()).mapNotNull { pointIndex ->
                    val point = points.optJSONObject(pointIndex) ?: return@mapNotNull null
                    val bpm = point.optInt("bpm")
                    val time = point.optLong("time")
                    if (bpm in 30..220 && time in start..end) CompanionHeartRateSample(bpm, time) else null
                }
                val routeJson = item.optJSONArray("route") ?: JSONArray()
                val route = (0 until routeJson.length()).mapNotNull { pointIndex ->
                    val point = routeJson.optJSONObject(pointIndex) ?: return@mapNotNull null
                    val lat = point.optDouble("lat", Double.NaN)
                    val lon = point.optDouble("lon", Double.NaN)
                    val time = point.optLong("time")
                    if (!lat.isFinite() || !lon.isFinite() || time !in start..end) return@mapNotNull null
                    WorkoutLocationSample(
                        latitude = lat,
                        longitude = lon,
                        altitudeMeters = point.takeIf { !it.isNull("alt") }?.optDouble("alt"),
                        accuracyMeters = point.optDouble("accuracy", 0.0).toFloat(),
                        capturedAtEpochMillis = time,
                    )
                }
                val lapsJson = item.optJSONArray("laps") ?: JSONArray()
                val laps = (0 until lapsJson.length()).mapNotNull { lapIndex ->
                    val lap = lapsJson.optJSONObject(lapIndex) ?: return@mapNotNull null
                    val lapStart = lap.optLong("start")
                    val lapEnd = lap.optLong("end")
                    if (lapStart < start || lapEnd > end || lapEnd < lapStart) return@mapNotNull null
                    WorkoutLap(
                        startedAtEpochMillis = lapStart,
                        endedAtEpochMillis = lapEnd,
                        activeDurationMillis = lap.optLong("active_duration").coerceAtLeast(0L),
                        distanceMeters = lap.optDouble("distance", 0.0).coerceAtLeast(0.0),
                    )
                }.take(MAX_LAPS)
                SavedWorkout(
                    start,
                    end,
                    item.optString("type", "OTHER"),
                    samples,
                    item.optLong("active_duration").takeIf { it > 0L },
                    item.optDouble("distance_m").takeIf { it > 0.0 },
                    route,
                    laps,
                )
            }.take(MAX_SESSIONS)
        }.getOrElse { emptyList() }
    }

    private fun downsampleRoute(route: List<WorkoutLocationSample>): List<WorkoutLocationSample> {
        if (route.size <= MAX_ROUTE_POINTS) return route
        val stride = route.lastIndex.toDouble() / (MAX_ROUTE_POINTS - 1)
        return (0 until MAX_ROUTE_POINTS).map { route[(it * stride).toInt().coerceAtMost(route.lastIndex)] }
    }

    private const val MAX_ROUTE_POINTS = 1_500
    private const val MAX_LAPS = 500
}
