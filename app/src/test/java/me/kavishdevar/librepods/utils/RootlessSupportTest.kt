package me.kavishdevar.librepods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootlessSupportTest {
    @Test
    fun samsungAndroid16BuildUsedByFold4IsNotMarkedRootlessSupported() {
        assertFalse(
            isKnownRootlessSupport(
                manufacturer = "samsung",
                sdk = 36,
                buildId = "BP2A.250605.031.A3",
            )
        )
    }

    @Test
    fun supportedAndroid16Qpr3PixelBuildIsMarkedRootlessSupported() {
        assertTrue(
            isKnownRootlessSupport(
                manufacturer = "Google",
                sdk = 36,
                buildId = "CP1A.260305.018",
            )
        )
    }

    @Test
    fun android17BuildIsNotRestrictedToSpecificManufacturers() {
        assertTrue(
            isKnownRootlessSupport(
                manufacturer = "samsung",
                sdk = 37,
                buildId = "BP3A.test",
            )
        )
    }

    @Test
    fun unsupportedAndroid16PixelBuildIsNotAccidentallyAccepted() {
        assertFalse(
            isKnownRootlessSupport(
                manufacturer = "google",
                sdk = 36,
                buildId = "BP2A.250605.031.A3",
            )
        )
    }
}
