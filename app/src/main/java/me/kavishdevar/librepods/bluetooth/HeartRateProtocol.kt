/* SPDX-License-Identifier: GPL-3.0-or-later */
package me.kavishdevar.librepods.bluetooth

import java.io.ByteArrayOutputStream

/** Small, bounded protobuf subset used by the AirPods SensorDataWX RTBuddy service. */
internal data class SensorDataWx(
    val sequence: Int = 0,
    val logType: Int = 0,
    val descriptors: List<SensorDescriptor> = emptyList(),
    val command: SensorCommand? = null,
)

internal data class SensorDescriptor(val service: Int, val bytes: ByteArray)
internal data class SensorCommand(val service: Int, val payload: ByteArray)

internal object SensorDataWxCodec {
    private const val MAX_MESSAGE_BYTES = 16_384
    private const val MAX_DESCRIPTORS = 96

    fun decode(bytes: ByteArray): SensorDataWx {
        require(bytes.size <= MAX_MESSAGE_BYTES) { "SensorDataWX too large" }
        var sequence = 0
        var logType = 0
        val descriptors = mutableListOf<SensorDescriptor>()
        var command: SensorCommand? = null
        val reader = ProtoReader(bytes)
        while (!reader.exhausted()) {
            val tag = reader.varint().toInt()
            require(tag > 0) { "Invalid protobuf tag" }
            val field = tag ushr 3
            when (field) {
                1 -> { require(tag and 7 == 0); sequence = reader.varint().toInt() }
                2 -> { require(tag and 7 == 0); logType = reader.varint().toInt() }
                5 -> {
                    require(tag and 7 == 2 && descriptors.size < MAX_DESCRIPTORS)
                    descriptors += decodeDescriptor(reader.bytes())
                }
                7 -> {
                    require(tag and 7 == 2)
                    command = decodeCommand(reader.bytes())
                }
                else -> reader.skip(tag and 7)
            }
        }
        return SensorDataWx(sequence, logType, descriptors, command)
    }

    fun requestAllDescriptors(sequence: Int): ByteArray = protobuf {
        intField(1, sequence)
        bytesField(4, byteArrayOf())
    }

    fun serviceSetting(sequence: Int, logType: Int, service: Int, report: Int, intervalMicros: Int): ByteArray {
        require(service in 1..127 && report in 0..255 && intervalMicros >= 0)
        val configuration = byteArrayOf(report.toByte()) + ByteArray(4) {
            (intervalMicros ushr (it * 8)).toByte()
        }
        val setting = protobuf {
            intField(1, service)
            intField(2, 2)
            bytesField(3, configuration)
        }
        return protobuf {
            intField(1, sequence)
            intField(2, logType)
            bytesField(8, setting)
        }
    }

    fun wrapRtBuddy(proto: ByteArray): ByteArray {
        require(proto.size <= 0xffff)
        return byteArrayOf(4, 0, 4, 0, 0x17, 0, 0, 0, 0x10, 0,
            proto.size.toByte(), (proto.size ushr 8).toByte()) + proto
    }

    private fun decodeDescriptor(bytes: ByteArray): SensorDescriptor {
        var service = 0
        var descriptor = byteArrayOf()
        val reader = ProtoReader(bytes)
        while (!reader.exhausted()) {
            val tag = reader.varint().toInt()
            require(tag > 0)
            when (tag ushr 3) {
                1 -> { require(tag and 7 == 0); service = reader.varint().toInt() }
                2 -> { require(tag and 7 == 2); descriptor = reader.bytes() }
                else -> reader.skip(tag and 7)
            }
        }
        require(service in 1..127 && descriptor.isNotEmpty())
        return SensorDescriptor(service, descriptor)
    }

    private fun decodeCommand(bytes: ByteArray): SensorCommand {
        var service = 0
        var payload = byteArrayOf()
        val reader = ProtoReader(bytes)
        while (!reader.exhausted()) {
            val tag = reader.varint().toInt()
            require(tag > 0)
            when (tag ushr 3) {
                1 -> { require(tag and 7 == 0); service = reader.varint().toInt() }
                3 -> { require(tag and 7 == 2); payload = reader.bytes() }
                else -> reader.skip(tag and 7)
            }
        }
        require(service in 1..127)
        return SensorCommand(service, payload)
    }

    private class ProtoReader(private val data: ByteArray) {
        private var index = 0
        fun exhausted() = index == data.size

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                require(index < data.size) { "Truncated protobuf varint" }
                val byte = data[index++].toInt() and 0xff
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            throw IllegalArgumentException("Invalid protobuf varint")
        }

        fun bytes(): ByteArray {
            val length = varint()
            require(length in 0..MAX_MESSAGE_BYTES.toLong() && length <= data.size - index)
            return data.copyOfRange(index, index + length.toInt()).also { index += length.toInt() }
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> varint()
                1 -> advance(8)
                2 -> { val length = varint(); require(length <= MAX_MESSAGE_BYTES); advance(length.toInt()) }
                5 -> advance(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type")
            }
        }

