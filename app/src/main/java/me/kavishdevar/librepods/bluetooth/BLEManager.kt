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

package me.kavishdevar.librepods.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import me.kavishdevar.librepods.utils.BluetoothCryptography
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.iterator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Manager for Bluetooth Low Energy scanning operations specifically for AirPods
 */
@OptIn(ExperimentalEncodingApi::class)
class BLEManager(private val context: Context) {

    data class AirPodsStatus(
        val address: String,
        val lastSeen: Long = System.currentTimeMillis(),
        val rssi: Int = Int.MIN_VALUE,
        val paired: Boolean = false,
        val model: String = "Unknown",
        val leftBattery: Int? = null,
        val rightBattery: Int? = null,
        val caseBattery: Int? = null,
        val isLeftInEar: Boolean = false,
        val isRightInEar: Boolean = false,
        val isLeftCharging: Boolean = false,
        val isRightCharging: Boolean = false,
        val isCaseCharging: Boolean = false,
        val lidOpen: Boolean = false,
        val color: String = "Unknown",
        val connectionState: String = "Unknown"
    )

    data class SignalReading(
        val rssi: Int,
        val lastSeen: Long,
        val model: String,
    )

    data class FinderScanDiagnostics(
        val scannerRunning: Boolean,
        val bluetoothEnabled: Boolean,
        val locationServicesEnabled: Boolean,
        val selectedIdentityConfigured: Boolean,
        val identityResolutionKeyAvailable: Boolean,
        val nearbyAirPodsAdvertisementSeen: Boolean,
        val identityMismatchSeen: Boolean,
        val scanFailureCode: Int?,
    )

    @Volatile
    private var latestSignalReading: SignalReading? = null
    @Volatile
    private var scannerRunning = false
    @Volatile
    private var bluetoothEnabled = false
    @Volatile
    private var lastAirPodsAdvertisementAt = 0L
    @Volatile
    private var lastIdentityMismatchAt = 0L
    @Volatile
    private var lastScanFailureCode: Int? = null
    @Volatile
    private var finderScanActive = false

    fun getMostRecentStatus(): AirPodsStatus? {
        return deviceStatusMap.values.maxByOrNull { it.lastSeen }
    }

    fun getLatestSignalReading(): SignalReading? = latestSignalReading

    fun getFinderScanDiagnostics(): FinderScanDiagnostics {
        val now = System.currentTimeMillis()
        val selectedAddress = getSelectedAirPodsAddress()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return FinderScanDiagnostics(
            scannerRunning = scannerRunning,
            bluetoothEnabled = bluetoothEnabled,
            locationServicesEnabled = locationManager?.isLocationEnabled == true,
            selectedIdentityConfigured = !selectedAddress.isNullOrBlank(),
            identityResolutionKeyAvailable = sharedPreferences.contains(
                AACPManager.Companion.ProximityKeyType.IRK.name,
            ),
            nearbyAirPodsAdvertisementSeen = now - lastAirPodsAdvertisementAt < FINDER_DIAGNOSTIC_WINDOW_MS,
            identityMismatchSeen = now - lastIdentityMismatchAt < FINDER_DIAGNOSTIC_WINDOW_MS,
            scanFailureCode = lastScanFailureCode,
        )
    }

    interface AirPodsStatusListener {
        fun onDeviceStatusChanged(device: AirPodsStatus, previousStatus: AirPodsStatus?)
        fun onBroadcastFromNewAddress(device: AirPodsStatus)
        fun onLidStateChanged(lidOpen: Boolean)
        fun onEarStateChanged(device: AirPodsStatus, leftInEar: Boolean, rightInEar: Boolean)
        fun onBatteryChanged(device: AirPodsStatus)
        fun onDeviceDisappeared()
    }

    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mScanCallback: ScanCallback? = null
    private var airPodsStatusListener: AirPodsStatusListener? = null
    private val deviceStatusMap = mutableMapOf<String, AirPodsStatus>()
    private val verifiedAddresses = mutableSetOf<String>()
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var currentGlobalLidState: Boolean? = null
    private var lastBroadcastTime: Long = 0

