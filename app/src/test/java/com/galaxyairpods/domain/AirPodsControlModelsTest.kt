package com.galaxyairpods.domain

import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.AirPodsControlState
import com.galaxyairpods.domain.control.ControlCapabilityTier
import com.galaxyairpods.domain.control.ListeningMode
import com.galaxyairpods.domain.control.MicrophoneMode
import com.galaxyairpods.domain.control.EarbudSide
import com.galaxyairpods.domain.control.HeadGesture
import com.galaxyairpods.domain.control.HeadGestureRecognizer
import com.galaxyairpods.domain.control.StemAction
import com.galaxyairpods.domain.control.StemPressActions
import com.galaxyairpods.domain.control.nextListeningMode
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

    @Test
    fun longPressInterceptionIsEnabledOnlyForCustomizedBud() {
        assertFalse(StemPressActions().interceptLongPress)
        assertTrue(
            StemPressActions(leftLongPress = StemAction.NEXT_TRACK).interceptLongPress,
        )
        assertEquals(
            StemAction.NEXT_TRACK,
            StemPressActions(leftLongPress = StemAction.NEXT_TRACK).forSide(EarbudSide.LEFT),
        )
        assertEquals(
            0x08,
            StemPressActions(leftLongPress = StemAction.NEXT_TRACK).aapStemConfigValue,
        )
        assertEquals(0x00, StemPressActions().aapStemConfigValue)
    }

    @Test
    fun recognizesStableNodAndShakeButRejectsSparseSamples() {
        val nod = HeadGestureRecognizer()
        assertNull(nod.accept(0, 0, 1_000))
        assertNull(nod.accept(0, 1_200, 1_050))
        assertNull(nod.accept(0, -1_200, 1_100))
        assertEquals(HeadGesture.NOD, nod.accept(0, 1_000, 1_150))

        val shake = HeadGestureRecognizer()
        assertNull(shake.accept(0, 0, 2_000))
        assertNull(shake.accept(1_200, 0, 2_050))
        assertNull(shake.accept(-1_200, 0, 2_100))
        assertEquals(HeadGesture.SHAKE, shake.accept(1_000, 0, 2_150))

        val sparse = HeadGestureRecognizer()
        sparse.accept(0, 0, 3_000)
        sparse.accept(0, 1_200, 3_050)
        assertNull(sparse.accept(0, -1_200, 3_500))
    }

    @Test
    fun staleAndAmbiguousHeadMotionDoesNotBecomeACallAction() {
        val stale = HeadGestureRecognizer()
        assertNull(stale.accept(0, 0, capturedAtMs = 1_000, nowMs = 1_400))
        assertNull(stale.accept(0, 1_200, capturedAtMs = 1_050, nowMs = 1_450))

        val ambiguous = HeadGestureRecognizer()
        assertNull(ambiguous.accept(0, 0, 2_000))
        assertNull(ambiguous.accept(1_200, 1_200, 2_050))
        assertNull(ambiguous.accept(-1_200, -1_200, 2_100))
        assertNull(ambiguous.accept(1_000, 1_000, 2_150))
    }

    @Test
    fun offListeningModeIsUsedOnlyAfterTheAccessoryExplicitlyAllowsIt() {
        assertEquals(
            ListeningMode.NOISE_CANCELLATION,
            nextListeningMode(ListeningMode.ADAPTIVE, allowOff = false),
        )
        assertEquals(
            ListeningMode.OFF,
            nextListeningMode(ListeningMode.ADAPTIVE, allowOff = true),
        )
        assertEquals(
            ListeningMode.NOISE_CANCELLATION,
            nextListeningMode(null, allowOff = true),
        )
    }
}
