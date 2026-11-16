package me.kavishdevar.librepods

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.billing.BillingProviderFactory
import me.kavishdevar.librepods.data.ConnectionPopupStyle
import me.kavishdevar.librepods.data.IslandPopupSize
import me.kavishdevar.librepods.utils.XposedServiceHolder
import me.kavishdevar.librepods.utils.XposedState

class LibrePodsApplication: Application(), XposedServiceHelper.OnServiceListener, DefaultLifecycleObserver {

    override fun onCreate() {
        super<Application>.onCreate()
        migrateConnectionPopupDefaults()

        XposedServiceHelper.registerListener(this)
        BillingManager.provider = BillingProviderFactory.create(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** Move every existing installation to the new compact top-popup default once. */
    private fun migrateConnectionPopupDefaults() {
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        if (preferences.getInt("popup_defaults_migration_version", 0) >= 58) return
        val storedStyle = preferences.getString("connection_popup_style", null)
        val legacyBottom = preferences.getBoolean("show_bottom_sheet_popup", false)
        val legacyIsland = preferences.getBoolean("show_island_popup", true)
        val hasSavedPopupChoice = storedStyle != null ||
            preferences.contains("show_bottom_sheet_popup") ||
            preferences.contains("show_island_popup")
        val shouldMoveToSmallIsland = !hasSavedPopupChoice ||
            storedStyle == ConnectionPopupStyle.BOTTOM_SHEET.preferenceValue ||
            (storedStyle == null && legacyBottom)

        preferences.edit().apply {
            if (shouldMoveToSmallIsland) {
                putString("connection_popup_style", ConnectionPopupStyle.DYNAMIC_ISLAND.preferenceValue)
                putString("island_popup_size", IslandPopupSize.SMALL.preferenceValue)
                putBoolean("show_bottom_sheet_popup", false)
                putBoolean("show_island_popup", true)
            } else if (storedStyle == null) {
                putString(
                    "connection_popup_style",
                    ConnectionPopupStyle.fromPreferences(
                        storedValue = null,
                        legacyBottomSheetEnabled = legacyBottom,
                        legacyIslandEnabled = legacyIsland,
                    ).preferenceValue,
                )
            }
            putInt("popup_defaults_migration_version", 58)
        }.apply()
    }

    override fun onResume(owner: LifecycleOwner) {
        BillingManager.provider.queryPurchases()
        XposedState.isAvailable = XposedServiceHolder.service != null
        XposedState.bluetoothScopeEnabled = XposedServiceHolder.service?.scope?.contains("com.google.android.bluetooth") == true || XposedServiceHolder.service?.scope?.contains("com.android.bluetooth") == true
    }

    override fun onServiceBind(service: XposedService) {
        XposedServiceHolder.service = service
        XposedState.isAvailable = true
        XposedState.bluetoothScopeEnabled = XposedServiceHolder.service?.scope?.contains("com.google.android.bluetooth") == true || XposedServiceHolder.service?.scope?.contains("com.android.bluetooth") == true
    }

    override fun onServiceDied(p0: XposedService) {
        XposedServiceHolder.service = null
        XposedState.isAvailable = false
    }
}
