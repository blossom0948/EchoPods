package me.kavishdevar.librepods.utils

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowBatteryAlertPolicyTest {
    private val now = 10_000L

    private fun sample(
        level: Int,
        component: Int = BatteryComponent.LEFT,
        status: Int = BatteryStatus.NOT_CHARGING,
        capturedAt: Long = now - 1_000L,
    ) = Battery(
        component = component,
        level = level,
        status = status,
        levelCapturedAtElapsedMs = capturedAt,
    )

    @Test
    fun emitsFirstTwentyPercentWarningOncePerThreshold() {
        assertNull(LowBatteryAlertPolicy.nextThresholdToAlert(sample(21), null, now))
        val atTwenty = sample(20)
        assertEquals(20, LowBatteryAlertPolicy.nextThresholdToAlert(atTwenty, null, now))
        assertNull(LowBatteryAlertPolicy.nextThresholdToAlert(atTwenty, 20, now))
        assertNull(LowBatteryAlertPolicy.nextThresholdToAlert(sample(18), 20, now))
        assertEquals(10, LowBatteryAlertPolicy.nextThresholdToAlert(sample(9), 20, now))
        assertNull(LowBatteryAlertPolicy.nextThresholdToAlert(sample(8), 10, now))
        assertEquals(10, LowBatteryAlertPolicy.nextThresholdToAlert(sample(6), 20, now))
        assertEquals(5, LowBatteryAlertPolicy.nextThresholdToAlert(sample(5), 10, now))
    }

    @Test
    fun skippedThresholdsEmitOnlyTheCurrentMostRelevantWarning() {
        assertEquals(4, LowBatteryAlertPolicy.nextThresholdToAlert(sample(4), 20, now))
        assertEquals(3, LowBatteryAlertPolicy.nextThresholdToAlert(sample(3), 4, now))
        assertEquals(0, LowBatteryAlertPolicy.nextThresholdToAlert(sample(0), 1, now))
        assertEquals(4, LowBatteryAlertPolicy.nextThresholdToAlert(sample(4), 5, now))
    }

    @Test
    fun zeroIsAValidBatteryLevelButUnknownAndStaleSamplesDoNotAlert() {
        assertEquals(0, LowBatteryAlertPolicy.nextThresholdToAlert(sample(0), null, now))
        assertNull(
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(-1).copy(isLevelKnown = false), null, now,
            ),
        )
        assertNull(
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(5, capturedAt = now - Battery.LEVEL_FRESHNESS_TTL_MS - 1), null, now,
            ),
        )
        assertNull(
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(5, status = BatteryStatus.DISCONNECTED), null, now,
            ),
        )
        assertNull(
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(5, capturedAt = 0L), null, now,
            ),
        )
    }

    @Test
    fun recoveryRequiresHysteresisAndAFreshLevelSample() {
        assertFalse(LowBatteryAlertPolicy.shouldRearm(sample(24), now))
        assertTrue(LowBatteryAlertPolicy.shouldRearm(sample(25), now))
        assertFalse(
            LowBatteryAlertPolicy.shouldRearm(
                sample(80, capturedAt = now - Battery.LEVEL_FRESHNESS_TTL_MS - 1), now,
            ),
        )
    }

    @Test
    fun eachComponentCanMaintainAnIndependentThreshold() {
        assertEquals(
            10,
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(9, component = BatteryComponent.LEFT), 20, now,
            ),
        )
        assertEquals(
            20,
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(18, component = BatteryComponent.RIGHT), null, now,
            ),
        )
        assertEquals(
            5,
            LowBatteryAlertPolicy.nextThresholdToAlert(
                sample(5, component = BatteryComponent.CASE), null, now,
            ),
        )
    }
}
