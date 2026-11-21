package me.kavishdevar.librepods.data

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.roundToInt

data class AirPodsBatterySample(
    val component: Int,
    val level: Int,
    val status: Int,
    val recordedAtMillis: Long,
)

data class AirPodsRuntimeEstimate(
    val minutesRemaining: Int,
    val observedDropPercent: Int,
    val sampleCount: Int,
)

/** Locally stores observed AirPods charge changes; it does not infer battery capacity or health. */
object AirPodsBatteryHistoryStore {
    private const val PREFS_NAME = "airpods_battery_history"
    private const val KEY_PREFIX = "history_"
    private const val MAX_HISTORY_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MIN_REPEAT_SAMPLE_INTERVAL_MS = 15L * 60 * 1000
    private const val MAX_SAMPLES = 7_500
    private const val ESTIMATE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private val lock = Any()

    fun record(
        context: Context,
        deviceAddress: String?,
        modelNumber: String?,
        batteries: List<Battery>,
        nowMillis: Long = System.currentTimeMillis(),
        nowElapsedMillis: Long = SystemClock.elapsedRealtime(),
    ) {
        val identity = historyKey(deviceAddress, modelNumber) ?: return
        val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            val samples = read(preferences.getString(identity, null)).toMutableList()
            val latestByComponent = samples.groupBy { it.component }.mapValues { (_, values) ->
                values.maxByOrNull { it.recordedAtMillis }
            }
            var changed = false

            batteries.forEach { battery ->
                val sampleAge = nowElapsedMillis - battery.levelCapturedAtElapsedMs
                if (!battery.isLevelKnown || battery.level !in 0..100 ||
                    battery.levelCapturedAtElapsedMs <= 0L ||
                    sampleAge !in 0L..Battery.LEVEL_FRESHNESS_TTL_MS ||
                    battery.status !in setOf(
                        BatteryStatus.CHARGING,
                        BatteryStatus.NOT_CHARGING,
                        BatteryStatus.OPTIMIZED_CHARGING,
                    )
                ) return@forEach

                val previous = latestByComponent[battery.component]
                if (previous != null && previous.level == battery.level && previous.status == battery.status &&
                    nowMillis - previous.recordedAtMillis < MIN_REPEAT_SAMPLE_INTERVAL_MS
                ) return@forEach

                samples += AirPodsBatterySample(
                    component = battery.component,
                    level = battery.level,
                    status = battery.status,
                    recordedAtMillis = nowMillis,
                )
                changed = true
            }

            if (!changed) return
            val retained = samples
                .filter { it.recordedAtMillis in (nowMillis - MAX_HISTORY_AGE_MS)..nowMillis }
                .sortedBy { it.recordedAtMillis }
                .takeLast(MAX_SAMPLES)
            preferences.edit().putString(identity, encode(retained)).apply()
        }
    }

    fun load(
        context: Context,
        deviceAddress: String?,
        modelNumber: String?,
    ): List<AirPodsBatterySample> {
        val identity = historyKey(deviceAddress, modelNumber) ?: return emptyList()
        val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return synchronized(lock) {
            read(preferences.getString(identity, null))
                .filter { it.recordedAtMillis >= System.currentTimeMillis() - MAX_HISTORY_AGE_MS }
                .sortedBy { it.recordedAtMillis }
        }
    }

    fun clear(context: Context, deviceAddress: String?, modelNumber: String?) {
        val identity = historyKey(deviceAddress, modelNumber) ?: return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(identity)
            .apply()
    }

    fun estimateRemainingMinutes(
        samples: List<AirPodsBatterySample>,
        component: Int,
        currentLevel: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): AirPodsRuntimeEstimate? {
        if (currentLevel !in 1..100) return null
        val observations = samples
            .asSequence()
            .filter { it.component == component && it.status == BatteryStatus.NOT_CHARGING }
            .filter { it.recordedAtMillis in (nowMillis - ESTIMATE_WINDOW_MS)..nowMillis }
            .sortedBy { it.recordedAtMillis }
            .toList()

        val dischargeIntervals = observations.zipWithNext().mapNotNull { (before, after) ->
            val elapsed = after.recordedAtMillis - before.recordedAtMillis
            val drop = before.level - after.level
            if (elapsed !in 5 * 60_000L..6 * 60 * 60_000L || drop !in 1..30) return@mapNotNull null
            (elapsed / 60_000.0 / drop).takeIf { it in 1.0..90.0 }?.let { it to drop }
        }

        val observedDrop = dischargeIntervals.sumOf { it.second }
        if (dischargeIntervals.size < 2 || observedDrop < 4) return null

        val minutesPerPercent = dischargeIntervals.map { it.first }.sorted()
        val medianRate = if (minutesPerPercent.size % 2 == 0) {
            (minutesPerPercent[minutesPerPercent.size / 2 - 1] +
                minutesPerPercent[minutesPerPercent.size / 2]) / 2.0
        } else {
            minutesPerPercent[minutesPerPercent.size / 2]
        }
        val estimate = (medianRate * currentLevel).roundToInt().coerceAtLeast(1)
        return AirPodsRuntimeEstimate(
            minutesRemaining = estimate,
            observedDropPercent = observedDrop,
            sampleCount = observations.size,
        )
    }

    private fun historyKey(deviceAddress: String?, modelNumber: String?): String? {
        val address = deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val model = modelNumber?.trim().orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$address|$model".toByteArray())
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte) }
        return KEY_PREFIX + digest
    }

    private fun read(raw: String?): List<AirPodsBatterySample> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val entry = json.optJSONObject(index) ?: continue
                    val component = entry.optInt("component", -1)
                    val level = entry.optInt("level", -1)
                    val status = entry.optInt("status", -1)
                    val time = entry.optLong("time", -1L)
                    if (component in setOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE) &&
                        level in 0..100 &&
                        status in setOf(BatteryStatus.CHARGING, BatteryStatus.NOT_CHARGING, BatteryStatus.OPTIMIZED_CHARGING) &&
                        time > 0L
                    ) {
                        add(AirPodsBatterySample(component, level, status, time))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(samples: List<AirPodsBatterySample>): String = JSONArray().apply {
        samples.forEach { sample ->
            put(
                JSONObject()
                    .put("component", sample.component)
                    .put("level", sample.level)
                    .put("status", sample.status)
                    .put("time", sample.recordedAtMillis),
            )
        }
    }.toString()
}
