package me.kavishdevar.librepods.bluetooth

/** Accepts advertisements only when cryptographically verified or resolved to the selected bond. */
internal object BleAdvertisementIdentityPolicy {
    private val bluetoothAddressPattern = Regex("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    fun isTrusted(
        observedAddress: String,
        selectedBondAddress: String?,
        verifiedByIrk: Boolean,
        matchedSelectedBondAddressFilter: Boolean = false,
    ): Boolean {
        if (verifiedByIrk || matchedSelectedBondAddressFilter) return true
        if (selectedBondAddress == null ||
            !bluetoothAddressPattern.matches(observedAddress) ||
            !bluetoothAddressPattern.matches(selectedBondAddress)
        ) {
            return false
        }
        return observedAddress.equals(selectedBondAddress, ignoreCase = true)
    }
}
