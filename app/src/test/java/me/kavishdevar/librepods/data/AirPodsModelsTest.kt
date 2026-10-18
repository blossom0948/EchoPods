package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsModelsTest {
    @Test
    fun recognizesSupportedAirPodsAndMaxGenerationsWithoutTreatingMaxAsEarbuds() {
        assertTrue(AirPodsModels.getModelByModelNumber("A1523") is AirPods)
        assertTrue(AirPodsModels.getModelByModelNumber("A2032") is AirPods2)
        assertTrue(AirPodsModels.getModelByModelNumber("A2565") is AirPods3)
        assertTrue(AirPodsModels.getModelByModelNumber("A3053") is AirPods4)
        assertTrue(AirPodsModels.getModelByModelNumber("A3531") is AirPods5)
        assertTrue(AirPodsModels.getModelByModelNumber("A2084") is AirPodsPro1)
        assertTrue(AirPodsModels.getModelByModelNumber("A3047") is AirPodsPro2USBC)
        assertTrue(AirPodsModels.getModelByModelNumber("A3063") is AirPodsPro3)
        assertEquals(
            AirPodsFormFactor.OVER_EAR_HEADPHONES,
            AirPodsModels.getModelByModelNumber("A3454")?.formFactor,
        )
    }

    @Test
    fun Max1DoesNotExposeStemControlsAndMax2CapabilitiesAreModelSpecific() {
        val max1 = AirPodsModels.getModelByModelNumber("A3184")!!
        val max2 = AirPodsModels.getModelByModelNumber("A3454")!!

        assertTrue(Capability.STEM_CONFIG !in max1.capabilities)
        assertTrue(Capability.ADAPTIVE_AUDIO !in max1.capabilities)
        assertTrue(Capability.ADAPTIVE_AUDIO in max2.capabilities)
        assertTrue(Capability.CONVERSATION_AWARENESS in max2.capabilities)
        assertTrue(Capability.HEAD_GESTURES in max2.capabilities)
        assertTrue(Capability.SLEEP_DETECTION in max2.capabilities)
        assertTrue(Capability.LOUD_SOUND_REDUCTION in max2.capabilities)
        assertFalse(Capability.HRM in max2.capabilities)
    }

    @Test
    fun headGesturesAreLimitedToModelsAppleListsAsSupportingThem() {
        val airPods3 = AirPodsModels.getModelByModelNumber("A2565")!!
        val airPods4 = AirPodsModels.getModelByModelNumber("A3053")!!
        val airPods4Anc = AirPodsModels.getModelByModelNumber("A3056")!!

        assertFalse(Capability.HEAD_GESTURES in airPods3.capabilities)
        assertTrue(Capability.HEAD_GESTURES in airPods4.capabilities)
        assertTrue(Capability.HEAD_GESTURES in airPods4Anc.capabilities)
    }

    @Test
    fun everyRegisteredModelHasArtworkAndPro3HeartRateIsCapabilityGated() {
        AirPodsModels.models.forEach { model ->
            assertTrue("${model.name} buds artwork", model.budsRes != 0)
            assertTrue("${model.name} left artwork", model.leftBudsRes != 0)
            assertTrue("${model.name} right artwork", model.rightBudsRes != 0)
            assertTrue("${model.name} case artwork", model.caseRes != 0)
            model.modelNumber.forEach { number ->
                assertTrue("Model number $number resolves", AirPodsModels.getModelByModelNumber(number) === model)
            }
        }

        val airPods5WirelessCase = AirPodsModels.getModelByModelNumber("A3439")!!
        assertEquals(R.drawable.airpods_5_wireless_case, airPods5WirelessCase.caseRes)

        val pro3 = AirPodsModels.getModelByModelNumber("A3063")!!
        assertTrue(Capability.HRM in pro3.capabilities)
        assertTrue(Capability.HRM !in AirPodsModels.getModelByModelNumber("A3047")!!.capabilities)
    }
}
