package me.kavishdevar.librepods.data

import java.util.Locale

/** The single presentation mode used for connection and case-open popups. */
enum class ConnectionPopupStyle(val preferenceValue: String) {
    BOTTOM_SHEET("BOTTOM_SHEET"),
    DYNAMIC_ISLAND("DYNAMIC_ISLAND"),
    DISABLED("DISABLED");

    companion object {
        fun fromPreferences(
            storedValue: String?,
            legacyBottomSheetEnabled: Boolean = false,
            legacyIslandEnabled: Boolean = true
        ): ConnectionPopupStyle = when (storedValue?.uppercase(Locale.ROOT)) {
            BOTTOM_SHEET.preferenceValue -> BOTTOM_SHEET
            DYNAMIC_ISLAND.preferenceValue -> DYNAMIC_ISLAND
            DISABLED.preferenceValue -> DISABLED
            // The top island is the default. Respect the old bottom-sheet preference only
            // when the old island preference was explicitly off.
            else -> if (legacyBottomSheetEnabled && !legacyIslandEnabled) BOTTOM_SHEET
            else DYNAMIC_ISLAND
        }
    }
}