        private fun advance(count: Int) {
            require(count >= 0 && count <= data.size - index)
            index += count
        }
    }

    private class ProtoWriter {
        private val output = ByteArrayOutputStream()
        fun intField(field: Int, value: Int) {
            rawVarint((field shl 3).toLong())
            rawVarint(value.toLong() and 0xffffffffL)
        }
        fun bytesField(field: Int, bytes: ByteArray) {
            rawVarint(((field shl 3) or 2).toLong())
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }
        fun toByteArray() = output.toByteArray()
        private fun rawVarint(value: Long) {
            var v = value
            while (v and -128L != 0L) {
                output.write(((v and 0x7f) or 0x80).toInt())
                v = v ushr 7
            }
            output.write(v.toInt())
        }
    }

    private inline fun protobuf(block: ProtoWriter.() -> Unit): ByteArray =
        ProtoWriter().apply(block).toByteArray()
}

/** Reassembles fragmented RTBuddy SensorDataWX records from arbitrary Bluetooth reads. */
internal class HeartRateSensorFrames {
    private var carry = byteArrayOf()

    @Synchronized
    fun feed(chunk: ByteArray): List<ByteArray> {
        if (chunk.size > MAX_RECORD || carry.size + chunk.size > MAX_RECORD * 2) {
            carry = byteArrayOf()
            return emptyList()
        }
        val input = carry + chunk
        val frames = mutableListOf<ByteArray>()
        var cursor = 0
        while (input.size - cursor >= PREFIX.size) {
            if (!PREFIX.indices.all { input[cursor + it] == PREFIX[it] }) { cursor++; continue }
            if (input.size - cursor < HEADER_SIZE) break
            val descriptor = (input[cursor + 6].toInt() and 255) or
                ((input[cursor + 7].toInt() and 255) shl 8) or
                ((input[cursor + 8].toInt() and 255) shl 16) or
                ((input[cursor + 9].toInt() and 255) shl 24)
            val length = (input[cursor + 10].toInt() and 255) or
                ((input[cursor + 11].toInt() and 255) shl 8)
            if (length > MAX_RECORD - HEADER_SIZE) { cursor += PREFIX.size; continue }
            val end = cursor + HEADER_SIZE + length
            if (end > input.size) break
            if (descriptor == SENSOR_DATA_WX_DESCRIPTOR) frames += input.copyOfRange(cursor, end)
            cursor = end
        }
        val keepFrom = if (input.size - cursor > MAX_RECORD) input.size - PREFIX.size + 1 else cursor
        carry = input.copyOfRange(keepFrom.coerceAtMost(input.size), input.size)
        return frames
    }

    @Synchronized fun reset() { carry = byteArrayOf() }

    companion object {
        const val MAX_RECORD = 16_384
        const val HEADER_SIZE = 12
        const val SENSOR_DATA_WX_DESCRIPTOR = 0x00100000
        private val PREFIX = byteArrayOf(4, 0, 4, 0, 0x17, 0)
    }
}

/** Validates the HID descriptor dictionary before trusting device-announced service IDs. */
internal object HeartRateSensorDescriptor {
    enum class Kind { MOTION, HEART_RATE }
    private const val MAX_PAYLOAD = 16_384
    private fun hex(value: String) = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
    private fun unsigned(value: Byte) = value.toInt() and 255
    private val motionDescriptor = hex("06 0c ff 09 11 a1 01 06 00 ff 09 23 a1 00 85 01 06 15 ff 09 04 95 01 75 40 81 02 06 0c ff 09 0d 76 b0 05 81 02 85 02 09 0e 81 02 c0 85 01 05 20 0a 0e 03 75 20 b1 02 85 02 0a 1b 03 b1 02 06 15 ff 0a 01 00 85 03 b1 02 85 04 05 20 0a 1a 03 b1 02 85 ff 06 15 ff 0a 20 00 75 08 95 e0 b1 02 c0")
    private val heartDescriptor = hex("05 20 09 16 a1 01 85 01 0a 0e 03 14 27 ff ff ff 7f 75 20 95 01 b1 02 0a b8 04 26 ff 00 75 08 95 01 81 02 06 15 ff 0a 20 01 95 01 75 08 81 02 26 ff 7f 06 15 ff 0a 21 01 95 01 75 10 81 02 75 08 95 01 15 01 25 02 1a 04 01 2a 05 01 81 00 06 00 ff 09 23 a1 00 15 00 24 06 15 ff 09 04 95 08 75 08 81 02 06 0a ff 09 12 95 04 75 08 81 02 85 02 06 0a ff 09 13 96 58 02 75 08 81 02 c0 c0")

