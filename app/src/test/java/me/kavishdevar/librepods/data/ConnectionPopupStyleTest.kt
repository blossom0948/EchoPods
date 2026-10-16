package me.kavishdevar.librepods.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionPopupStyleTest {
    @Test
    fun storedStyleWinsOverLegacyFlags() {
        assertEquals(
            ConnectionPopupStyle.DYNAMIC_ISLAND,
            ConnectionPopupStyle.fromPreferences("dynamic_island", true, true)
        )
        assertEquals(
            ConnectionPopupStyle.BOTTOM_SHEET,
            ConnectionPopupStyle.fromPreferences("BOTTOM_SHEET", false, true)
        )
    }

    @Test
    fun legacySingleEnabledStyleIsMigrated() {
        assertEquals(
            ConnectionPopupStyle.DYNAMIC_ISLAND,
            ConnectionPopupStyle.fromPreferences(null, legacyBottomSheetEnabled = false, legacyIslandEnabled = true)
        )
        assertEquals(
            ConnectionPopupStyle.BOTTOM_SHEET,
            ConnectionPopupStyle.fromPreferences(null, legacyBottomSheetEnabled = true, legacyIslandEnabled = false)
        )
    }

    @Test
    fun ambiguousOrInvalidLegacyPreferenceDefaultsToOnePopup() {
        assertEquals(
            ConnectionPopupStyle.BOTTOM_SHEET,
            ConnectionPopupStyle.fromPreferences(null, legacyBottomSheetEnabled = true, legacyIslandEnabled = true)
        )
        assertEquals(
            ConnectionPopupStyle.BOTTOM_SHEET,
            ConnectionPopupStyle.fromPreferences("unknown", legacyBottomSheetEnabled = false, legacyIslandEnabled = false)
        )
    }
}
