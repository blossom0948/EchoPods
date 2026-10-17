/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.data

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize

// TODO: Remove everything but Battery-related stuff

enum class Enums(val value: ByteArray) {
    NOISE_CANCELLATION(byteArrayOf(0x0d)),
    PREFIX(byteArrayOf(0x04, 0x00, 0x04, 0x00)),
    SETTINGS(byteArrayOf(0x09, 0x00)),
    NOISE_CANCELLATION_PREFIX(PREFIX.value + SETTINGS.value + NOISE_CANCELLATION.value),
    CONVERSATION_AWARENESS_RECEIVE_PREFIX(PREFIX.value + byteArrayOf(0x4b, 0x00, 0x02, 0x00)),
}

object BatteryComponent {
    const val LEFT = 4
    const val RIGHT = 2
    const val CASE = 8
}

object BatteryStatus {
    const val CHARGING = 1
    const val NOT_CHARGING = 2
    const val DISCONNECTED = 4
    const val OPTIMIZED_CHARGING = 5
}

@Parcelize
data class Battery(
    val component: Int,
    val level: Int,
    val status: Int,
    val isLevelKnown: Boolean = level in 0..100,
) : Parcelable {
    fun getComponentName(): String? {
        return when (component) {
            BatteryComponent.LEFT -> "LEFT"
            BatteryComponent.RIGHT -> "RIGHT"
            BatteryComponent.CASE -> "CASE"
            else -> null
        }
    }

    fun getStatusName(): String? {
        return when (status) {
            BatteryStatus.CHARGING -> "CHARGING"
            BatteryStatus.NOT_CHARGING -> "NOT_CHARGING"
            BatteryStatus.DISCONNECTED -> "DISCONNECTED"
            BatteryStatus.OPTIMIZED_CHARGING -> "OPTIMIZED_CHARGING"
            else -> null
        }
    }
}

enum class NoiseControlMode {
    OFF,  NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

class AirPodsNotifications {
    companion object {
        const val AIRPODS_CONNECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTED"
        const val AIRPODS_L2CAP_CONNECTED = "me.kavishdevar.librepods.AIRPODS_L2CAP_CONNECTED"
        const val AIRPODS_DATA = "me.kavishdevar.librepods.AIRPODS_DATA"
        const val EAR_DETECTION_DATA = "me.kavishdevar.librepods.EAR_DETECTION_DATA"
        const val ANC_DATA = "me.kavishdevar.librepods.ANC_DATA"
        const val BATTERY_DATA = "me.kavishdevar.librepods.BATTERY_DATA"
        const val CA_DATA = "me.kavishdevar.librepods.CA_DATA"
        const val AIRPODS_DISCONNECTED = "me.kavishdevar.librepods.AIRPODS_DISCONNECTED"
        const val AIRPODS_CONNECTION_DETECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTION_DETECTED"
        const val DISCONNECT_RECEIVERS = "me.kavishdevar.librepods.DISCONNECT_RECEIVERS"
        const val EQ_DATA = "me.kavishdevar.librepods.HEADPHONE_ACCOMMODATION"
        const val AIRPODS_INFORMATION_UPDATED = "me.kavishdevar.librepods.AIRPODS_INFORMATION_UPDATED"
    }

    class EarDetection {
        private val notificationBit = 6.toByte()
        private val notificationPrefix = Enums.PREFIX.value + notificationBit

        var status: List<Byte> = listOf(0x01, 0x01)

        fun setStatus(data: ByteArray) {
            status = listOf(data[6], data[7])
        }

        fun isEarDetectionData(data: ByteArray): Boolean {
            if (data.size != 8) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }
    }

    class ANC {
        private val notificationPrefix = Enums.NOISE_CANCELLATION_PREFIX.value

        var status: Int = 1
            private set

