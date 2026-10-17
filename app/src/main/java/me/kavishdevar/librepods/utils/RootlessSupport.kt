/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.utils

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

fun isSupported(sharedPreferences: SharedPreferences): Boolean {
    val isBypassFlagActive = sharedPreferences.getBoolean("bypass_device_check.v2", false)
    return isBypassFlagActive || hasRootlessBluetoothSupport()
}

/** True only when this OS/build is in the known rootless Bluetooth support set. */
fun hasRootlessBluetoothSupport(): Boolean {
    return isKnownRootlessSupport(Build.MANUFACTURER, Build.VERSION.SDK_INT, Build.ID)
}

internal fun isKnownRootlessSupport(manufacturer: String, sdk: Int, buildId: String): Boolean {
    if (sdk >= 37) return true

    val normalizedManufacturer = manufacturer.lowercase()
    val isPixel = normalizedManufacturer == "google"
    val isOppoFamily = normalizedManufacturer in listOf("oneplus", "oppo", "realme")

    if (isPixel && sdk == 36) {
        return buildId.startsWith("CP1A")
    } else if (isOppoFamily) {
        return sdk >= 36
    }
    return false
}

fun bypassDeviceCheck(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit{ putBoolean("bypass_device_check.v2", true) }
}

fun removeDeviceCheckBypass(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit{ putBoolean("bypass_device_check.v2", false) }
}
