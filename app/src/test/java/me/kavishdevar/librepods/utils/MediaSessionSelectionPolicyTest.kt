package me.kavishdevar.librepods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionSelectionPolicyTest {
    @Test
    fun selectsTheMostRecentlyUpdatedPlayingSessionWithPauseSupport() {
        val selected = MediaSessionSelectionPolicy.selectPlaying(
            listOf(
                MediaSessionCandidate("first-but-paused", MediaSessionSelectionPolicy.STATE_PAUSED, MediaSessionSelectionPolicy.ACTION_PLAY, 99),
                MediaSessionCandidate("second-playing", MediaSessionSelectionPolicy.STATE_PLAYING, MediaSessionSelectionPolicy.ACTION_PAUSE, 50),
                MediaSessionCandidate("third-playing-current", MediaSessionSelectionPolicy.STATE_PLAYING, MediaSessionSelectionPolicy.ACTION_PAUSE, 70),
                MediaSessionCandidate("newer-but-cannot-pause", MediaSessionSelectionPolicy.STATE_PLAYING, 0L, 100),
            )
        )
        assertEquals("third-playing-current", selected?.id)
    }

    @Test
    fun doesNotSelectPausedOrUnsupportedSessions() {
        assertNull(
            MediaSessionSelectionPolicy.selectPlaying(
                listOf(
                    MediaSessionCandidate("paused", MediaSessionSelectionPolicy.STATE_PAUSED, MediaSessionSelectionPolicy.ACTION_PAUSE, 100),
                    MediaSessionCandidate("unsupported", MediaSessionSelectionPolicy.STATE_PLAYING, 0L, 100),
                )
            )
        )
    }
}
