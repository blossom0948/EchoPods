package me.kavishdevar.librepods.utils

/** Platform-neutral snapshot so selection behavior can be verified with local unit tests. */
data class MediaSessionCandidate(
    val id: String,
    val playbackState: Int?,
    val supportedActions: Long,
    val lastPositionUpdateTime: Long,
)

object MediaSessionSelectionPolicy {
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3
    const val ACTION_PAUSE = 1L shl 1
    const val ACTION_PLAY = 1L shl 2

    /** Pick the most recently updated session that is actually playing and can be paused. */
    fun selectPlaying(candidates: List<MediaSessionCandidate>): MediaSessionCandidate? =
        candidates
            .asSequence()
            .filter { it.playbackState == STATE_PLAYING }
            .filter { it.supportedActions and ACTION_PAUSE != 0L }
            .maxByOrNull { it.lastPositionUpdateTime }
}
