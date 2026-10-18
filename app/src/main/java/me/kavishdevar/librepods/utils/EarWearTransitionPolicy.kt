package me.kavishdevar.librepods.utils

/** Wear-state transitions preserve the physical side reported by the AirPods. */
enum class EarWearPosition(val wornMask: Int) {
    NONE(0b00),
    LEFT_ONLY(0b01),
    RIGHT_ONLY(0b10),
    BOTH(0b11);

    val wornCount: Int get() = Integer.bitCount(wornMask)
}

enum class EarWearAction {
    NONE,
    PAUSE,
    RESUME_IF_PAUSED_BY_APP,
}

object EarWearTransitionPolicy {
    /**
     * AAP Classic reports 0=in-ear, 1=out-of-ear, 2=in-case, and 3=disconnected.
     * Every valid state except IN_EAR means that physical earbud is not being worn.
     */
    fun decode(left: Byte, right: Byte): EarWearPosition? {
        if (left !in VALID_VALUES || right !in VALID_VALUES) return null
        val mask = (if (left == IN_EAR) LEFT_MASK else 0) or
            (if (right == IN_EAR) RIGHT_MASK else 0)
        return EarWearPosition.entries.firstOrNull { it.wornMask == mask }
    }

    fun action(previous: EarWearPosition?, current: EarWearPosition): EarWearAction {
        if (previous == null || previous == current) return EarWearAction.NONE
        val removedAnyEarbud = previous.wornMask and current.wornMask != previous.wornMask
        val addedAnyEarbud = current.wornMask and previous.wornMask != current.wornMask
        return when {
            // Removing either physical earbud pauses, even if the other side is inserted
            // in the same update (e.g. LEFT_ONLY -> RIGHT_ONLY).
            removedAnyEarbud -> EarWearAction.PAUSE
            addedAnyEarbud -> EarWearAction.RESUME_IF_PAUSED_BY_APP
            else -> EarWearAction.NONE
        }
    }

    fun shouldShowWornPopup(previous: EarWearPosition?, current: EarWearPosition): Boolean =
        previous != null && current.wornCount > previous.wornCount

    fun shouldPause(previous: EarWearPosition?, current: EarWearPosition, mediaIsPlaying: Boolean): Boolean =
        mediaIsPlaying && action(previous, current) == EarWearAction.PAUSE

    fun shouldResume(previous: EarWearPosition?, current: EarWearPosition, pausedByApp: Boolean): Boolean =
        pausedByApp && action(previous, current) == EarWearAction.RESUME_IF_PAUSED_BY_APP

    private val VALID_VALUES = setOf(IN_EAR, OUT_OF_EAR, IN_CASE, DISCONNECTED)
    private const val LEFT_MASK = 0b01
    private const val RIGHT_MASK = 0b10
    private const val OUT_OF_EAR: Byte = 0x01
    private const val IN_EAR: Byte = 0x00
    private const val IN_CASE: Byte = 0x02
    private const val DISCONNECTED: Byte = 0x03
}
