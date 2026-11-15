package com.galaxyairpods.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class CompanionWorkoutSummary(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val samples: List<CompanionHeartRateSample>,
    val workoutType: String = "OTHER",
    val activeDurationMillis: Long? = null,
    val distanceMeters: Double? = null,
    val route: List<WorkoutLocationSample> = emptyList(),
    val laps: List<WorkoutLap> = emptyList(),
) {
    val averageBpm: Int? get() = samples.takeIf { it.isNotEmpty() }?.sumOf { it.bpm }?.div(samples.size)
    val maxBpm: Int? get() = samples.maxOfOrNull { it.bpm }
}

data class CompanionHeartRateSample(val bpm: Int, val capturedAtEpochMillis: Long)

data class CompanionWorkoutState(
    val status: String = "DISCONNECTED",
    val bpm: Int? = null,
    val capturedAtEpochMillis: Long? = null,
    val startedAtEpochMillis: Long? = null,
    val sampleCount: Int = 0,
    val averageBpm: Int? = null,
    val maxBpm: Int? = null,
    val recentSamples: List<CompanionHeartRateSample> = emptyList(),
    val message: String? = null,
    val lastWorkout: CompanionWorkoutSummary? = null,
    val healthRecordSaved: Boolean = false,
    val useMaterialAppearance: Boolean = false,
)

object CompanionWorkoutStateHolder {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(CompanionWorkoutState())
    val state = _state
    private val healthWriteInProgress = AtomicBoolean(false)

    fun update(state: CompanionWorkoutState) { _state.value = state }
    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(HealthWorkoutBridgeReceiver.PREFS, Context.MODE_PRIVATE)
        _state.value = _state.value.copy(
            useMaterialAppearance = preferences.getBoolean(HealthWorkoutBridgeReceiver.PREF_MATERIAL_APPEARANCE, false),
        )
        WorkoutHistoryStore.initialize(context)
    }
    fun updateAppearance(context: Context, useMaterial: Boolean) {
        context.getSharedPreferences(HealthWorkoutBridgeReceiver.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(HealthWorkoutBridgeReceiver.PREF_MATERIAL_APPEARANCE, useMaterial).apply()
        _state.value = _state.value.copy(useMaterialAppearance = useMaterial)
    }
    fun reportHealthSaved(success: Boolean, message: String? = null) {
        _state.value = _state.value.copy(
            healthRecordSaved = success,
            message = message ?: if (success) "Saved to Health Connect." else _state.value.message,
        )
    }
    fun beginHealthWrite() = healthWriteInProgress.compareAndSet(false, true)
    fun finishHealthWrite() { healthWriteInProgress.set(false) }
}

