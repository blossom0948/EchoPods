package me.kavishdevar.librepods.utils

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryStatus

/** Low-battery warnings must only be driven by a live sample, never restored cache. */
object LowBatteryAlertPolicy {
    const val ALERT_THRESHOLD = 20

    fun isFresh(battery: Battery): Boolean =
        battery.isLevelKnown && battery.level in 0..100 &&
            battery.status != BatteryStatus.DISCONNECTED

    fun isLow(battery: Battery): Boolean =
        isFresh(battery) && battery.level <= ALERT_THRESHOLD

    fun shouldAlert(battery: Battery, alreadyAlerted: Boolean): Boolean =
        isLow(battery) && !alreadyAlerted

    fun shouldRearm(battery: Battery): Boolean =
        isFresh(battery) && battery.level > ALERT_THRESHOLD
}
