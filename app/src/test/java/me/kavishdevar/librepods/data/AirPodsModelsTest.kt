package me.kavishdevar.librepods.data

import org.junit.Assert.assertEquals
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
    }
}
