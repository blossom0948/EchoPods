package me.kavishdevar.librepods.data

import java.util.Locale

/** The single presentation mode used for connection and case-open popups. */
enum class ConnectionPopupStyle(val preferenceValue: String) {
    BOTTOM_SHEET("BOTTOM_SHEET"),
    DYNAMIC_ISLAND("DYNAMIC_ISLAND");

    companion object {
        fun fromPreferences(
            storedValue: String?,
            legacyBottomSheetEnabled: Boolean = true,
            legacyIslandEnabled: Boolean = true
        ): ConnectionPopupStyle = when (storedValue?.uppercase(Locale.ROOT)) {
            BOTTOM_SHEET.preferenceValue -> BOTTOM_SHEET
            DYNAMIC_ISLAND.preferenceValue -> DYNAMIC_ISLAND
            // Migrate an old single-choice configuration. If both old toggles were on,
            // prefer the bottom card so first launch cannot show two popups.
            else -> if (legacyIslandEnabled && !legacyBottomSheetEnabled) {
                DYNAMIC_ISLAND
            } else {
                BOTTOM_SHEET
            }
        }
    }
}
