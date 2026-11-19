package me.kavishdevar.librepods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyVariantConfigurationTest {
    @Test
    fun galaxyFlavorUsesStoreManagedUpdatesWithoutPlayOnlyBehavior() {
        assertTrue(BuildConfig.STORE_BUILD)
        assertFalse(BuildConfig.PLAY_BUILD)
        assertTrue(BuildConfig.FLAVOR == "galaxy")
    }
}