        fun isANCData(data: ByteArray): Boolean {
            if (data.size != 11) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setStatus(data: ByteArray) {
            when (data.size) {
                // if the whole packet is given
                11 -> {
                    status = data[7].toInt()
                }
                // if only the data is given
                1 -> {
                    status = data[0].toInt()
                }
                // if the value of control command is given
                4 -> {
                    status = data[0].toInt()
                }
                else -> {
                    Log.d("ANC", "Invalid ANC data size: ${data.size}")
                }
            }
        }

        val name: String =
            when (status) {
                1 -> "OFF"
                2 -> "ON"
                3 -> "TRANSPARENCY"
                4 -> "ADAPTIVE"
                else -> "UNKNOWN"
            }

    }

    class BatteryNotification {
        private var first: Battery = Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED, false)
        private var second: Battery = Battery(BatteryComponent.RIGHT, 0, BatteryStatus.DISCONNECTED, false)
        private var case: Battery = Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED, false)
        private var firstSource = BatterySource.NONE
        private var secondSource = BatterySource.NONE
        private var caseSource = BatterySource.NONE

        private enum class BatterySource {
            NONE,
            BLE,
            AACP,
        }

        /** Restore percentages as stale observations; charging is never restored across a process. */
        fun restoreLastKnownBattery(leftLevel: Int?, rightLevel: Int?, caseLevel: Int?) {
            first = Battery(BatteryComponent.LEFT, leftLevel?.takeIf { it in 0..100 } ?: 0, BatteryStatus.DISCONNECTED, leftLevel?.let { it in 0..100 } == true)
            second = Battery(BatteryComponent.RIGHT, rightLevel?.takeIf { it in 0..100 } ?: 0, BatteryStatus.DISCONNECTED, rightLevel?.let { it in 0..100 } == true)
            case = Battery(BatteryComponent.CASE, caseLevel?.takeIf { it in 0..100 } ?: 0, BatteryStatus.DISCONNECTED, caseLevel?.let { it in 0..100 } == true)
            firstSource = BatterySource.NONE
            secondSource = BatterySource.NONE
            caseSource = BatterySource.NONE
        }

        fun isBatteryData(data: ByteArray): Boolean {
            if (data.joinToString("") { "%02x".format(it) }.startsWith("040004000400")) {
                Log.d("BatteryNotification", "Battery data starts with 040004000400. Most likely is a battery packet.")
            } else {
                return false
            }
            if (data.size != 22) {
                Log.d("BatteryNotification", "Battery data size is not 22, probably being used with Airpods with fewer or more battery count.")
                return false
            }
            Log.d("BatteryNotification", data.joinToString("") { "%02x".format(it) }.startsWith("040004000400").toString())
            return data.joinToString("") { "%02x".format(it) }.startsWith("040004000400")
        }

