package me.kavishdevar.librepods.bluetooth

/** BLE discovery establishes a baseline; only a known closed-to-open edge is a case-open event. */
object LidStateTransitionPolicy {
    fun shouldEmitTransition(previous: Boolean?, current: Boolean): Boolean =
        previous != null && previous != current
}
