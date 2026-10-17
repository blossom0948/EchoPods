package me.kavishdevar.librepods.utils

/** Wear-state transitions derived from the two physical AirPods ear-detection bytes. */
enum class EarWearPosition(val wornCount: Int) {
    NONE(0),
    PARTIAL(1),
    BOTH(2),
}

enum class EarWearAction {
    NONE,
    PAUSE,
    RESUME_IF_PAUSED_BY_APP,
}

object EarWearTransitionPolicy {
    /** In AAP ear-detection notifications, 0 means in-ear and 1 means out-of-ear. */
    fun decode(left: Byte, right: Byte): EarWearPosition? {
        if (left !in VALID_VALUES || right !in VALID_VALUES) return null
        val wornCount = (if (left == IN_EAR) 1 else 0) + (if (right == IN_EAR) 1 else 0)
        return when (wornCount) {
            0 -> EarWearPosition.NONE
            1 -> EarWearPosition.PARTIAL
            2 -> EarWearPosition.BOTH
            else -> null
        }
    }

    fun action(previous: EarWearPosition?, current: EarWearPosition): EarWearAction {
        if (previous == null || previous == current) return EarWearAction.NONE
        return when {
            current.wornCount < previous.wornCount -> EarWearAction.PAUSE
            current.wornCount > previous.wornCount -> EarWearAction.RESUME_IF_PAUSED_BY_APP
            else -> EarWearAction.NONE
        }
    }

    fun shouldPause(previous: EarWearPosition?, current: EarWearPosition, mediaIsPlaying: Boolean): Boolean =
        mediaIsPlaying && action(previous, current) == EarWearAction.PAUSE

    fun shouldResume(previous: EarWearPosition?, current: EarWearPosition, pausedByApp: Boolean): Boolean =
        pausedByApp && action(previous, current) == EarWearAction.RESUME_IF_PAUSED_BY_APP

    private val VALID_VALUES = setOf(OUT_OF_EAR, IN_EAR)
    private const val OUT_OF_EAR: Byte = 0x01
    private const val IN_EAR: Byte = 0x00
}
