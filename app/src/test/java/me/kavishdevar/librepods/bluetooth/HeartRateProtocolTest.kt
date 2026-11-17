package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateProtocolTest {
    private fun heartRatePayload(bpm: Int = 72, confidence: Int = 192, tail: IntArray = intArrayOf(0x10, 0, 0)) =
        ByteArray(18).apply {
            this[1] = bpm.toByte()
            this[2] = confidence.toByte()
            tail.forEachIndexed { index, value -> this[15 + index] = value.toByte() }
        }

    private fun sensorCommand(service: Int, payload: ByteArray, logType: Int = 3): ByteArray {
        val command = byteArrayOf(0x08, service.toByte(), 0x1a, payload.size.toByte()) + payload
        return byteArrayOf(0x08, 3, 0x10, logType.toByte(), 0x3a, command.size.toByte()) + command
    }

    @Test
    fun rtBuddyStreamReassemblesRecordsAcrossEveryReadBoundary() {
        val proto = sensorCommand(19, heartRatePayload())
        val record = SensorDataWxCodec.wrapRtBuddy(proto)
        for (split in 1 until record.size) {
            val frames = HeartRateSensorFrames()
            assertTrue(frames.feed(record.copyOfRange(0, split)).isEmpty())
            assertArrayEquals(record, frames.feed(record.copyOfRange(split, record.size)).single())
        }
    }

    @Test
    fun sensorStreamPacketUsesExpectedAacpAndProtobufFields() {
        assertArrayEquals(
            byteArrayOf(
                0x08, 0x00, 0x10, 0x02, 0x42, 0x0b,
                0x08, 0x54, 0x10, 0x02, 0x1a, 0x05, 0x01, 0x40, 0x42, 0x0f, 0x00,
            ),
            SensorDataWxCodec.serviceSetting(0, 2, 84, 1, 1_000_000),
        )
        assertArrayEquals(
            byteArrayOf(0x08, 0x00, 0x22, 0x00),
            SensorDataWxCodec.requestAllDescriptors(0),
        )
    }

    @Test
    fun unknownHeartRateServiceFallbackCoversBothObservedPro3Ids() {
        assertEquals(listOf(19, 20, 19), (0..2).map(HeartRateSession::legacyHeartRateService))
    }

    @Test
    fun heartRateReportRequiresFreshProtocolShapeAndPlausibleValues() {
        val parser = HeartRateSensorReports()
        val valid = SensorDataWxCodec.decode(sensorCommand(19, heartRatePayload()))
        assertEquals(HeartRateSensorReports.Sample(72, 192), parser.receive(valid).sample)
        assertNull(parser.receive(SensorDataWxCodec.decode(sensorCommand(19, heartRatePayload(bpm = 29)))).sample)
        assertNull(parser.receive(SensorDataWxCodec.decode(sensorCommand(19, heartRatePayload(confidence = 127)))).sample)
        assertNull(parser.receive(SensorDataWxCodec.decode(sensorCommand(19, heartRatePayload(tail = intArrayOf(1, 2, 3))))).sample)
        assertNull(parser.receive(SensorDataWxCodec.decode(sensorCommand(19, heartRatePayload(), logType = 2))).sample)
    }

    @Test
    fun malformedOrOversizedDataIsRejectedWithoutLeakingIntoNormalPackets() {
        val parser = HeartRateSensorFrames()
        val record = SensorDataWxCodec.wrapRtBuddy(sensorCommand(19, heartRatePayload()))
        val oversized = record.copyOf(12).apply {
            this[10] = 0xff.toByte()
            this[11] = 0xff.toByte()
        }
        assertEquals(1, parser.feed(oversized + record).size)
        assertTrue(parser.feed(ByteArray(HeartRateSensorFrames.MAX_RECORD + 1)).isEmpty())
        assertTrue(runCatching { SensorDataWxCodec.decode(byteArrayOf(0x3a, 0x7f)) }.isFailure)
    }
}
