package me.kavishdevar.librepods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarWearTransitionPolicyTest {
    @Test
    fun decodesBothSingleAndNoEarbudStates() {
        assertEquals(EarWearPosition.BOTH, EarWearTransitionPolicy.decode(0, 0))
        assertEquals(EarWearPosition.LEFT_ONLY, EarWearTransitionPolicy.decode(0, 1))
        assertEquals(EarWearPosition.RIGHT_ONLY, EarWearTransitionPolicy.decode(1, 0))
        assertEquals(EarWearPosition.NONE, EarWearTransitionPolicy.decode(1, 1))
        assertEquals(EarWearPosition.LEFT_ONLY, EarWearTransitionPolicy.decode(0, 2))
        assertEquals(EarWearPosition.LEFT_ONLY, EarWearTransitionPolicy.decode(0, 3))
        assertEquals(EarWearPosition.RIGHT_ONLY, EarWearTransitionPolicy.decode(3, 0))
        assertEquals(EarWearPosition.NONE, EarWearTransitionPolicy.decode(2, 3))
    }

    @Test
    fun malformedStateDoesNotBecomeAnOutOfEarEvent() {
        assertNull(EarWearTransitionPolicy.decode(0, 0x7F))
        assertNull(EarWearTransitionPolicy.decode(4, 1))
    }

    @Test
    fun anyDecreaseInWornBudsPausesAndAnyIncreaseOnlyResumesIfAppPaused() {
        assertEquals(EarWearAction.PAUSE, EarWearTransitionPolicy.action(EarWearPosition.BOTH, EarWearPosition.LEFT_ONLY))
        assertEquals(EarWearAction.PAUSE, EarWearTransitionPolicy.action(EarWearPosition.LEFT_ONLY, EarWearPosition.NONE))
        assertEquals(EarWearAction.RESUME_IF_PAUSED_BY_APP, EarWearTransitionPolicy.action(EarWearPosition.NONE, EarWearPosition.LEFT_ONLY))
        assertEquals(EarWearAction.RESUME_IF_PAUSED_BY_APP, EarWearTransitionPolicy.action(EarWearPosition.LEFT_ONLY, EarWearPosition.BOTH))
    }

    @Test
    fun initialOrRepeatedStateDoesNotTriggerPlayback() {
        assertEquals(EarWearAction.NONE, EarWearTransitionPolicy.action(null, EarWearPosition.LEFT_ONLY))
        assertEquals(EarWearAction.NONE, EarWearTransitionPolicy.action(EarWearPosition.LEFT_ONLY, EarWearPosition.LEFT_ONLY))
        assertEquals(EarWearAction.NONE, EarWearTransitionPolicy.action(EarWearPosition.NONE, EarWearPosition.NONE))
    }

    @Test
    fun alreadyPausedMediaIsNotClaimedAndOnlyAppPausedMediaCanResume() {
        assertEquals(false, EarWearTransitionPolicy.shouldPause(EarWearPosition.BOTH, EarWearPosition.NONE, false))
        assertEquals(true, EarWearTransitionPolicy.shouldPause(EarWearPosition.BOTH, EarWearPosition.NONE, true))
        assertEquals(false, EarWearTransitionPolicy.shouldResume(EarWearPosition.NONE, EarWearPosition.LEFT_ONLY, false))
        assertEquals(true, EarWearTransitionPolicy.shouldResume(EarWearPosition.NONE, EarWearPosition.LEFT_ONLY, true))
    }

    @Test
    fun removingOrReinsertingOnlyOneEarbudControlsMedia() {
        assertEquals(true, EarWearTransitionPolicy.shouldPause(EarWearPosition.BOTH, EarWearPosition.RIGHT_ONLY, true))
        assertEquals(true, EarWearTransitionPolicy.shouldPause(EarWearPosition.RIGHT_ONLY, EarWearPosition.NONE, true))
        assertEquals(true, EarWearTransitionPolicy.shouldResume(EarWearPosition.NONE, EarWearPosition.RIGHT_ONLY, true))
        assertEquals(true, EarWearTransitionPolicy.shouldResume(EarWearPosition.RIGHT_ONLY, EarWearPosition.BOTH, true))
    }

    @Test
    fun switchingWhichSingleEarbudIsWornPausesInsteadOfBeingTreatedAsNoChange() {
        assertEquals(
            EarWearAction.PAUSE,
            EarWearTransitionPolicy.action(EarWearPosition.LEFT_ONLY, EarWearPosition.RIGHT_ONLY),
        )
    }

    @Test
    fun wearPopupOnlyAppearsWhenAnEarbudIsPutBackIn() {
        assertEquals(true, EarWearTransitionPolicy.shouldShowWornPopup(EarWearPosition.NONE, EarWearPosition.LEFT_ONLY))
        assertEquals(true, EarWearTransitionPolicy.shouldShowWornPopup(EarWearPosition.LEFT_ONLY, EarWearPosition.BOTH))
        assertEquals(false, EarWearTransitionPolicy.shouldShowWornPopup(EarWearPosition.BOTH, EarWearPosition.LEFT_ONLY))
        assertEquals(false, EarWearTransitionPolicy.shouldShowWornPopup(EarWearPosition.LEFT_ONLY, EarWearPosition.NONE))
        assertEquals(false, EarWearTransitionPolicy.shouldShowWornPopup(null, EarWearPosition.LEFT_ONLY))
    }
}