/** Receives the authenticated, explicit-Package sample stream from EchoPods. */
class HealthWorkoutBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_APPEARANCE -> {
                CompanionWorkoutStateHolder.updateAppearance(
                    context,
                    intent.getBooleanExtra(EXTRA_APPEARANCE_MATERIAL, false),
                )
            }

            ACTION_STATE -> {
                HealthWorkoutSessionStore.alignStartFromEchoPods(
                    context,
                    intent.getLongExtra(EXTRA_STARTED_AT, 0L),
                )
                val current = CompanionWorkoutStateHolder.state.value
                CompanionWorkoutStateHolder.update(
                    current.copy(
                        status = intent.getStringExtra(EXTRA_STATUS) ?: current.status,
                        bpm = intent.getIntExtra(EXTRA_BPM, -1).takeIf { it > 0 },
                        capturedAtEpochMillis = intent.getLongExtra(EXTRA_CAPTURED_AT, 0L).takeIf { it > 0 },
                        startedAtEpochMillis = intent.getLongExtra(EXTRA_STARTED_AT, 0L).takeIf { it > 0 },
                        sampleCount = intent.getIntExtra(EXTRA_SAMPLE_COUNT, current.sampleCount),
                        averageBpm = intent.getIntExtra(EXTRA_AVERAGE_BPM, -1).takeIf { it > 0 },
                        maxBpm = intent.getIntExtra(EXTRA_MAX_BPM, -1).takeIf { it > 0 },
                        recentSamples = readRecentSamples(intent),
                        message = intent.getStringExtra(EXTRA_MESSAGE),
                    ),
                )
            }

            ACTION_WORKOUT_FINISHED -> {
                val summary = readSummary(intent) ?: return
                HealthWorkoutSessionStore.completeFromEchoPods(context, summary)
                CompanionWorkoutStateHolder.update(
                    CompanionWorkoutState(
                        status = "OFF",
                        sampleCount = summary.samples.size,
                        averageBpm = summary.averageBpm,
                        maxBpm = summary.maxBpm,
                        message = if (summary.samples.isEmpty()) "No heart-rate samples were received." else null,
                        lastWorkout = summary,
                        useMaterialAppearance = CompanionWorkoutStateHolder.state.value.useMaterialAppearance,
                    ),
                )
                WorkoutHistoryStore.append(context, summary)
                val shouldSave = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(PREF_SAVE_HEALTH_CONNECT, false)
                if (shouldSave && CompanionWorkoutStateHolder.beginHealthWrite()) {
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            HealthConnectWorkoutWriter(context).write(summary)
                            CompanionWorkoutStateHolder.reportHealthSaved(true)
                        } catch (error: Exception) {
                            CompanionWorkoutStateHolder.reportHealthSaved(
                                false,
                                "Health Connect save failed. Check its permissions and try again.",
                            )
                        } finally {
                            CompanionWorkoutStateHolder.finishHealthWrite()
                            pending.finish()
                        }
                    }
                }
            }
        }
    }

    private fun readSummary(intent: Intent): CompanionWorkoutSummary? {
        val values = intent.getIntArrayExtra(EXTRA_SAMPLE_VALUES) ?: return null
        val times = intent.getLongArrayExtra(EXTRA_SAMPLE_TIMES) ?: return null
        if (values.size != times.size || values.size > MAX_SAMPLES) return null
        val samples = values.indices.mapNotNull { index ->
            val bpm = values[index]
            val time = times[index]
            if (bpm in 30..220 && time > 0) CompanionHeartRateSample(bpm, time) else null
        }
        return CompanionWorkoutSummary(
            startedAtEpochMillis = intent.getLongExtra(EXTRA_STARTED_AT, 0L),
            endedAtEpochMillis = intent.getLongExtra(EXTRA_CAPTURED_AT, 0L),
            samples = samples,
            workoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "OTHER",
        ).takeIf { it.startedAtEpochMillis > 0 && it.endedAtEpochMillis > it.startedAtEpochMillis }
    }

    private fun readRecentSamples(intent: Intent): List<CompanionHeartRateSample> {
        val values = intent.getIntArrayExtra(EXTRA_SAMPLE_VALUES) ?: return emptyList()
        val times = intent.getLongArrayExtra(EXTRA_SAMPLE_TIMES) ?: return emptyList()
        if (values.size != times.size || values.size > MAX_SAMPLES) return emptyList()
        return values.indices.mapNotNull { index ->
            val bpm = values[index]
            val time = times[index]
            if (bpm in 30..220 && time > 0) CompanionHeartRateSample(bpm, time) else null
        }
    }

    companion object {
        const val ACTION_STATE = "com.galaxyairpods.health.action.STATE"
        const val ACTION_WORKOUT_FINISHED = "com.galaxyairpods.health.action.WORKOUT_FINISHED"
        const val ACTION_START_WORKOUT = "com.galaxyairpods.health.action.START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.galaxyairpods.health.action.STOP_WORKOUT"
        const val ACTION_QUERY_STATE = "com.galaxyairpods.health.action.QUERY_STATE"
        const val ACTION_APPEARANCE = "com.galaxyairpods.health.action.APPEARANCE"
        const val ACTION_QUERY_APPEARANCE = "com.galaxyairpods.health.action.QUERY_APPEARANCE"
        const val EXTRA_APPEARANCE_MATERIAL = "appearance_material"
        const val EXTRA_WORKOUT_TYPE = "workout_type"
        const val EXTRA_STATUS = "status"
        const val EXTRA_BPM = "bpm"
        const val EXTRA_CAPTURED_AT = "captured_at"
        const val EXTRA_STARTED_AT = "started_at"
        const val EXTRA_SAMPLE_COUNT = "sample_count"
        const val EXTRA_AVERAGE_BPM = "average_bpm"
        const val EXTRA_MAX_BPM = "max_bpm"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SAMPLE_VALUES = "sample_values"
        const val EXTRA_SAMPLE_TIMES = "sample_times"
        const val CONTROL_PERMISSION = "com.galaxyairpods.permission.CONTROL_HEART_RATE"
        const val ECHOPODS_PACKAGE = "com.galaxyairpods"
        const val PREFS = "health_settings"
        const val PREF_SAVE_HEALTH_CONNECT = "save_to_health_connect"
        const val PREF_MATERIAL_APPEARANCE = "appearance_material"
        private const val MAX_SAMPLES = 8 * 60 * 60
    }
}
