package me.kavishdevar.librepods.utils

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus

data class BatterySummary(val level: Int, val isStale: Boolean)

object BatterySummaryPolicy {
    /** A popup summary uses only known earbud samples; a known 0% remains a real value. */
    fun primaryEarbudBattery(batteries: List<Battery>): BatterySummary? {
        val known = batteries.filter {
            (it.component == BatteryComponent.LEFT || it.component == BatteryComponent.RIGHT) &&
                it.isLevelKnown && it.level in 0..100
        }
        if (known.isEmpty()) return null
        return BatterySummary(
            level = known.minOf { it.level },
            isStale = known.any { it.status == BatteryStatus.DISCONNECTED },
        )
    }
}
