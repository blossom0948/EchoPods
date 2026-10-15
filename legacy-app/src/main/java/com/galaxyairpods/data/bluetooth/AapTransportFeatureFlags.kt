package com.galaxyairpods.data.bluetooth

import android.os.Build

/**
 * V4 transport policy. The hidden Classic socket probe is deliberately
 * isolated behind a narrow Samsung model/API allowlist. It is not a general
 * Android fallback and it never promotes a device to T2 by itself; only a
 * real AAP response followed by a valid 0x0004 packet can do that.
 */
internal object AapTransportFeatureFlags {
    private const val ENABLE_HIDDEN_CLASSIC_PROBE = true

    fun hiddenClassicL2capAllowed(): Boolean = ENABLE_HIDDEN_CLASSIC_PROBE &&
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
        when (Build.MODEL) {
            // Galaxy Z Fold6 / Fold7 are the combinations that have been
            // explicitly exercised with the rootless Classic AAP transport.
            "SM-F956N" -> Build.VERSION.SDK_INT >= 36
            "SM-F966N" -> Build.VERSION.SDK_INT >= 37
            else -> false
        }
}
