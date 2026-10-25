package me.kavishdevar.librepods

import org.junit.Assert.assertFalse
import org.junit.Test

class FossVariantConfigurationTest {
    @Test
    fun fossFlavorKeepsTheGithubSelfUpdaterEnabled() {
        assertFalse(BuildConfig.PLAY_BUILD)
    }
}
