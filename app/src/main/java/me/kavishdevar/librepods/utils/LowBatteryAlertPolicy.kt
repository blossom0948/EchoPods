package me.kavishdevar.librepods.utils

import android.os.SystemClock
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryStatus

/** Thresholds are ordered from the first warning to the most severe one. */
object LowBatteryAlertPolicy {
    val thresholds: List<Int> = listOf(20, 10, 5, 4, 3, 2, 1, 0)

    /** Require a little recovery above 20% before arming a new discharge cycle. */
    const val REARM_LEVEL = 25

    fun isFresh(
        battery: Battery,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        maxAgeMs: Long = Battery.LEVEL_FRESHNESS_TTL_MS,
    ): Boolean {
        val age = nowElapsedMs - battery.levelCapturedAtElapsedMs
        return battery.isLevelKnown && battery.level in 0..100 &&
            battery.status != BatteryStatus.DISCONNECTED &&
            battery.levelCapturedAtElapsedMs > 0L && age in 0..maxAgeMs
    }

    /**
     * Return at most one newly crossed warning threshold for this component.
     * A skipped interval maps directly to its current, most relevant threshold;
     * e.g. moving from 6% to 4% emits 4%, not a burst of 5% and 4% alerts.
     */
    fun nextThresholdToAlert(
        battery: Battery,
        lastAlertedThreshold: Int?,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Int? {
        if (!isFresh(battery, nowElapsedMs)) return null
        val currentThreshold = thresholds.filter { battery.level <= it }.minOrNull() ?: return null
        return currentThreshold.takeIf { lastAlertedThreshold == null || it < lastAlertedThreshold }
    }

    fun shouldRearm(
        battery: Battery,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean = isFresh(battery, nowElapsedMs) && battery.level >= REARM_LEVEL
}
