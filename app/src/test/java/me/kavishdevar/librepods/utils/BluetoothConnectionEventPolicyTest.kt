package me.kavishdevar.librepods.utils

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothConnectionEventPolicyTest {
    private val connected = BluetoothConnectionEventPolicy.Event.CONNECTED
    private val linkCandidate = BluetoothConnectionEventPolicy.Event.LINK_CANDIDATE
    private val profileDisconnected = BluetoothConnectionEventPolicy.Event.PROFILE_DISCONNECTED
    private val disconnected = BluetoothConnectionEventPolicy.Event.DISCONNECTED
    private val ignored = BluetoothConnectionEventPolicy.Event.IGNORE

    @Test
    fun selectedAirPodsAclConnectDoesNotPretendAudioProfileIsConnected() {
        assertEquals(
            linkCandidate,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.device.action.ACL_CONNECTED",
                profileState = null,
                isSelectedAirPods = true,
            ),
        )
    }

    @Test
    fun selectedAirPodsAclDisconnectIsNotTreatedAsAConnection() {
        assertEquals(
            disconnected,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.device.action.ACL_DISCONNECTED",
                profileState = null,
                isSelectedAirPods = true,
            ),
        )
    }

    @Test
    fun onlyConnectedAudioProfileStateIsAccepted() {
        assertEquals(
            connected,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
                BluetoothProfile.STATE_CONNECTED,
                isSelectedAirPods = true,
            ),
        )
        assertEquals(
            ignored,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
                BluetoothProfile.STATE_CONNECTING,
                isSelectedAirPods = true,
            ),
        )
        assertEquals(
            ignored,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
                BluetoothProfile.STATE_DISCONNECTING,
                isSelectedAirPods = true,
            ),
        )
        assertEquals(
            profileDisconnected,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
                BluetoothProfile.STATE_DISCONNECTED,
                isSelectedAirPods = true,
            ),
        )
    }

    @Test
    fun unrelatedOrUnselectedBluetoothEventsAreIgnored() {
        assertEquals(
            ignored,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.device.action.BOND_STATE_CHANGED",
                BluetoothProfile.STATE_CONNECTED,
                isSelectedAirPods = true,
            ),
        )
        assertEquals(
            ignored,
            BluetoothConnectionEventPolicy.classify(
                "android.bluetooth.device.action.ACL_CONNECTED",
                profileState = null,
                isSelectedAirPods = false,
            ),
        )
    }

    @Test
    fun automaticControlSessionWaitsForConfirmedAudioProfile() {
        assertEquals(
            false,
            BluetoothConnectionEventPolicy.shouldStartControlSession(
                androidAudioProfileConnected = false,
            ),
        )
        assertEquals(
            true,
            BluetoothConnectionEventPolicy.shouldStartControlSession(
                androidAudioProfileConnected = true,
            ),
        )
    }

    @Test
    fun explicitControlRetryCanRunWithoutProfileBroadcast() {
        assertEquals(
            true,
            BluetoothConnectionEventPolicy.shouldStartControlSession(
                androidAudioProfileConnected = false,
                manualRetry = true,
            ),
        )
    }

    @Test
    fun savedDeviceMustBePresentInsteadOfFallingBackToAnotherAirPods() {
        assertEquals(
            null,
            BluetoothConnectionEventPolicy.selectConnectedAddress(
                selectedAddress = "AA:BB:CC:DD:EE:01",
                connectedCandidates = listOf("AA:BB:CC:DD:EE:02" to true),
            ),
        )
    }

    @Test
    fun savedDeviceWinsAmongMultipleConnectedAirPods() {
        assertEquals(
            "AA:BB:CC:DD:EE:02",
            BluetoothConnectionEventPolicy.selectConnectedAddress(
                selectedAddress = "aa:bb:cc:dd:ee:02",
                connectedCandidates = listOf(
                    "AA:BB:CC:DD:EE:01" to true,
                    "AA:BB:CC:DD:EE:02" to true,
                ),
            ),
        )
    }

    @Test
    fun withoutSavedIdentityOnlyOneUnambiguousAirPodsCandidateIsAccepted() {
        assertEquals(
            "AA:BB:CC:DD:EE:02",
            BluetoothConnectionEventPolicy.selectConnectedAddress(
                selectedAddress = null,
                connectedCandidates = listOf(
                    "AA:BB:CC:DD:EE:01" to false,
                    "AA:BB:CC:DD:EE:02" to true,
                ),
            ),
        )
        assertEquals(
            null,
            BluetoothConnectionEventPolicy.selectConnectedAddress(
                selectedAddress = null,
                connectedCandidates = listOf(
                    "AA:BB:CC:DD:EE:01" to true,
                    "AA:BB:CC:DD:EE:02" to true,
                ),
            ),
        )
    }
}
