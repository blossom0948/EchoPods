/* SPDX-License-Identifier: GPL-3.0-or-later */
package me.kavishdevar.librepods.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.kavishdevar.librepods.bluetooth.HeartRateStreamState
import me.kavishdevar.librepods.bluetooth.HeartRateWorkoutSummary
import me.kavishdevar.librepods.data.Capability

internal object HealthWorkoutBridge {
    const val HEALTH_APP_PACKAGE = "com.galaxyairpods.health"
    const val CONTROL_PERMISSION = "com.galaxyairpods.permission.CONTROL_HEART_RATE"
    const val RECEIVE_PERMISSION = CONTROL_PERMISSION

    const val START_WORKOUT = "com.galaxyairpods.health.action.START_WORKOUT"
    const val STOP_WORKOUT = "com.galaxyairpods.health.action.STOP_WORKOUT"
    const val QUERY_STATE = "com.galaxyairpods.health.action.QUERY_STATE"
    const val STATE = "com.galaxyairpods.health.action.STATE"
    const val WORKOUT_FINISHED = "com.galaxyairpods.health.action.WORKOUT_FINISHED"
    const val QUERY_APPEARANCE = "com.galaxyairpods.health.action.QUERY_APPEARANCE"
    const val APPEARANCE = "com.galaxyairpods.health.action.APPEARANCE"

    const val EXTRA_WORKOUT_TYPE = "workout_type"
    const val EXTRA_APPEARANCE_MATERIAL = "appearance_material"
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

    fun sendState(context: Context, state: HeartRateStreamState, message: String? = null) {
        val intent = Intent(STATE)
            .setPackage(HEALTH_APP_PACKAGE)
            .putExtra(EXTRA_STATUS, state.status.name)
            .putExtra(EXTRA_BPM, state.bpm ?: -1)
            .putExtra(EXTRA_CAPTURED_AT, state.sampledAtEpochMillis ?: 0L)
            .putExtra(EXTRA_STARTED_AT, state.workoutStartedAtEpochMillis ?: 0L)
            .putExtra(EXTRA_SAMPLE_COUNT, state.sampleCount)
            .putExtra(EXTRA_AVERAGE_BPM, state.averageBpm ?: -1)
            .putExtra(EXTRA_MAX_BPM, state.maxBpm ?: -1)
            .putExtra(EXTRA_SAMPLE_VALUES, state.recentSamples.map { it.bpm }.toIntArray())
            .putExtra(EXTRA_SAMPLE_TIMES, state.recentSamples.map { it.capturedAtEpochMillis }.toLongArray())
        if (message != null) intent.putExtra(EXTRA_MESSAGE, message)
        context.sendBroadcast(intent, RECEIVE_PERMISSION)
    }

    fun sendAppearance(context: Context, useMaterial: Boolean) {
        context.sendBroadcast(
            Intent(APPEARANCE)
                .setPackage(HEALTH_APP_PACKAGE)
                .putExtra(EXTRA_APPEARANCE_MATERIAL, useMaterial),
            RECEIVE_PERMISSION,
        )
    }

    fun sendFinished(context: Context, summary: HeartRateWorkoutSummary) {
        val intent = Intent(WORKOUT_FINISHED)
            .setPackage(HEALTH_APP_PACKAGE)
            .putExtra(EXTRA_STARTED_AT, summary.startedAtEpochMillis)
            .putExtra(EXTRA_CAPTURED_AT, summary.endedAtEpochMillis)
            .putExtra(EXTRA_SAMPLE_COUNT, summary.samples.size)
            .putExtra(EXTRA_AVERAGE_BPM, summary.averageBpm ?: -1)
            .putExtra(EXTRA_MAX_BPM, summary.maxBpm ?: -1)
            .putExtra(EXTRA_WORKOUT_TYPE, summary.workoutType)
            .putExtra(EXTRA_SAMPLE_VALUES, summary.samples.map { it.bpm }.toIntArray())
            .putExtra(EXTRA_SAMPLE_TIMES, summary.samples.map { it.capturedAtEpochMillis }.toLongArray())
        context.sendBroadcast(intent, RECEIVE_PERMISSION)
    }
}

/** Receives requests only from the separately signed Echo Health companion. */
class HealthWorkoutCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == HealthWorkoutBridge.QUERY_APPEARANCE) {
            val useMaterial = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("m3e_enabled", false)
            HealthWorkoutBridge.sendAppearance(context, useMaterial)
            return
        }

        val service = ServiceManager.getService()
        if (service == null) {
            HealthWorkoutBridge.sendState(
                context,
                HeartRateStreamState(),
                "EchoPods is not connected. Open EchoPods and connect AirPods first.",
            )
            return
        }

        when (intent.action) {
            HealthWorkoutBridge.QUERY_STATE -> HealthWorkoutBridge.sendState(
                context,
                service.aacpManager.heartRateState.value,
            )

            HealthWorkoutBridge.START_WORKOUT -> {
                val supportsHeartRate = service.airpodsInstance?.model?.capabilities
                    ?.contains(Capability.HRM) == true
                if (!supportsHeartRate) {
                    HealthWorkoutBridge.sendState(
                        context,
                        service.aacpManager.heartRateState.value,
                        "Heart rate measurement requires supported AirPods Pro 3 hardware.",
                    )
                } else if (!service.aacpManager.startHeartRateWorkout(
                        intent.getStringExtra(HealthWorkoutBridge.EXTRA_WORKOUT_TYPE) ?: "OTHER",
                    )) {
                    HealthWorkoutBridge.sendState(
                        context,
                        service.aacpManager.heartRateState.value,
                        "AirPods are not connected to EchoPods right now.",
                    )
                } else {
                    HealthWorkoutBridge.sendState(context, service.aacpManager.heartRateState.value)
                }
            }

            HealthWorkoutBridge.STOP_WORKOUT -> {
                val summary = service.aacpManager.finishHeartRateWorkout()
                if (summary == null) {
                    HealthWorkoutBridge.sendState(
                        context,
                        service.aacpManager.heartRateState.value,
                        "There is no active EchoPods workout.",
                    )
                }
            }
        }
    }
}
