package me.kavishdevar.librepods

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayVariantConfigurationTest {
    @Test
    fun playFlavorDisablesTheGithubSelfUpdater() {
        assertTrue(BuildConfig.PLAY_BUILD)
    }
}
