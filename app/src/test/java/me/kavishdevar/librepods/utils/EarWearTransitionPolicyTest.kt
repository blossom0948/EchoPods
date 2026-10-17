package me.kavishdevar.librepods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarWearTransitionPolicyTest {
    @Test
    fun decodesBothSingleAndNoEarbudStates() {
        assertEquals(EarWearPosition.BOTH, EarWearTransitionPolicy.decode(0, 0))
        assertEquals(EarWearPosition.PARTIAL, EarWearTransitionPolicy.decode(0, 1))
        assertEquals(EarWearPosition.PARTIAL, EarWearTransitionPolicy.decode(1, 0))
        assertEquals(EarWearPosition.NONE, EarWearTransitionPolicy.decode(1, 1))
    }

    @Test
    fun malformedStateDoesNotBecomeAnOutOfEarEvent() {
        assertNull(EarWearTransitionPolicy.decode(2, 1))
        assertNull(EarWearTransitionPolicy.decode(0, 0x7F))
    }

    @Test
    fun anyDecreaseInWornBudsPausesAndAnyIncreaseOnlyResumesIfAppPaused() {
        assertEquals(EarWearAction.PAUSE, EarWearTransitionPolicy.action(EarWearPosition.BOTH, EarWearPosition.PARTIAL))
        assertEquals(EarWearAction.PAUSE, EarWearTransitionPolicy.action(EarWearPosition.PARTIAL, EarWearPosition.NONE))
        assertEquals(EarWearAction.RESUME_IF_PAUSED_BY_APP, EarWearTransitionPolicy.action(EarWearPosition.NONE, EarWearPosition.PARTIAL))
        assertEquals(EarWearAction.RESUME_IF_PAUSED_BY_APP, EarWearTransitionPolicy.action(EarWearPosition.PARTIAL, EarWearPosition.BOTH))
    }

    @Test
    fun initialOrRepeatedStateDoesNotTriggerPlayback() {
        assertEquals(EarWearAction.NONE, EarWearTransitionPolicy.action(null, EarWearPosition.PARTIAL))
        assertEquals(EarWearAction.NONE, EarWearTransitionPolicy.action(EarWearPosition.PARTIAL, EarWearPosition.PARTIAL))
        assertEquals(EarWearAction.NONE, EarWearTransitionPolicy.action(EarWearPosition.NONE, EarWearPosition.NONE))
    }

    @Test
    fun alreadyPausedMediaIsNotClaimedAndOnlyAppPausedMediaCanResume() {
        assertEquals(false, EarWearTransitionPolicy.shouldPause(EarWearPosition.BOTH, EarWearPosition.NONE, false))
        assertEquals(true, EarWearTransitionPolicy.shouldPause(EarWearPosition.BOTH, EarWearPosition.NONE, true))
        assertEquals(false, EarWearTransitionPolicy.shouldResume(EarWearPosition.NONE, EarWearPosition.PARTIAL, false))
        assertEquals(true, EarWearTransitionPolicy.shouldResume(EarWearPosition.NONE, EarWearPosition.PARTIAL, true))
    }

    @Test
    fun removingOrReinsertingOnlyOneEarbudControlsMedia() {
        assertEquals(true, EarWearTransitionPolicy.shouldPause(EarWearPosition.BOTH, EarWearPosition.PARTIAL, true))
        assertEquals(true, EarWearTransitionPolicy.shouldPause(EarWearPosition.PARTIAL, EarWearPosition.NONE, true))
        assertEquals(true, EarWearTransitionPolicy.shouldResume(EarWearPosition.NONE, EarWearPosition.PARTIAL, true))
        assertEquals(true, EarWearTransitionPolicy.shouldResume(EarWearPosition.PARTIAL, EarWearPosition.BOTH, true))
    }
}