        fun setBatteryDirect(
            leftLevel: Int,
            leftCharging: Boolean,
            rightLevel: Int,
            rightCharging: Boolean,
            caseLevel: Int,
            caseCharging: Boolean
        ) {
            first = Battery(BatteryComponent.LEFT, leftLevel, if (leftCharging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
            second = Battery(BatteryComponent.RIGHT, rightLevel, if (rightCharging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
            case = Battery(BatteryComponent.CASE, caseLevel, if (caseCharging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
            firstSource = BatterySource.BLE
            secondSource = BatterySource.BLE
            caseSource = BatterySource.BLE
        }

        /** Merge public/encrypted BLE readings without allowing coarse data to replace an
         * active AACP reading. Missing components are intentionally left untouched. */
        fun mergeBleBattery(
            leftLevel: Int?,
            leftCharging: Boolean?,
            rightLevel: Int?,
            rightCharging: Boolean?,
            caseLevel: Int?,
            caseCharging: Boolean?,
        ) {
            first = mergeBleComponent(first, firstSource, leftLevel, leftCharging).also {
                if (it !== first || (leftLevel != null && firstSource != BatterySource.AACP)) {
                    firstSource = BatterySource.BLE
                }
            }
            second = mergeBleComponent(second, secondSource, rightLevel, rightCharging).also {
                if (it !== second || (rightLevel != null && secondSource != BatterySource.AACP)) {
                    secondSource = BatterySource.BLE
                }
            }
            case = mergeBleComponent(case, caseSource, caseLevel, caseCharging).also {
                if (it !== case || (caseLevel != null && caseSource != BatterySource.AACP)) {
                    caseSource = BatterySource.BLE
                }
            }
        }

        private fun mergeBleComponent(
            previous: Battery,
            source: BatterySource,
            level: Int?,
            charging: Boolean?,
        ): Battery {
            if (level == null || level !in 0..100) return previous
            if (source == BatterySource.AACP && previous.status != BatteryStatus.DISCONNECTED) {
                return previous
            }
            val status = when (charging) {
                true -> BatteryStatus.CHARGING
                false -> BatteryStatus.NOT_CHARGING
                null -> previous.status.takeIf { it != BatteryStatus.DISCONNECTED }
                    ?: BatteryStatus.NOT_CHARGING
            }
            return Battery(previous.component, level, status)
        }

        fun setBattery(data: ByteArray) {
            if (data.size != 22) {
                return
            }
            first = parseBatteryComponent(data, 7, 9, 10, first).also {
                if (data[10].toInt() != BatteryStatus.DISCONNECTED && it !== first) {
                    firstSource = BatterySource.AACP
                }
            }
            second = parseBatteryComponent(data, 12, 14, 15, second).also {
                if (data[15].toInt() != BatteryStatus.DISCONNECTED && it !== second) {
                    secondSource = BatterySource.AACP
                }
            }
            case = parseBatteryComponent(data, 17, 19, 20, case).also {
                if (data[20].toInt() != BatteryStatus.DISCONNECTED && it !== case) {
                    caseSource = BatterySource.AACP
                }
            }
        }

        private fun parseBatteryComponent(
            data: ByteArray,
            componentIndex: Int,
            levelIndex: Int,
            statusIndex: Int,
            previous: Battery,
        ): Battery {
            val status = data[statusIndex].toInt()
            if (status !in setOf(
                    BatteryStatus.CHARGING,
                    BatteryStatus.NOT_CHARGING,
                    BatteryStatus.DISCONNECTED,
                    BatteryStatus.OPTIMIZED_CHARGING,
                )
            ) {
                return previous
            }

            if (status == BatteryStatus.DISCONNECTED) {
                return Battery(previous.component, previous.level, status, previous.isLevelKnown)
            }

            val component = data[componentIndex].toInt()
            val level = data[levelIndex].toInt()
            if (component !in setOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE) || level !in 0..100) {
                return previous
            }

            return Battery(component, level, status)
        }

        fun getBattery(): List<Battery> {
            val left = if (first.component == BatteryComponent.LEFT) first else second
            val right = if (first.component == BatteryComponent.LEFT) second else first
            return listOf(left, right, case)
        }
    }

    class ConversationalAwarenessNotification {
        @Suppress("PrivatePropertyName")
        private val NOTIFICATION_PREFIX = Enums.CONVERSATION_AWARENESS_RECEIVE_PREFIX.value

        var status: Byte = 0
            private set

        fun isConversationalAwarenessData(data: ByteArray): Boolean {
            if (data.size != 10) {
                return false
            }
            val prefixHex = NOTIFICATION_PREFIX.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setData(data: ByteArray) {
            status = data[9]
        }
    }
}

fun isHeadTrackingData(data: ByteArray): Boolean {
    if (data.size <= 60) return false

    val prefixPattern = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
        0x10, 0x00
    )

    for (i in prefixPattern.indices) {
        if (data[i] != prefixPattern[i]) return false
    }

    if (data[10] != 0x44.toByte() && data[10] != 0x45.toByte()) return false

    if (data[11] != 0x00.toByte()) return false

    return true
}