    fun parse(data: ByteArray): Kind? = try {
        require(data.size in 8..MAX_PAYLOAD && unsigned(data[0]) == 0xd3 && data.sliceArray(1..3).all { it == 0.toByte() })
        var offset = 4
        fun take(size: Int): ByteArray {
            require(size >= 0 && size <= data.size - offset)
            val result = data.copyOfRange(offset, offset + size)
            offset = (offset + size + 3) and -4
            require(offset <= data.size)
            return result
        }
        fun word() = take(4).let { unsigned(it[0]) or (unsigned(it[1]) shl 8) or (unsigned(it[2]) shl 16) or (unsigned(it[3]) shl 24) }
        fun item(): Pair<Int, ByteArray> {
            val header = word()
            val kind = (header ushr 24) and 0x7f
            val count = header and 0xffffff
            val length = when {
                kind == 4 && count <= 64 -> 8
                kind in 8..10 && count <= MAX_PAYLOAD -> count
                kind == 11 && count <= 1 -> 0
                else -> error("Unsupported descriptor field")
            }
            return kind to take(length)
        }
        val header = word()
        val count = header and 0xffffff
        require(header and 0x7f000000 == 0x01000000 && count in 1..96)
        val values = mutableMapOf<String, Pair<Int, ByteArray>>()
        repeat(count) {
            val (keyKind, keyBytes) = item()
            require(keyKind in 8..9 && keyBytes.size <= 128)
            val key = keyBytes.toString(Charsets.US_ASCII).trimEnd('\u0000')
            require(values[key] == null)
            values[key] = item()
        }
        require(offset == data.size)
        fun number(key: String): Long? = values[key]?.takeIf { it.first == 4 && it.second.size == 8 }?.second?.let { bytes ->
            var value = 0L
            bytes.forEachIndexed { index, byte -> value = value or (unsigned(byte).toLong() shl (index * 8)) }
            value
        }
        require(number("VendorID") == 1452L)
        val names = listOfNotNull(values["AccessoryService"], values["HeartRateService"])
        require(names.size == 1 && names[0].first in 8..9)
        val name = names[0].second.toString(Charsets.US_ASCII).trimEnd('\u0000')
        val descriptor = values["ReportDescriptor"]?.takeIf { it.first == 10 }?.second ?: return null
        when {
            name == "devmotion6" && number("MaxReportSize") == 191L && descriptor.contentEquals(motionDescriptor) -> Kind.MOTION
            name == "HeartRate" && number("MaxReportSize") == 601L && descriptor.contentEquals(heartDescriptor) -> Kind.HEART_RATE
            else -> null
        }
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
}

internal class HeartRateSensorReports {
    data class Sample(val bpm: Int, val confidence: Int)
    data class Batch(val mappingChanged: Boolean, val sample: Sample?)
    var motionService: Int? = null
        private set
    var heartRateService: Int? = null
        private set
    private var heartDescriptorSeen = false
    private var lastDescriptorSequence: Int? = null

    fun receive(data: SensorDataWx): Batch = Batch(discover(data), parseSample(data))

    private fun discover(data: SensorDataWx): Boolean {
        if (data.logType !in setOf(1, 3) || data.descriptors.size !in 1..96) return false
        val announced = data.descriptors.mapNotNull { descriptor ->
            descriptor.service.takeIf { it in 1..127 }?.let { service ->
                HeartRateSensorDescriptor.parse(descriptor.bytes)?.let { service to it }
            }
        }
        val restart = data.sequence == 1 && (lastDescriptorSequence ?: 0) > 1
        if (announced.any { item -> announced.any { (item.first == it.first) != (item.second == it.second) } }) return false
        var motion = if (restart) null else motionService
        var heart = if (restart) null else heartRateService
        for ((service, kind) in announced) when (kind) {
            HeartRateSensorDescriptor.Kind.MOTION -> { if (heart == service) return false; motion = service }
            HeartRateSensorDescriptor.Kind.HEART_RATE -> { if (motion == service) return false; heart = service }
        }
        val changed = restart || motion != motionService || heart != heartRateService
        motionService = motion
        heartRateService = heart
        heartDescriptorSeen = heartDescriptorSeen || heart != null
        lastDescriptorSequence = data.sequence
        return changed
    }

    private fun parseSample(data: SensorDataWx): Sample? {
        if (data.logType !in setOf(1, 3)) return null
        val command = data.command ?: return null
        if (heartRateService != null) {
            if (command.service != heartRateService) return null
        } else if (heartDescriptorSeen || command.service !in LEGACY_SERVICES || command.service == motionService) return null
        val bytes = command.payload
        if (bytes.size != 18) return null
        val bpm = bytes[1].toInt() and 255
        val confidence = bytes[2].toInt() and 255
        if (bpm !in 30..220 || confidence < 0x80 || HEART_TAILS.none { tail -> tail.indices.all { tail[it] == bytes[15 + it] } }) return null
        return Sample(bpm, confidence)
    }

    companion object {
        val LEGACY_SERVICES = setOf(19, 20, 84)
        private val HEART_TAILS = listOf("100000", "100080", "200000", "208000", "200280", "208280")
            .map { it.chunked(2).map { value -> value.toInt(16).toByte() }.toByteArray() }
    }
}
