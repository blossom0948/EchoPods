package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdvertisementIdentityPolicyTest {
    @Test
    fun acceptsAddressResolvedToSelectedBondIgnoringCase() {
        assertTrue(
            BleAdvertisementIdentityPolicy.isTrusted(
                observedAddress = "68:ca:c4:12:34:56",
                selectedBondAddress = "68:CA:C4:12:34:56",
                verifiedByIrk = false,
            ),
        )
    }

    @Test
    fun rejectsNearbyAirPodsWithDifferentAddressWithoutIrkProof() {
        assertFalse(
            BleAdvertisementIdentityPolicy.isTrusted(
                observedAddress = "68:CA:C4:12:34:56",
                selectedBondAddress = "68:CA:C4:65:43:21",
                verifiedByIrk = false,
            ),
        )
    }

    @Test
    fun rejectsUnresolvedAddressWhenSelectedBondIsMissing() {
        assertFalse(
            BleAdvertisementIdentityPolicy.isTrusted(
                observedAddress = "68:CA:C4:12:34:56",
                selectedBondAddress = null,
                verifiedByIrk = false,
            ),
        )
    }

    @Test
    fun acceptsCryptographicallyVerifiedRotatingAddress() {
        assertTrue(
            BleAdvertisementIdentityPolicy.isTrusted(
                observedAddress = "C2:11:27:45:89:AB",
                selectedBondAddress = "68:CA:C4:12:34:56",
                verifiedByIrk = true,
            ),
        )
    }

    @Test
    fun acceptsRotatingAddressWhenAndroidMatchedTheSelectedBondScanFilter() {
        assertTrue(
            BleAdvertisementIdentityPolicy.isTrusted(
                observedAddress = "C2:11:27:45:89:AB",
                selectedBondAddress = "68:CA:C4:12:34:56",
                verifiedByIrk = false,
                matchedSelectedBondAddressFilter = true,
            ),
        )
    }

    @Test
    fun doesNotTrustSystemFilterResultAfterSelectedBondChanges() {
        assertFalse(
            BleAdvertisementIdentityPolicy.isTrusted(
                observedAddress = "C2:11:27:45:89:AB",
                selectedBondAddress = "68:CA:C4:65:43:21",
                verifiedByIrk = false,
                matchedSelectedBondAddressFilter = false,
            ),
        )
    }
}
