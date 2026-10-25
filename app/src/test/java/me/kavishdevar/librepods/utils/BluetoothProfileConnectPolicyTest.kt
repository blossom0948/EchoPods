package me.kavishdevar.librepods.utils

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothProfileConnectPolicyTest {
    @Test
    fun connectIsRequestedOnlyWhenProfileIsDisconnected() {
        assertTrue(
            BluetoothProfileConnectPolicy.shouldRequestConnect(
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
        assertFalse(
            BluetoothProfileConnectPolicy.shouldRequestConnect(
                BluetoothProfile.STATE_CONNECTING,
            ),
        )
        assertFalse(
            BluetoothProfileConnectPolicy.shouldRequestConnect(
                BluetoothProfile.STATE_CONNECTED,
            ),
        )
        assertFalse(
            BluetoothProfileConnectPolicy.shouldRequestConnect(
                BluetoothProfile.STATE_DISCONNECTING,
            ),
        )
    }

    @Test
    fun disconnectIsRequestedOnlyForAnActiveOrConnectingProfile() {
        assertFalse(
            BluetoothProfileConnectPolicy.shouldRequestDisconnect(
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
        assertTrue(
            BluetoothProfileConnectPolicy.shouldRequestDisconnect(
                BluetoothProfile.STATE_CONNECTING,
            ),
        )
        assertTrue(
            BluetoothProfileConnectPolicy.shouldRequestDisconnect(
                BluetoothProfile.STATE_CONNECTED,
            ),
        )
        assertFalse(
            BluetoothProfileConnectPolicy.shouldRequestDisconnect(
                BluetoothProfile.STATE_DISCONNECTING,
            ),
        )
    }
}
