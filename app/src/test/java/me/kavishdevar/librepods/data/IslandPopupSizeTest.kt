package me.kavishdevar.librepods.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPopupSizeTest {
    @Test
    fun unknownPreferenceDefaultsToSmallAndSliderSnapsToThreeChoices() {
        assertEquals(IslandPopupSize.SMALL, IslandPopupSize.fromPreference(null))
        assertEquals(IslandPopupSize.SMALL, IslandPopupSize.fromPreference("unknown"))
        assertEquals(IslandPopupSize.SMALL, IslandPopupSize.fromSliderPosition(0.2f))
        assertEquals(IslandPopupSize.MEDIUM, IslandPopupSize.fromSliderPosition(1.1f))
        assertEquals(IslandPopupSize.LARGE, IslandPopupSize.fromSliderPosition(2f))
    }

    @Test
    fun sizePresetsIncreaseWithinDeviceWidth() {
        val widths = IslandPopupSize.entries.map { it.widthPx(screenWidthPx = 900, density = 2.625f) }
        assertTrue(widths[0] < widths[1])
        assertTrue(widths[1] < widths[2])
        assertTrue(widths.all { it <= 900 })
    }

    @Test
    fun disabledConnectionPopupPreferenceIsRestored() {
        assertEquals(
            ConnectionPopupStyle.DISABLED,
            ConnectionPopupStyle.fromPreferences("DISABLED"),
        )
    }
}
