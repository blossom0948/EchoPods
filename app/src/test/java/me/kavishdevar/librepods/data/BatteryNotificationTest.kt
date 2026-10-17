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
        assertEquals(true, battery.getValue(BatteryComponent.LEFT).isLevelKnown)
        assertEquals(true, battery.getValue(BatteryComponent.RIGHT).isLevelKnown)
        assertEquals(true, battery.getValue(BatteryComponent.CASE).isLevelKnown)
    }

    @Test
    fun missingRestoredValuesAreUnknownButZeroPercentIsKnown() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.restoreLastKnownBattery(leftLevel = 0, rightLevel = null, caseLevel = 57)

        val battery = notification.getBattery().associateBy { it.component }
        assertEquals(true, battery.getValue(BatteryComponent.LEFT).isLevelKnown)
        assertEquals(0, battery.getValue(BatteryComponent.LEFT).level)
        assertEquals(false, battery.getValue(BatteryComponent.RIGHT).isLevelKnown)
        assertEquals(true, battery.getValue(BatteryComponent.CASE).isLevelKnown)
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

    @Test
    fun bleReadingsUpdateUnknownComponentsAndIgnoreMissingValues() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.mergeBleBattery(
            leftLevel = 60,
            leftCharging = false,
            rightLevel = 70,
            rightCharging = true,
            caseLevel = 40,
            caseCharging = false,
        )

        notification.mergeBleBattery(
            leftLevel = null,
            leftCharging = null,
            rightLevel = 80,
            rightCharging = false,
            caseLevel = null,
            caseCharging = null,
        )

        val battery = notification.getBattery().associateBy { it.component }
        assertEquals(60, battery.getValue(BatteryComponent.LEFT).level)
        assertEquals(80, battery.getValue(BatteryComponent.RIGHT).level)
        assertEquals(40, battery.getValue(BatteryComponent.CASE).level)
    }

    @Test
    fun activeAacpReadingsWinButBleCanFillAacpDisconnectedCase() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.setBattery(batteryPacket(left = 63, right = 88, case = 41))

        val caseDisconnected = batteryPacket(left = 63, right = 88, case = 0).apply {
            this[20] = BatteryStatus.DISCONNECTED.toByte()
        }
        notification.setBattery(caseDisconnected)
        notification.mergeBleBattery(
            leftLevel = 60,
            leftCharging = false,
            rightLevel = 80,
            rightCharging = false,
            caseLevel = 37,
            caseCharging = false,
        )

        val battery = notification.getBattery().associateBy { it.component }
        assertEquals(63, battery.getValue(BatteryComponent.LEFT).level)
        assertEquals(88, battery.getValue(BatteryComponent.RIGHT).level)
        assertEquals(37, battery.getValue(BatteryComponent.CASE).level)
        assertEquals(BatteryStatus.NOT_CHARGING, battery.getValue(BatteryComponent.CASE).status)
    }

    @Test
    fun restoredPercentagesAreStaleAndDoNotRestoreCharging() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.restoreLastKnownBattery(leftLevel = 51, rightLevel = 76, caseLevel = 38)

        val battery = notification.getBattery().associateBy { it.component }
        assertEquals(51, battery.getValue(BatteryComponent.LEFT).level)
        assertEquals(76, battery.getValue(BatteryComponent.RIGHT).level)
        assertEquals(38, battery.getValue(BatteryComponent.CASE).level)
        assertEquals(BatteryStatus.DISCONNECTED, battery.getValue(BatteryComponent.LEFT).status)
        assertEquals(BatteryStatus.DISCONNECTED, battery.getValue(BatteryComponent.RIGHT).status)
        assertEquals(BatteryStatus.DISCONNECTED, battery.getValue(BatteryComponent.CASE).status)
    }

    @Test
    fun restoredValuesCanBeReplacedByFreshBleSamples() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.restoreLastKnownBattery(leftLevel = 51, rightLevel = 76, caseLevel = 38)
        notification.mergeBleBattery(
            leftLevel = 52,
            leftCharging = false,
            rightLevel = null,
            rightCharging = null,
            caseLevel = null,
            caseCharging = null,
        )

        val battery = notification.getBattery().associateBy { it.component }
        assertEquals(52, battery.getValue(BatteryComponent.LEFT).level)
        assertEquals(76, battery.getValue(BatteryComponent.RIGHT).level)
        assertEquals(38, battery.getValue(BatteryComponent.CASE).level)
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
