package me.kavishdevar.librepods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationModeReturnPolicyTest {
    @Test
    fun supportsNativeAndCustomTenToOneHundredTwentySecondRange() {
        assertEquals(0, ConversationModeReturnPolicy.sanitizeDelay(-1))
        assertEquals(10, ConversationModeReturnPolicy.sanitizeDelay(1))
        assertEquals(10, ConversationModeReturnPolicy.sanitizeDelay(9))
        assertEquals(15, ConversationModeReturnPolicy.sanitizeDelay(15))
        assertEquals(31, ConversationModeReturnPolicy.sanitizeDelay(31))
        assertEquals(0, ConversationModeReturnPolicy.sanitizeDelay(0))
        assertEquals(10, ConversationModeReturnPolicy.sanitizeDelay(10))
        assertEquals(30, ConversationModeReturnPolicy.sanitizeDelay(30))
        assertEquals(60, ConversationModeReturnPolicy.sanitizeDelay(60))
        assertEquals(120, ConversationModeReturnPolicy.sanitizeDelay(120))
        assertEquals(120, ConversationModeReturnPolicy.sanitizeDelay(121))
    }

    @Test
    fun createsDelayedRestoreOnlyWithKnownNonTransparencyMode() {
        assertEquals(
            ConversationModeReturnPlan(3, 4, 30),
            ConversationModeReturnPolicy.createPlan(30, 4),
        )
        assertEquals(
            ConversationModeReturnPlan(3, 2, 60),
            ConversationModeReturnPolicy.createPlan(60, 2),
        )
    }

    @Test
    fun refusesNativeUnknownAndAlreadyTransparencyPlans() {
        assertNull(ConversationModeReturnPolicy.createPlan(0, 4))
        assertNull(ConversationModeReturnPolicy.createPlan(30, null))
        assertNull(ConversationModeReturnPolicy.createPlan(30, 0))
        assertNull(ConversationModeReturnPolicy.createPlan(30, 5))
        assertNull(ConversationModeReturnPolicy.createPlan(30, 3))
        assertEquals(
            ConversationModeReturnPlan(3, 4, 31),
            ConversationModeReturnPolicy.createPlan(31, 4),
        )
    }
}
