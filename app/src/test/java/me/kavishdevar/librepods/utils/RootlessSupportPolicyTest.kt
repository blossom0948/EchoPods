package me.kavishdevar.librepods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootlessSupportPolicyTest {
    @Test
    fun fold4ClassSamsungOnAndroid16IsNotMistakenForRootlessSupportedBuild() {
        assertFalse(isKnownRootlessSupport("samsung", 36, "UP1A.231005.007"))
    }

    @Test
    fun supportedPixelAndOppoFamilyBuildsUseTheirSpecificRequirements() {
        assertTrue(isKnownRootlessSupport("google", 36, "CP1A.260305.001"))
        assertFalse(isKnownRootlessSupport("google", 36, "BP1A.260101.001"))
        assertTrue(isKnownRootlessSupport("oneplus", 36, "ANY"))
        assertFalse(isKnownRootlessSupport("oneplus", 35, "ANY"))
    }

    @Test
    fun futureAndroidSupportGateRemainsVersionBased() {
        assertTrue(isKnownRootlessSupport("samsung", 37, "ANY"))
    }
}
