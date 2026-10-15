package me.kavishdevar.librepods.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryNotificationTest {
    @Test
    fun disconnectedComponentsKeepTheirLastReportedPercentages() {
        val notification = AirPodsNotifications.BatteryNotification()
        val active = batteryPacket(left = 63, right = 88, case = 41)
        notification.setBattery(active)

        val disconnected = batteryPacket(left = 0, right = 0, case = 0).apply {
            this[10] = BatteryStatus.DISCONNECTED.toByte()
            this[15] = BatteryStatus.DISCONNECTED.toByte()
            this[20] = BatteryStatus.DISCONNECTED.toByte()
        }
        notification.setBattery(disconnected)

        val battery = notification.getBattery().associateBy { it.component }
        assertEquals(63, battery.getValue(BatteryComponent.LEFT).level)
        assertEquals(88, battery.getValue(BatteryComponent.RIGHT).level)
        assertEquals(41, battery.getValue(BatteryComponent.CASE).level)
        assertEquals(BatteryStatus.DISCONNECTED, battery.getValue(BatteryComponent.LEFT).status)
        assertEquals(BatteryStatus.DISCONNECTED, battery.getValue(BatteryComponent.RIGHT).status)
        assertEquals(BatteryStatus.DISCONNECTED, battery.getValue(BatteryComponent.CASE).status)
    }

    @Test
    fun malformedPercentageDoesNotOverwriteTheLastValidReading() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.setBattery(batteryPacket(left = 63, right = 88, case = 41))

        val malformed = batteryPacket(left = 63, right = 88, case = 41).apply {
            this[9] = 101
        }
        notification.setBattery(malformed)

        val left = notification.getBattery().first { it.component == BatteryComponent.LEFT }
        assertEquals(63, left.level)
    }

    private fun batteryPacket(left: Int, right: Int, case: Int) = ByteArray(22).apply {
        this[0] = 0x04
        this[2] = 0x04
        this[4] = 0x04
        this[7] = BatteryComponent.LEFT.toByte()
        this[9] = left.toByte()
        this[10] = BatteryStatus.NOT_CHARGING.toByte()
        this[12] = BatteryComponent.RIGHT.toByte()
        this[14] = right.toByte()
        this[15] = BatteryStatus.NOT_CHARGING.toByte()
        this[17] = BatteryComponent.CASE.toByte()
        this[19] = case.toByte()
        this[20] = BatteryStatus.NOT_CHARGING.toByte()
    }
}
