package me.kavishdevar.librepods.presentation.widgets

import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPods2
import me.kavishdevar.librepods.data.AirPodsMaxUSBC
import me.kavishdevar.librepods.data.AirPodsPro2USBC
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryWidgetIconPolicyTest {
    @Test
    fun choosesIconForSelectedModelFamily() {
        val regular = BatteryWidgetIconPolicy.iconsForModel(AirPods2())
        assertEquals(R.drawable.ic_airpod_regular_widget, regular.left)
        assertEquals(R.drawable.ic_airpod_regular_right_widget, regular.right)

        val pro = BatteryWidgetIconPolicy.iconsForModel(AirPodsPro2USBC())
        assertEquals(R.drawable.ic_airpod_pro_left_widget, pro.left)
        assertEquals(R.drawable.ic_airpod_pro_right_widget, pro.right)

        val max = BatteryWidgetIconPolicy.iconsForModel(AirPodsMaxUSBC())
        assertEquals(R.drawable.ic_headphones_widget, max.left)
        assertEquals(R.drawable.ic_headphones_widget, max.right)
    }

    @Test
    fun unknownModelUsesNeutralEarbudIcon() {
        assertEquals(
            R.drawable.ic_airpod_regular_widget,
            BatteryWidgetIconPolicy.iconsForModel(null).left,
        )
    }
}
