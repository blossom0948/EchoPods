package me.kavishdevar.librepods.utils

import android.bluetooth.BluetoothProfile

/** Separates a selected device's low-level link from a confirmed Android audio profile. */
internal object BluetoothConnectionEventPolicy {
    enum class Event {
        IGNORE,
        LINK_CANDIDATE,
        CONNECTED,
        PROFILE_DISCONNECTED,
        DISCONNECTED,
    }

    private const val ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED"
    private const val ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED"
    private const val ACTION_A2DP_CONNECTION_STATE_CHANGED =
        "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
    private const val ACTION_HEADSET_CONNECTION_STATE_CHANGED =
        "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"

    fun classify(action: String?, profileState: Int?, isSelectedAirPods: Boolean): Event {
        if (!isSelectedAirPods) return Event.IGNORE

        return when (action) {
            ACTION_ACL_CONNECTED -> Event.LINK_CANDIDATE
            ACTION_ACL_DISCONNECTED -> Event.DISCONNECTED
            ACTION_A2DP_CONNECTION_STATE_CHANGED,
            ACTION_HEADSET_CONNECTION_STATE_CHANGED ->
                when (profileState) {
                    BluetoothProfile.STATE_CONNECTED -> Event.CONNECTED
                    BluetoothProfile.STATE_DISCONNECTED -> Event.PROFILE_DISCONNECTED
                    else -> Event.IGNORE
                }

            else -> Event.IGNORE
        }
    }

    fun profileForAction(action: String?): Int? = when (action) {
        ACTION_A2DP_CONNECTION_STATE_CHANGED -> BluetoothProfile.A2DP
        ACTION_HEADSET_CONNECTION_STATE_CHANGED -> BluetoothProfile.HEADSET
        else -> null
    }

    /**
     * Resolve the active profile only to the saved device. Without a saved identity,
     * accept a candidate only when exactly one connected device looks like AirPods.
     */
    fun selectConnectedAddress(
        selectedAddress: String?,
        connectedCandidates: List<Pair<String, Boolean>>,
    ): String? {
        if (!selectedAddress.isNullOrBlank()) {
            return connectedCandidates.firstOrNull { (address, _) ->
                address.equals(selectedAddress, ignoreCase = true)
            }?.first
        }
        return connectedCandidates.singleOrNull { (_, looksLikeAirPods) -> looksLikeAirPods }?.first
    }

    /**
     * Do not open the proprietary control channel just because an ACL link exists.
     * Automatic attempts wait until Android confirms an audio profile; explicit user
     * retries may still try the channel directly.
     */
    fun shouldStartControlSession(
        androidAudioProfileConnected: Boolean,
        manualRetry: Boolean = false,
    ): Boolean = manualRetry || androidAudioProfileConnected
}
