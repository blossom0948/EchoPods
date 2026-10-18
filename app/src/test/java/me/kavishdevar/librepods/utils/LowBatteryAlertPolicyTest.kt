package me.kavishdevar.librepods.utils

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowBatteryAlertPolicyTest {
    @Test
    fun alertsAtTwentyButOnlyOnceUntilFreshRecovery() {
        val battery = Battery(BatteryComponent.LEFT, 20, BatteryStatus.NOT_CHARGING)
        assertTrue(LowBatteryAlertPolicy.shouldAlert(battery, alreadyAlerted = false))
        assertFalse(LowBatteryAlertPolicy.shouldAlert(battery, alreadyAlerted = true))
        assertTrue(LowBatteryAlertPolicy.shouldRearm(Battery(BatteryComponent.LEFT, 21, BatteryStatus.NOT_CHARGING)))
    }

    @Test
    fun staleOrUnknownSamplesCannotTriggerOrRearmWarning() {
        val stale = Battery(BatteryComponent.CASE, 5, BatteryStatus.DISCONNECTED)
        val unknown = Battery(BatteryComponent.RIGHT, -1, BatteryStatus.NOT_CHARGING, isLevelKnown = false)
        assertFalse(LowBatteryAlertPolicy.shouldAlert(stale, alreadyAlerted = false))
        assertFalse(LowBatteryAlertPolicy.shouldRearm(stale))
        assertFalse(LowBatteryAlertPolicy.shouldAlert(unknown, alreadyAlerted = false))
        assertFalse(LowBatteryAlertPolicy.shouldRearm(unknown))
    }
}
