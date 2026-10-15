package com.galaxyairpods.data.bluetooth

import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.EarbudSide
import com.galaxyairpods.domain.control.StemPressType
import kotlin.math.roundToInt

/**
 * Small, strict AAP (AirPods Accessory Protocol) codec used by the classic
 * BR/EDR probe.  This file deliberately contains no socket or Android code so
 * the wire parser can be tested without a device.
 */
internal object AapBatteryProtocol {
    const val CLASSIC_PSM = 0x1001
    const val MESSAGE_PACKET_TYPE = 0x0004
    const val BATTERY_COMMAND = 0x0004
    const val EAR_DETECTION_COMMAND = 0x0006
    const val CONTROL_COMMAND = 0x0009
    const val CONVERSATION_AWARENESS_COMMAND = 0x004B
    const val INFORMATION_COMMAND = 0x001D
    const val HEAD_TRACKING_COMMAND = 0x0017
    const val STEM_PRESS_COMMAND = 0x0019

    private val DATA_HEADER = byteArrayOf(0x04, 0x00, 0x04, 0x00)

    val handshake: ByteArray = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    val keyRequest: ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x30, 0x00, 0x05, 0x00,
    )

    /**
     * Enables the accessory feature set used by AACP battery notifications.
     *
     * Some AirPods firmware does not emit the complete component battery
     * notification (especially the case entry) until this request is sent
     * after the handshake. This command is never a battery value; only a
     * subsequent valid 0x0004 frame can promote a sample.
     */
    val featureFlags: ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xD7.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /**
     * Notification masks used by different AirPods/Samsung generations.
     *
     * v0.3.8 registered both EF and FF. Later media-control changes reduced
     * this to one mask (eventually FE), which still delivered ear events on
     * some phones but could omit the 0x0004 battery notification. Register
     * the known-compatible masks in one session so the battery path keeps the
     * old working behaviour while retaining the newer ear-detection mask.
     */
    val notificationProfiles: List<Pair<String, ByteArray>> = listOf(
        "FE_EAR_COMPAT" to byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(),
        ),
        "EF_COMPAT" to byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xEF.toByte(), 0xFF.toByte(),
        ),
        "FF" to byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        ),
    )

    /**
     * The v0.3.8 client sent the two original masks immediately after the
     * handshake. Keep that preflight for firmware that only applies the
     * registration during the initial session setup; the response-gated full
     * registration in the client remains the normal path.
     */
    val legacyPreflightNotificationProfiles: List<Pair<String, ByteArray>> = listOf(
        notificationProfiles.first { it.first == "EF_COMPAT" },
        notificationProfiles.first { it.first == "FF" },
    )

    fun parseFrame(raw: ByteArray): AapFrame? {
        if (raw.size < 4) return null
        val packetType = littleEndian16(raw, 0)
        val service = littleEndian16(raw, 2)
        return when (packetType) {
            0x0001 -> {
                if (raw.size < 18) return null
                AapFrame.ConnectResponse(
                    length = raw.size,
                    service = service,
                    status = littleEndian16(raw, 4),
                )
            }
            MESSAGE_PACKET_TYPE -> {
                if (raw.size < 6) return null
                AapFrame.Message(
                    length = raw.size,
                    command = littleEndian16(raw, 4),
                    payload = raw.copyOfRange(6, raw.size),
                )
            }
            else -> AapFrame.Other(packetType = packetType, length = raw.size)
        }
    }

    /**
     * Decodes command 0x0004 exactly.  Unknown components are ignored, but a
     * malformed frame or invalid percentage is rejected in full rather than
     * being turned into a guessed battery value.
     */
    fun parseBattery(frame: AapFrame.Message): AapBatterySnapshot? {
        if (frame.command != BATTERY_COMMAND) return null
        val payload = frame.payload
        if (payload.isEmpty()) return null

        val count = payload[0].u8()
        if (count > MAX_COMPONENTS) return null
        val expectedPayloadLength = 1 + count * COMPONENT_SIZE
        if (payload.size != expectedPayloadLength) return null

        var left: AapBatteryReading? = null
        var right: AapBatteryReading? = null
        var case: AapBatteryReading? = null
        var offset = 1
        repeat(count) {
            val component = payload[offset].u8()
            val percent = payload[offset + 2].u8()
            val status = payload[offset + 3].u8()
            offset += COMPONENT_SIZE

            // 0x7F and 0xFF are observed invalid/disconnected markers; all
            // values over 100 are invalid for a percentage.
            if (percent > 100 || percent == 0x7F || percent == 0xFF) return@repeat
            if (status !in VALID_STATUSES) return@repeat
            // A disconnected component is not a fresh battery sample. On
            // Samsung it is commonly reported as 0%, and accepting it here
            // would overwrite the last real pod value when the case closes.
            if (status == STATUS_DISCONNECTED) return@repeat
            val reading = AapBatteryReading(percent, status.toCharging())
            when (component) {
                COMPONENT_LEFT -> left = reading
                COMPONENT_RIGHT -> right = reading
                COMPONENT_CASE -> case = reading
                // 0x01 means one/unknown earbud. It must not be copied to L/R.
            }
        }

        if (left == null && right == null && case == null) return null
        return AapBatterySnapshot(
            left = left,
            right = right,
            case = case,
            componentCount = count,
        )
    }

    /**
     * AAP ear detection is a fixed two-byte payload after the message header:
     * primary pod status, then secondary pod status. The public observation is
     * a variable-length stream in practice, so accept only the exact frame
     * length after the message parser has validated the header.
     *
     * Some AirPods Pro sessions on Samsung send 0x03 for a pod whose AAP
     * placement is temporarily unavailable (for example, the other pod has
     * dropped from the active session). It is a known non-in-ear state for
     * wear transitions, not a malformed packet. We retain it as
     * DISCONNECTED so callers do not mistake it for IN_CASE.
     */
    fun parseEarDetection(frame: AapFrame.Message): AapEarDetectionSnapshot? {
        if (frame.command != EAR_DETECTION_COMMAND || frame.payload.size != 2) return null
        val primary = parseEarStatus(frame.payload[0].u8()) ?: return null
        val secondary = parseEarStatus(frame.payload[1].u8()) ?: return null
        return AapEarDetectionSnapshot(primary = primary, secondary = secondary)
    }

    /**
     * Parses a fixed-size AACP control notification.  The accessory reserves
     * four bytes for the value and pads shorter values with zeroes.  Preserve
     * one zero byte because zero is a valid value for microphone mode and a
     * few timing controls.
     */
    fun parseControlCommand(frame: AapFrame.Message): AapControlSnapshot? {
        if (frame.command != CONTROL_COMMAND) return null
        val payload = frame.payload
        if (payload.size !in 2..5) return null
        val identifier = AapControlId.fromWireValue(payload[0].u8()) ?: return null
        val rawValue = payload.copyOfRange(1, payload.size)
        val trimmed = rawValue.dropLastWhile { it == 0.toByte() }.toByteArray()
        return AapControlSnapshot(
            identifier = identifier,
            value = if (trimmed.isEmpty()) byteArrayOf(0) else trimmed,
        )
    }

    /** A stem event contains the physical press type and the originating bud. */
    fun parseStemPress(frame: AapFrame.Message): AapStemPressSnapshot? {
        if (frame.command != STEM_PRESS_COMMAND || frame.length != 8 || frame.payload.size != 2) return null
        val pressType = StemPressType.fromWireValue(frame.payload[0].u8()) ?: return null
        val side = EarbudSide.fromWireValue(frame.payload[1].u8()) ?: return null
        return AapStemPressSnapshot(pressType = pressType, side = side)
    }

    /** Head-tracking notifications have a fixed sensor block in the AAP frame. */
    fun parseHeadTracking(frame: AapFrame.Message): AapHeadTrackingSnapshot? {
        if (frame.command != HEAD_TRACKING_COMMAND || frame.length < 70 || frame.payload.size < 64) return null
        // AAP byte 8 identifies the orientation sensor packet family. Reject
        // unrelated 0x17 control/status messages that happen to be long enough.
        if (frame.payload[2].u8() != 0x10) return null
        val orientation2 = signedLittleEndian16(frame.payload, HEAD_ORIENTATION_2_OFFSET)
        val orientation3 = signedLittleEndian16(frame.payload, HEAD_ORIENTATION_3_OFFSET)
        // Convert the coupled AirPods orientation axes into signed yaw/pitch
        // hundredths of a degree. The sensor marker can still vary by model,
        // so the consumer treats this as experimental and requires repeated,
        // fresh directional evidence before acting.
        val yaw = (((orientation2 - orientation3) / 2.0) / 32_000.0 * 18_000.0)
            .roundToInt().coerceIn(-7_000, 7_000)
        val pitch = (((orientation2 + orientation3) / 2.0) / 32_000.0 * 18_000.0)
            .roundToInt().coerceIn(-9_000, 9_000)
        return AapHeadTrackingSnapshot(
            horizontal = yaw,
            vertical = pitch,
        )
    }

    /** Starts the short-lived AAP head-tracking stream used only during a call. */
    val startHeadTrackingPacket: ByteArray = DATA_HEADER + byteArrayOf(
        HEAD_TRACKING_COMMAND.toByte(), 0x00,
        0x00, 0x00, 0x10, 0x00, 0x10, 0x00, 0x08, 0xA1.toByte(), 0x02,
        0x42, 0x0B, 0x08, 0x0E, 0x10, 0x02, 0x1A, 0x05, 0x01,
        0x40, 0x9C.toByte(), 0x00, 0x00,
    )

    /** Stops the head-tracking stream when the call ends or is handled. */
    val stopHeadTrackingPacket: ByteArray = DATA_HEADER + byteArrayOf(
        HEAD_TRACKING_COMMAND.toByte(), 0x00,
        0x00, 0x00, 0x10, 0x00, 0x11, 0x00, 0x08, 0x7E, 0x10, 0x02,
        0x42, 0x0B, 0x08, 0x4E, 0x10, 0x02, 0x1A, 0x05, 0x01,
        0x00, 0x00, 0x00, 0x00,
    )

    /**
     * Builds the same padded control packet shape used by the public AACP
     * implementations.  Values longer than four bytes are rejected rather
     * than silently truncating a user setting.
     */
    fun createControlCommandPacket(identifier: AapControlId, value: ByteArray): ByteArray {
        require(value.size <= CONTROL_VALUE_SIZE) { "AACP control values are limited to four bytes" }
        val packet = DATA_HEADER + byteArrayOf(
            CONTROL_COMMAND.toByte(),
            0x00,
            identifier.wireValue.toByte(),
        ) + ByteArray(CONTROL_VALUE_SIZE)
        value.copyInto(packet, destinationOffset = DATA_HEADER.size + 3)
        return packet
    }

    /** Rename is a session command, not a persisted Android display name. */
    fun createRenamePacket(name: String): ByteArray {
        val nameBytes = name.trim().toByteArray(Charsets.UTF_8)
        require(nameBytes.isNotEmpty()) { "AirPods name must not be empty" }
        require(nameBytes.size <= 255) { "AirPods name is too long" }
        return DATA_HEADER + byteArrayOf(
            0x1A,
            0x00,
            0x01,
            nameBytes.size.toByte(),
            0x00,
        ) + nameBytes
    }

    private fun parseEarStatus(value: Int): AapEarStatus? = when (value) {
        0x00 -> AapEarStatus.IN_EAR
        0x01 -> AapEarStatus.OUT_OF_EAR
        0x02 -> AapEarStatus.IN_CASE
        0x03 -> AapEarStatus.DISCONNECTED
        else -> null
    }

    private fun signedLittleEndian16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].u8() or (bytes[offset + 1].u8() shl 8)).toShort().toInt()

    private fun Int.toCharging(): Boolean? = when (this) {
        STATUS_CHARGING, STATUS_OPTIMIZED -> true
        STATUS_NOT_CHARGING, STATUS_DISCONNECTED -> false
        STATUS_UNKNOWN -> null
        else -> null
    }

    private fun littleEndian16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].u8()) or (bytes[offset + 1].u8() shl 8)

    private fun Byte.u8(): Int = toInt() and 0xFF

    private const val MAX_COMPONENTS = 64
    private const val COMPONENT_SIZE = 5
    private const val CONTROL_VALUE_SIZE = 4
    // Offsets are relative to the AACP payload, after the 4-byte service and
    // 2-byte command fields in the outer frame.
    private const val HEAD_ORIENTATION_2_OFFSET = 45
    private const val HEAD_ORIENTATION_3_OFFSET = 47
    private const val COMPONENT_LEFT = 0x04
    private const val COMPONENT_RIGHT = 0x02
    private const val COMPONENT_CASE = 0x08
    private const val STATUS_UNKNOWN = 0x00
    private const val STATUS_CHARGING = 0x01
    private const val STATUS_NOT_CHARGING = 0x02
    private const val STATUS_DISCONNECTED = 0x04
    private const val STATUS_OPTIMIZED = 0x05
    private val VALID_STATUSES = setOf(
        STATUS_UNKNOWN,
        STATUS_CHARGING,
        STATUS_NOT_CHARGING,
        STATUS_DISCONNECTED,
        STATUS_OPTIMIZED,
    )
}

