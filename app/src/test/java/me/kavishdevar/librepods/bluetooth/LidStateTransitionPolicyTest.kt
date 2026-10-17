package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidStateTransitionPolicyTest {
    @Test
    fun initialAdvertisementOnlySetsTheBaseline() {
        assertFalse(LidStateTransitionPolicy.shouldEmitTransition(null, true))
        assertFalse(LidStateTransitionPolicy.shouldEmitTransition(null, false))
    }

    @Test
    fun onlyActualStateChangesAreEmitted() {
        assertTrue(LidStateTransitionPolicy.shouldEmitTransition(false, true))
        assertTrue(LidStateTransitionPolicy.shouldEmitTransition(true, false))
        assertFalse(LidStateTransitionPolicy.shouldEmitTransition(true, true))
        assertFalse(LidStateTransitionPolicy.shouldEmitTransition(false, false))
    }
}
