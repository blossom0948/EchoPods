package me.kavishdevar.librepods.presentation.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryWidgetRingPolicyTest {
    @Test
    fun twentyPercentIsLowAndTwentyOnePercentIsNormal() {
        assertEquals(72f, BatteryWidgetRingPolicy.style(20).sweepDegrees, 0.001f)
        assertEquals(BatteryWidgetRingPolicy.LOW_RED, BatteryWidgetRingPolicy.style(20).progressColor)
        assertEquals(BatteryWidgetRingPolicy.NORMAL_GREEN, BatteryWidgetRingPolicy.style(21).progressColor)
    }

    @Test
    fun emptyAndFullBatteryHaveExactArcLengths() {
        assertEquals(0f, BatteryWidgetRingPolicy.style(0).sweepDegrees, 0.001f)
        assertEquals(360f, BatteryWidgetRingPolicy.style(100).sweepDegrees, 0.001f)
    }

    @Test
    fun missingOrInvalidBatteryDoesNotBecomeZeroPercent() {
        assertEquals(BatteryWidgetRingStyle(0f, null), BatteryWidgetRingPolicy.style(null))
        assertEquals(BatteryWidgetRingStyle(0f, null), BatteryWidgetRingPolicy.style(-1))
        assertEquals(BatteryWidgetRingStyle(0f, null), BatteryWidgetRingPolicy.style(101))
    }

    @Test
    fun previewFixtureUsesTheRequestedIndependentComponentValues() {
        val preview = BatteryWidgetState(
            phone = WidgetBatteryComponent(level = 95),
            left = WidgetBatteryComponent(level = 15, charging = true),
            right = WidgetBatteryComponent(level = 90),
            case = WidgetBatteryComponent(level = 85),
        )

        assertEquals(95, preview.phone.level)
        assertEquals(90, preview.right.level)
        assertEquals(15, preview.left.level)
        assertEquals(true, preview.left.charging)
        assertEquals(85, preview.case.level)
        assertEquals(BatteryWidgetRingPolicy.LOW_RED, BatteryWidgetRingPolicy.style(preview.left.level).progressColor)
    }
}
