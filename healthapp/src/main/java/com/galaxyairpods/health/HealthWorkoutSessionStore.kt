package com.galaxyairpods.health

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveHealthWorkout(
    val startedAtEpochMillis: Long,
    val workoutType: String,
    val pausedAtEpochMillis: Long? = null,
    val accumulatedPauseMillis: Long = 0L,
    val pauseIntervals: List<WorkoutPauseInterval> = emptyList(),
)

data class WorkoutPauseInterval(val startedAtEpochMillis: Long, val endedAtEpochMillis: Long)

/** Keeps the exercise timer local so workouts remain recordable without AirPods HR support. */
object HealthWorkoutSessionStore {
    private const val PREFS = "health_workout_session"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_TYPE = "workout_type"
    private const val KEY_PAUSED_AT = "paused_at"
    private const val KEY_ACCUMULATED_PAUSE = "accumulated_pause"
    private const val KEY_PAUSE_INTERVALS = "pause_intervals"

    private val mutableSession = MutableStateFlow<ActiveHealthWorkout?>(null)
    val session = mutableSession.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val startedAt = preferences.getLong(KEY_STARTED_AT, 0L)
        val type = preferences.getString(KEY_TYPE, null)
        mutableSession.value = if (startedAt > 0L && type != null) {
            ActiveHealthWorkout(
                startedAtEpochMillis = startedAt,
                workoutType = type,
                pausedAtEpochMillis = preferences.getLong(KEY_PAUSED_AT, 0L).takeIf { it > 0L },
                accumulatedPauseMillis = preferences.getLong(KEY_ACCUMULATED_PAUSE, 0L).coerceAtLeast(0L),
                pauseIntervals = decodePauseIntervals(preferences.getString(KEY_PAUSE_INTERVALS, null)),
            )
        } else {
            null
        }
    }

    @Synchronized
    fun start(context: Context, workoutType: String, now: Long = System.currentTimeMillis()): Boolean {
        if (mutableSession.value != null) return false
        val next = ActiveHealthWorkout(now, workoutType)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STARTED_AT, next.startedAtEpochMillis)
            .putString(KEY_TYPE, next.workoutType)
            .remove(KEY_PAUSED_AT)
            .remove(KEY_ACCUMULATED_PAUSE)
            .remove(KEY_PAUSE_INTERVALS)
            .apply()
        mutableSession.value = next
        return true
    }

    @Synchronized
    fun pause(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val current = mutableSession.value ?: return false
        if (current.pausedAtEpochMillis != null) return false
        val next = current.copy(pausedAtEpochMillis = now.coerceAtLeast(current.startedAtEpochMillis))
        persist(context, next)
        mutableSession.value = next
        return true
    }

    @Synchronized
    fun resume(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val current = mutableSession.value ?: return false
        val pausedAt = current.pausedAtEpochMillis ?: return false
        val resumedAt = now.coerceAtLeast(pausedAt)
        val next = current.copy(
            pausedAtEpochMillis = null,
            accumulatedPauseMillis = current.accumulatedPauseMillis + (resumedAt - pausedAt),
            pauseIntervals = current.pauseIntervals + WorkoutPauseInterval(pausedAt, resumedAt),
        )
        persist(context, next)
        mutableSession.value = next
        return true
    }

    fun elapsedMillis(active: ActiveHealthWorkout, now: Long = System.currentTimeMillis()): Long {
        val effectiveEnd = active.pausedAtEpochMillis ?: now
        return (effectiveEnd - active.startedAtEpochMillis - active.accumulatedPauseMillis).coerceAtLeast(0L)
    }

    @Synchronized
    fun finish(
        context: Context,
        samples: List<CompanionHeartRateSample>,
        endedAt: Long = System.currentTimeMillis(),
        distanceMeters: Double? = null,
        route: List<WorkoutLocationSample> = emptyList(),
        laps: List<WorkoutLap> = emptyList(),
    ): CompanionWorkoutSummary? {
        val active = mutableSession.value ?: return null
        clear(context)
        val safeEnd = endedAt.coerceAtLeast(active.startedAtEpochMillis + 1L)
        val validSamples = samples
            .filter {
                it.bpm in 30..220 &&
                    it.capturedAtEpochMillis in active.startedAtEpochMillis until safeEnd &&
                    active.pauseIntervals.none { interval -> it.capturedAtEpochMillis in interval.startedAtEpochMillis until interval.endedAtEpochMillis } &&
                    (active.pausedAtEpochMillis == null || it.capturedAtEpochMillis < active.pausedAtEpochMillis)
            }
            .distinctBy { it.capturedAtEpochMillis }
            .sortedBy { it.capturedAtEpochMillis }
        return CompanionWorkoutSummary(
            startedAtEpochMillis = active.startedAtEpochMillis,
            endedAtEpochMillis = safeEnd,
            samples = validSamples,
            workoutType = active.workoutType,
            activeDurationMillis = elapsedMillis(active, safeEnd),
            distanceMeters = distanceMeters?.takeIf { it.isFinite() && it > 0.0 },
            route = route,
            laps = laps,
        )
    }

    @Synchronized
    fun completeFromEchoPods(context: Context, summary: CompanionWorkoutSummary) {
        val localStart = mutableSession.value?.startedAtEpochMillis ?: return
        if (kotlin.math.abs(localStart - summary.startedAtEpochMillis) <= HOST_START_ALIGNMENT_WINDOW_MS) clear(context)
    }

    @Synchronized
    fun alignStartFromEchoPods(context: Context, remoteStartedAtEpochMillis: Long) {
        val current = mutableSession.value ?: return
        if (remoteStartedAtEpochMillis <= 0L ||
            kotlin.math.abs(current.startedAtEpochMillis - remoteStartedAtEpochMillis) > HOST_START_ALIGNMENT_WINDOW_MS
        ) return
        val aligned = current.copy(startedAtEpochMillis = remoteStartedAtEpochMillis)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STARTED_AT, remoteStartedAtEpochMillis)
            .apply()
        mutableSession.value = aligned
    }

    private fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_STARTED_AT)
            .remove(KEY_TYPE)
            .remove(KEY_PAUSED_AT)
            .remove(KEY_ACCUMULATED_PAUSE)
            .remove(KEY_PAUSE_INTERVALS)
            .apply()
        mutableSession.value = null
    }

    private fun persist(context: Context, session: ActiveHealthWorkout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STARTED_AT, session.startedAtEpochMillis)
            .putString(KEY_TYPE, session.workoutType)
            .putLong(KEY_PAUSED_AT, session.pausedAtEpochMillis ?: 0L)
            .putLong(KEY_ACCUMULATED_PAUSE, session.accumulatedPauseMillis)
            .putString(KEY_PAUSE_INTERVALS, encodePauseIntervals(session.pauseIntervals))
            .apply()
    }

    private fun encodePauseIntervals(intervals: List<WorkoutPauseInterval>): String =
        org.json.JSONArray().apply {
            intervals.forEach { interval ->
                put(org.json.JSONArray().put(interval.startedAtEpochMillis).put(interval.endedAtEpochMillis))
            }
        }.toString()

    private fun decodePauseIntervals(encoded: String?): List<WorkoutPauseInterval> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(encoded)
            (0 until array.length()).mapNotNull { index ->
                val pair = array.optJSONArray(index) ?: return@mapNotNull null
                val start = pair.optLong(0)
                val end = pair.optLong(1)
                if (start > 0L && end >= start) WorkoutPauseInterval(start, end) else null
            }
        }.getOrDefault(emptyList())
    }

    private const val HOST_START_ALIGNMENT_WINDOW_MS = 10_000L
}
