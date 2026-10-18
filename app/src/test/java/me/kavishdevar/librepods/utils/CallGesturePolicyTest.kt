package me.kavishdevar.librepods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallGesturePolicyTest {
    @Test
    fun gesturesAreOnlyAvailableWhileAnIncomingCallIsRinging() {
        assertTrue(CallGesturePolicy.canRecognize(true, PhoneCallPhase.RINGING, false))
        assertFalse(CallGesturePolicy.canRecognize(true, PhoneCallPhase.IDLE, false))
        assertFalse(CallGesturePolicy.canRecognize(true, PhoneCallPhase.ACTIVE, false))
    }

    @Test
    fun disabledOrAlreadyAcceptedCallCannotRunHeadGestures() {
        assertFalse(CallGesturePolicy.canRecognize(false, PhoneCallPhase.RINGING, false))
        assertFalse(CallGesturePolicy.canRecognize(true, PhoneCallPhase.RINGING, true))
    }
}