internal sealed interface AapFrame {
    val length: Int

    data class ConnectResponse(
        override val length: Int,
        val service: Int,
        val status: Int,
    ) : AapFrame

    data class Message(
        override val length: Int,
        val command: Int,
        val payload: ByteArray,
    ) : AapFrame

    data class Other(
        val packetType: Int,
        override val length: Int,
    ) : AapFrame
}

internal data class AapBatteryReading(
    val percent: Int,
    val charging: Boolean?,
)

internal data class AapBatterySnapshot(
    val left: AapBatteryReading?,
    val right: AapBatteryReading?,
    val case: AapBatteryReading?,
    val componentCount: Int,
) {
    val valuesPresent: Boolean
        get() = left != null || right != null || case != null
}

internal enum class AapEarStatus {
    IN_EAR,
    OUT_OF_EAR,
    IN_CASE,
    DISCONNECTED,
}

internal data class AapEarDetectionSnapshot(
    val primary: AapEarStatus,
    val secondary: AapEarStatus,
)

internal data class AapControlSnapshot(
    val identifier: AapControlId,
    val value: ByteArray,
)

internal data class AapStemPressSnapshot(
    val pressType: StemPressType,
    val side: EarbudSide,
)

internal data class AapHeadTrackingSnapshot(
    val horizontal: Int,
    val vertical: Int,
)
