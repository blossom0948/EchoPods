package me.kavishdevar.librepods.utils

import android.bluetooth.BluetoothProfile

/** Avoids redundant profile operations while the Android audio route is already changing. */
internal object BluetoothProfileConnectPolicy {
    fun shouldRequestConnect(profileState: Int): Boolean =
        profileState == BluetoothProfile.STATE_DISCONNECTED

    fun shouldRequestDisconnect(profileState: Int): Boolean =
        profileState == BluetoothProfile.STATE_CONNECTED ||
            profileState == BluetoothProfile.STATE_CONNECTING
}