    private val lastValidCaseBatteryMap = mutableMapOf<String, Int>()
    private var lastIdentityRejectionLogAt = 0L
    private var selectedIdentityAtScanStart: String? = null
    private val modelNames = mapOf(
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro 2",
        0x2420 to "AirPods Pro 2 (USB-C)",
        0x0220 to "AirPods 1",
        0x0F20 to "AirPods 2",
        0x1320 to "AirPods 3",
        0x1920 to "AirPods 4",
        0x1B20 to "AirPods 4 (ANC)",
        0x2720 to "AirPods Pro 3",
        0x0A20 to "AirPods Max",
        0x1F20 to "AirPods Max (USB-C)"
    )

    val colorNames = mapOf(
        0x00 to "White", 0x01 to "Black", 0x02 to "Red", 0x03 to "Blue",
        0x04 to "Pink", 0x05 to "Gray", 0x06 to "Silver", 0x07 to "Gold",
        0x08 to "Rose Gold", 0x09 to "Space Gray", 0x0A to "Dark Blue",
        0x0B to "Light Blue", 0x0C to "Yellow"
    )

    val connStates = mapOf(
        0x00 to "Disconnected", 0x04 to "Idle", 0x05 to "Music",
        0x06 to "Call", 0x07 to "Ringing", 0x09 to "Hanging Up", 0xFF to "Unknown"
    )

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanupStaleDevices()
            checkLidStateTimeout()
            cleanupHandler.postDelayed(this, CLEANUP_INTERVAL_MS)
        }
    }

    private fun getSelectedAirPodsAddress(): String? =
        sharedPreferences.getString("preferred_airpods_address", null)
            ?.takeIf(String::isNotBlank)
            ?: sharedPreferences.getString("mac_address", null)?.takeIf(String::isNotBlank)

    /** Use an unfiltered foreground scan while the finder is open. Some controllers
     * inconsistently apply manufacturer-data filters to rotating accessory adverts. */
    @SuppressLint("MissingPermission")
    fun setFinderScanActive(active: Boolean) {
        if (finderScanActive == active) return
        finderScanActive = active
        Log.i(TAG, "Nearby finder scan mode ${if (active) "enabled" else "disabled"}")
        stopScanning()
        startScanning()
    }

    fun setAirPodsStatusListener(listener: AirPodsStatusListener) {
        airPodsStatusListener = listener
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        try {
            Log.d(TAG, "Starting BLE scanner")

            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter

            if (btAdapter == null) {
                scannerRunning = false
                bluetoothEnabled = false
                Log.d(TAG, "No Bluetooth adapter available")
                return
            }

            if (mBluetoothLeScanner != null && mScanCallback != null) {
                mBluetoothLeScanner?.stopScan(mScanCallback)
                mScanCallback = null
            }

            if (!btAdapter.isEnabled) {
                scannerRunning = false
                bluetoothEnabled = false
                Log.d(TAG, "Bluetooth is disabled")
                return
            }
            bluetoothEnabled = true
            lastScanFailureCode = null

            mBluetoothLeScanner = btAdapter.bluetoothLeScanner
            val scanner = mBluetoothLeScanner
            if (scanner == null) {
                scannerRunning = false
                lastScanFailureCode = SCAN_SCANNER_UNAVAILABLE
                Log.e(TAG, "BLE scanner is unavailable on this adapter")
                return
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                // Deliver every RSSI sample immediately. Batched callbacks plus
                // address de-duplication made a device appear to have no live signal.
                .setReportDelay(0L)
                .build()

            // Match only the AirPods advertisement header. Requiring a fixed
            // 27-byte payload drops shorter valid frames from some generations.
            val manufacturerData = ByteArray(2)
            val manufacturerDataMask = ByteArray(2)

            manufacturerData[0] = 7
            manufacturerData[1] = 25

            manufacturerDataMask[0] = -1
            manufacturerDataMask[1] = -1

            selectedIdentityAtScanStart = getSelectedAirPodsAddress()
                ?.takeIf(BluetoothAdapter::checkBluetoothAddress)
            // AirPods can rotate BLE addresses. Keep the manufacturer filter in the
            // controller, then validate the selected identity with the stored IRK or
            // exact bond address in processScanResult. A strict address filter can
            // otherwise discard the rotating advertisement before that check runs.
            val scanFilter = ScanFilter.Builder()
                .setManufacturerData(76, manufacturerData, manufacturerDataMask)
                .build()
            Log.i(TAG, "Scanning AirPods advertisements; selected identity is checked in app")

            mScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    processScanResult(result)
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    for (result in results) {
                        processScanResult(result)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    scannerRunning = false
                    lastScanFailureCode = errorCode
                    Log.e(TAG, "BLE scan failed with error code: $errorCode")
                }
            }

            val scanFilters = if (finderScanActive) emptyList() else listOf(scanFilter)
            scanner.startScan(scanFilters, scanSettings, mScanCallback)
            scannerRunning = true
            Log.d(TAG, "BLE scanner started successfully")

            cleanupHandler.postDelayed(cleanupRunnable, CLEANUP_INTERVAL_MS)
        } catch (t: Throwable) {
            scannerRunning = false
            lastScanFailureCode = if (t is SecurityException) SCAN_PERMISSION_ERROR else SCAN_START_ERROR
            Log.e(TAG, "Error starting BLE scanner", t)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            if (mBluetoothLeScanner != null && mScanCallback != null) {
                Log.d(TAG, "Stopping BLE scanner")
                mBluetoothLeScanner?.stopScan(mScanCallback)
                mScanCallback = null
            }
            scannerRunning = false

            cleanupHandler.removeCallbacks(cleanupRunnable)
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping BLE scanner", t)
        }
    }

    /** Clear cached identity results when the selected paired AirPods device changes. */
    @SuppressLint("MissingPermission")
    fun refreshSelectedBondFilter() {
        val selectedBondAddress = getSelectedAirPodsAddress()
            ?.takeIf(BluetoothAdapter::checkBluetoothAddress)
        if (selectedBondAddress.equals(selectedIdentityAtScanStart, ignoreCase = true)) return
        if (mScanCallback == null) return
        Log.i(TAG, "Selected AirPods identity changed; refreshing trusted BLE results")
        deviceStatusMap.clear()
        latestSignalReading = null
        verifiedAddresses.clear()
        lastValidCaseBatteryMap.clear()
        currentGlobalLidState = null
        lastBroadcastTime = 0L
        selectedIdentityAtScanStart = selectedBondAddress
        lastAirPodsAdvertisementAt = 0L
        lastIdentityMismatchAt = 0L
        stopScanning()
        startScanning()
    }

    @SuppressLint("MissingPermission")
    fun restartNearbySignalScan() {
        latestSignalReading = null
        lastAirPodsAdvertisementAt = 0L
        lastIdentityMismatchAt = 0L
        lastScanFailureCode = null
        stopScanning()
        startScanning()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getEncryptionKeyFromPreferences(): ByteArray? {
        val keyBase64 = sharedPreferences.getString(AACPManager.Companion.ProximityKeyType.ENC_KEY.name, null)
        return if (keyBase64 != null) {
            try {
                Base64.decode(keyBase64)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode stored proximity encryption key")
                null
            }
        } else {
            null
        }
    }

    @SuppressLint("GetInstance")
    private fun decryptLastBytes(data: ByteArray, key: ByteArray): ByteArray? {
        return try {
            if (data.size < 16) {
                return null
            }

            val block = data.copyOfRange(data.size - 16, data.size)
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher.doFinal(block)
        } catch (e: Exception) {
            Log.e(TAG, "BLE payload decryption failed")
            null
        }
    }

    private fun formatBattery(byteVal: Int): Pair<Boolean, Int> {
        val charging = (byteVal and 0x80) != 0
        val level = byteVal and 0x7F
        return Pair(charging, level)
    }

    private fun processScanResult(result: ScanResult) {
        try {
            val scanRecord = result.scanRecord ?: return
            val address = result.device.address

            val manufacturerData = scanRecord.getManufacturerSpecificData(76) ?: return
            if (manufacturerData.size >= 2 &&
                manufacturerData[0].toInt() == 7 && manufacturerData[1].toInt() == 25
            ) {
                lastAirPodsAdvertisementAt = System.currentTimeMillis()
            }
            if (manufacturerData.size <= 20) return

            if (!verifiedAddresses.contains(address)) {
                val irk = getIrkFromPreferences()
                val verifiedByIrk = irk?.let { BluetoothCryptography.verifyRPA(address, it) } == true
                val selectedBondAddress = getSelectedAirPodsAddress()
                if (!BleAdvertisementIdentityPolicy.isTrusted(
                        observedAddress = address,
                        selectedBondAddress = selectedBondAddress,
                        verifiedByIrk = verifiedByIrk,
                    )
                ) {
                    lastIdentityMismatchAt = System.currentTimeMillis()
                    logIdentityRejection(irkAvailable = irk != null)
                    return
                }
                verifiedAddresses.add(address)
                Log.d(
                    TAG,
                    if (verifiedByIrk) {
                        "Verified a rotating AirPods address with the stored identity key"
                    } else {
                        "Android BLE address matches the selected paired AirPods; accepting coarse public battery data"
                    },
                )
            }

            lastBroadcastTime = System.currentTimeMillis()

            val encryptionKey = getEncryptionKeyFromPreferences()
            val decryptedData = if (encryptionKey != null) decryptLastBytes(manufacturerData, encryptionKey) else null
            val parsedStatus = (if (decryptedData != null && decryptedData.size == 16) {
                parseProximityMessageWithDecryption(address, manufacturerData, decryptedData)
            } else {
                parseProximityMessage(address, manufacturerData)
            }).copy(rssi = result.rssi)

            val previousStatus = deviceStatusMap[address]
            deviceStatusMap[address] = parsedStatus
            latestSignalReading = SignalReading(
                rssi = parsedStatus.rssi,
                lastSeen = parsedStatus.lastSeen,
                model = parsedStatus.model,
            )

            airPodsStatusListener?.let { listener ->
                if (previousStatus == null) {
                    listener.onBroadcastFromNewAddress(parsedStatus)
                    Log.d(TAG, "New AirPods advertisement detected")

                    val previousGlobalState = currentGlobalLidState
                    currentGlobalLidState = parsedStatus.lidOpen
                    if (LidStateTransitionPolicy.shouldEmitTransition(previousGlobalState, parsedStatus.lidOpen)) {
                        listener.onLidStateChanged(parsedStatus.lidOpen)
                        Log.d(TAG, "Lid state changed to ${if (parsedStatus.lidOpen) "open" else "closed"} after BLE address rotation")
                    } else if (previousGlobalState == null) {
                        Log.d(TAG, "Initial BLE lid state recorded without generating a case-open event")
                    }
                } else {
                    if (parsedStatus.copy(
                            lastSeen = previousStatus.lastSeen,
                            rssi = previousStatus.rssi,
                        ) != previousStatus
                    ) {
                        listener.onDeviceStatusChanged(parsedStatus, previousStatus)
                    }

                    if (parsedStatus.lidOpen != previousStatus.lidOpen) {
                        val previousGlobalState = currentGlobalLidState
                        currentGlobalLidState = parsedStatus.lidOpen

                        if (LidStateTransitionPolicy.shouldEmitTransition(previousGlobalState, parsedStatus.lidOpen)) {
                            listener.onLidStateChanged(parsedStatus.lidOpen)
                            Log.d(TAG, "Lid state changed from $previousGlobalState to ${parsedStatus.lidOpen}")
                        }
                    }

                    if (parsedStatus.isLeftInEar != previousStatus.isLeftInEar ||
                        parsedStatus.isRightInEar != previousStatus.isRightInEar) {
                        listener.onEarStateChanged(
                            parsedStatus,
                            parsedStatus.isLeftInEar,
                            parsedStatus.isRightInEar
                        )
                        Log.d(TAG, "Ear state changed - Left: ${parsedStatus.isLeftInEar}, Right: ${parsedStatus.isRightInEar}")
                    }

                    if (parsedStatus.leftBattery != previousStatus.leftBattery ||
                        parsedStatus.rightBattery != previousStatus.rightBattery ||
                        parsedStatus.caseBattery != previousStatus.caseBattery) {
                        listener.onBatteryChanged(parsedStatus)
                        Log.d(TAG, "Battery changed - Left: ${parsedStatus.leftBattery}, Right: ${parsedStatus.rightBattery}, Case: ${parsedStatus.caseBattery}")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error processing scan result", t)
        }
    }

    private fun logIdentityRejection(irkAvailable: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastIdentityRejectionLogAt < 15_000L) return
        lastIdentityRejectionLogAt = now
        Log.i(
            TAG,
            if (irkAvailable) {
                "Ignoring BLE advertisement: it did not match the stored identity key or selected paired address"
            } else {
                "Ignoring BLE advertisement: no identity key is available and Android did not resolve it to the selected paired address"
            },
        )
    }

    private fun parseProximityMessageWithDecryption(address: String, data: ByteArray, decrypted: ByteArray): AirPodsStatus {
        val paired = data[2].toInt() == 1
        val modelId = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val model = modelNames[modelId] ?: "Unknown ($modelId)"

        val status = data[5].toInt() and 0xFF
//        val flagsCase = data[7].toInt() and 0xFF
        val lid = data[8].toInt() and 0xFF
        val color = colorNames[data[9].toInt()] ?: "Unknown"
        val conn = connStates[data[10].toInt()] ?: "Unknown (${data[10].toInt()})"

        val primaryLeft = ((status shr 5) and 0x01) == 1
        val thisInCase = ((status shr 6) and 0x01) == 1
        val xorFactor = primaryLeft xor thisInCase

        val isLeftInEar = if (xorFactor) (status and 0x08) != 0 else (status and 0x02) != 0
        val isRightInEar = if (xorFactor) (status and 0x02) != 0 else (status and 0x08) != 0

        val isFlipped = !primaryLeft

        val leftByteIndex = if (isFlipped) 2 else 1
        val rightByteIndex = if (isFlipped) 1 else 2

        val (isLeftCharging, leftBattery) = formatBattery(decrypted[leftByteIndex].toInt() and 0xFF)
        val (isRightCharging, rightBattery) = formatBattery(decrypted[rightByteIndex].toInt() and 0xFF)

        val rawCaseBatteryByte = decrypted[3].toInt() and 0xFF
        val (isCaseCharging, rawCaseBattery) = formatBattery(rawCaseBatteryByte)

        val caseBattery = if (rawCaseBatteryByte == 0xFF || (isCaseCharging && rawCaseBattery == 127)) {
            lastValidCaseBatteryMap[address]
        } else {
            lastValidCaseBatteryMap[address] = rawCaseBattery
            rawCaseBattery
        }

        val lidOpen = ((lid shr 3) and 0x01) == 0

        return AirPodsStatus(
            address = address,
            lastSeen = System.currentTimeMillis(),
            paired = paired,
            model = model,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
            isLeftInEar = isLeftInEar,
            isRightInEar = isRightInEar,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            lidOpen = lidOpen,
            color = color,
            connectionState = conn
        )
    }

    private fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val staleCutoff = now - STALE_DEVICE_TIMEOUT_MS
        val hadDevices = deviceStatusMap.isNotEmpty()

        val staleDevices = deviceStatusMap.filter { it.value.lastSeen < staleCutoff }

        for (device in staleDevices) {
            deviceStatusMap.remove(device.key)
            Log.d(TAG, "Removed stale BLE device from tracking")
        }

        if (hadDevices && deviceStatusMap.isEmpty()) {
            currentGlobalLidState = null
            lastBroadcastTime = 0L
            latestSignalReading = null
            airPodsStatusListener?.onDeviceDisappeared()
        } else if (latestSignalReading?.let { now - it.lastSeen >= STALE_DEVICE_TIMEOUT_MS } == true) {
            latestSignalReading = deviceStatusMap.values.maxByOrNull { it.lastSeen }?.let {
                SignalReading(it.rssi, it.lastSeen, it.model)
            }
        }
    }

    private fun checkLidStateTimeout() {
        val currentTime = System.currentTimeMillis()
        if (lastBroadcastTime > 0L && currentTime - lastBroadcastTime > STALE_DEVICE_TIMEOUT_MS && currentGlobalLidState != null) {
            Log.d(TAG, "BLE lid state became stale after ${STALE_DEVICE_TIMEOUT_MS}ms without advertisements; no lid-close event emitted")
            currentGlobalLidState = null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getIrkFromPreferences(): ByteArray? {
        val irkBase64 = sharedPreferences.getString(AACPManager.Companion.ProximityKeyType.IRK.name, null)
        return if (irkBase64 != null) {
            try {
                Base64.decode(irkBase64)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode stored identity-resolution key")
                null
            }
        } else {
            null
        }
    }

    private fun parseProximityMessage(address: String, data: ByteArray): AirPodsStatus {
        val paired = data[2].toInt() == 1
        val modelId = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val model = modelNames[modelId] ?: "Unknown ($modelId)"

        val status = data[5].toInt() and 0xFF
        val podsBattery = data[6].toInt() and 0xFF
        val flagsCase = data[7].toInt() and 0xFF
        val lid = data[8].toInt() and 0xFF
        val color = colorNames[data[9].toInt()] ?: "Unknown"
        val conn = connStates[data[10].toInt()] ?: "Unknown (${data[10].toInt()})"

        val primaryLeft = ((status shr 5) and 0x01) == 1
        val thisInCase = ((status shr 6) and 0x01) == 1
        val xorFactor = primaryLeft xor thisInCase

        val isLeftInEar = if (xorFactor) (status and 0x08) != 0 else (status and 0x02) != 0
        val isRightInEar = if (xorFactor) (status and 0x02) != 0 else (status and 0x08) != 0

        val isFlipped = !primaryLeft

        val leftBatteryNibble = if (isFlipped) (podsBattery shr 4) and 0x0F else podsBattery and 0x0F
        val rightBatteryNibble = if (isFlipped) podsBattery and 0x0F else (podsBattery shr 4) and 0x0F

        val caseBattery = flagsCase and 0x0F
        val flags = (flagsCase shr 4) and 0x0F

        val isLeftCharging = if (isFlipped) (flags and 0x02) != 0 else (flags and 0x01) != 0
        val isRightCharging = if (isFlipped) (flags and 0x01) != 0 else (flags and 0x02) != 0
        val isCaseCharging = (flags and 0x04) != 0

        val lidOpen = ((lid shr 3) and 0x01) == 0

        fun decodeBattery(n: Int): Int? = when (n) {
            in 0x0..0x9 -> n * 10
            in 0xA..0xE -> 100
            0xF -> null
            else -> null
        }

        return AirPodsStatus(
            address = address,
            lastSeen = System.currentTimeMillis(),
            paired = paired,
            model = model,
            leftBattery = decodeBattery(leftBatteryNibble),
            rightBattery = decodeBattery(rightBatteryNibble),
            caseBattery = decodeBattery(caseBattery),
            isLeftInEar = isLeftInEar,
            isRightInEar = isRightInEar,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            lidOpen = lidOpen,
            color = color,
            connectionState = conn
        )
    }

    companion object {
        private const val TAG = "AirPodsBLE"
        private const val CLEANUP_INTERVAL_MS = 10000L
        private const val STALE_DEVICE_TIMEOUT_MS = 15000L
        private const val FINDER_DIAGNOSTIC_WINDOW_MS = 10000L
        private const val SCAN_PERMISSION_ERROR = -1001
        private const val SCAN_START_ERROR = -1002
        private const val SCAN_SCANNER_UNAVAILABLE = -1003
    }
}
