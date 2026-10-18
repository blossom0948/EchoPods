package me.kavishdevar.librepods.utils

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatterySummaryPolicyTest {
    @Test
    fun knownZeroIsNotMistakenForUnknown() {
        val summary = BatterySummaryPolicy.primaryEarbudBattery(
            listOf(
                Battery(BatteryComponent.LEFT, 0, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 61, BatteryStatus.NOT_CHARGING),
            ),
        )

        assertEquals(BatterySummary(level = 0, isStale = false), summary)
    }

    @Test
    fun cachedDisconnectedSampleIsMarkedStale() {
        val summary = BatterySummaryPolicy.primaryEarbudBattery(
            listOf(
                Battery(BatteryComponent.LEFT, 48, BatteryStatus.DISCONNECTED),
                Battery(BatteryComponent.RIGHT, 86, BatteryStatus.NOT_CHARGING),
            ),
        )

        assertEquals(BatterySummary(level = 48, isStale = true), summary)
    }

    @Test
    fun chargingCaseDoesNotOverrideEarbudSummary() {
        val summary = BatterySummaryPolicy.primaryEarbudBattery(
            listOf(
                Battery(BatteryComponent.LEFT, 57, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 73, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.CASE, 4, BatteryStatus.CHARGING),
            ),
        )

        assertEquals(BatterySummary(level = 57, isStale = false), summary)
    }

    @Test
    fun unknownOrCaseOnlySamplesDoNotBecomeEarbudSummary() {
        val summary = BatterySummaryPolicy.primaryEarbudBattery(
            listOf(
                Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED, isLevelKnown = false),
                Battery(BatteryComponent.CASE, 72, BatteryStatus.NOT_CHARGING),
            ),
        )

        assertNull(summary)
    }
}
