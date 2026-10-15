package com.galaxyairpods.domain

import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.AirPodsControlState
import com.galaxyairpods.domain.control.ControlCapabilityTier
import com.galaxyairpods.domain.control.ListeningMode
import com.galaxyairpods.domain.control.MicrophoneMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsControlModelsTest {
    @Test
    fun mapsKnownWireValuesWithoutGuessingUnknownModes() {
        assertEquals(AapControlId.LISTENING_MODE, AapControlId.fromWireValue(0x0D))
        assertEquals(ListeningMode.ADAPTIVE, ListeningMode.fromWireValue(4))
        assertEquals(MicrophoneMode.ALWAYS_LEFT, MicrophoneMode.fromWireValue(2))
        assertNull(AapControlId.fromWireValue(0x7F))
        assertNull(ListeningMode.fromWireValue(0x7F))
    }

    @Test
    fun controlStateDistinguishesUnknownBooleanFromExplicitFalse() {
        val unknown = AirPodsControlState()
        val knownFalse = unknown.withValue(
            AapControlId.CONVERSATION_DETECT_CONFIG,
            byteArrayOf(0x02),
        )

        assertNull(unknown.boolean(AapControlId.CONVERSATION_DETECT_CONFIG))
        assertFalse(knownFalse.boolean(AapControlId.CONVERSATION_DETECT_CONFIG)!!)
        assertEquals(ControlCapabilityTier.BLE_ONLY, unknown.tier)
        assertTrue(knownFalse.value(AapControlId.CONVERSATION_DETECT_CONFIG)!!.contentEquals(byteArrayOf(0x02)))
    }
}
