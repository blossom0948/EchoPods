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

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.services

//import me.kavishdevar.librepods.utils.CrossDevice
//import me.kavishdevar.librepods.utils.CrossDevicePackets
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.UserHandle
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.StemPressType
import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.bluetooth.ATTManagerv2
import me.kavishdevar.librepods.bluetooth.BLEManager
import me.kavishdevar.librepods.bluetooth.BluetoothConnectionManager
import me.kavishdevar.librepods.bluetooth.BluetoothSocketConnectTimeout
import me.kavishdevar.librepods.bluetooth.createBluetoothSocket
import me.kavishdevar.librepods.data.AirPodsInstance
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.ConnectionPopupStyle
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.AirPodsFormFactor
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.data.CustomEq
import me.kavishdevar.librepods.data.StemAction
import me.kavishdevar.librepods.data.XposedRemotePrefProvider
import me.kavishdevar.librepods.data.isHeadTrackingData
import me.kavishdevar.librepods.presentation.overlays.IslandType
import me.kavishdevar.librepods.presentation.overlays.IslandWindow
import me.kavishdevar.librepods.presentation.overlays.LowBatteryAlertWindow
import me.kavishdevar.librepods.presentation.overlays.PopupWindow
import me.kavishdevar.librepods.presentation.widgets.BatteryWidget
import me.kavishdevar.librepods.presentation.widgets.BatteryWidgetState
import me.kavishdevar.librepods.presentation.widgets.WidgetBatteryComponent
import me.kavishdevar.librepods.presentation.widgets.NoiseControlWidget
import me.kavishdevar.librepods.utils.GestureDetector
import me.kavishdevar.librepods.utils.EarWearAction
import me.kavishdevar.librepods.utils.EarWearPosition
import me.kavishdevar.librepods.utils.EarWearTransitionPolicy
import me.kavishdevar.librepods.utils.CallGesturePolicy
import me.kavishdevar.librepods.utils.PhoneCallPhase
import me.kavishdevar.librepods.utils.BluetoothConnectionEventPolicy
import me.kavishdevar.librepods.utils.BluetoothProfileConnectPolicy
import me.kavishdevar.librepods.utils.HeadTracking
import me.kavishdevar.librepods.utils.MediaController
import me.kavishdevar.librepods.utils.LowBatteryAlertPolicy
import me.kavishdevar.librepods.utils.SystemApisUtils
import me.kavishdevar.librepods.utils.SystemApisUtils.DEVICE_TYPE_UNTETHERED_HEADSET
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_COMPANION_APP
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_DEVICE_TYPE
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_MAIN_ICON
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_MANUFACTURER_NAME
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_MODEL_NAME
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_CASE_BATTERY
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_CASE_CHARGING
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_CASE_ICON
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_CASE_LOW_BATTERY_THRESHOLD
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_LEFT_BATTERY
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_LEFT_CHARGING
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_LEFT_ICON
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_LEFT_LOW_BATTERY_THRESHOLD
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_RIGHT_BATTERY
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_RIGHT_CHARGING
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_RIGHT_ICON
import me.kavishdevar.librepods.utils.SystemApisUtils.METADATA_UNTETHERED_RIGHT_LOW_BATTERY_THRESHOLD
import me.kavishdevar.librepods.utils.XposedState
import me.kavishdevar.librepods.utils.canAttemptAacpControlSession
import me.kavishdevar.librepods.utils.hasRootlessBluetoothSupport
import com.galaxyairpods.update.UpdateManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "AirPodsService"
private const val EXTRA_ANDROID_AUDIO_PROFILE_CONNECTED = "android_audio_profile_connected"

object ServiceManager {
    private var service: AirPodsService? = null

    @Synchronized
    fun getService(): AirPodsService? {
        return service
    }

    @Synchronized
    fun setService(service: AirPodsService?) {
        this.service = service
    }
}

// @Suppress("unused")
class AirPodsService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val updateCheckScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketConnectInProgress = AtomicBoolean(false)
    var macAddress = ""
    var localMac = ""
    @Volatile
    var isAndroidAudioProfileConnected: Boolean = false
        private set
    private val connectedAndroidAudioProfiles = mutableSetOf<Int>()
    lateinit var aacpManager: AACPManager
    lateinit var attManager: ATTManagerv2
    var airpodsInstance: AirPodsInstance? = null
    var cameraActive = false
    private var disconnectedBecauseReversed = false
    private var otherDeviceTookOver = false

    data class ServiceConfig(
        var deviceName: String = "AirPods",
        var earDetectionEnabled: Boolean = true,
        var mediaControlOnWearChangeEnabled: Boolean = true,
        var popupOnWearChange: Boolean = true,
        var conversationalAwarenessPauseMusic: Boolean = false,
        var showPhoneBatteryInWidget: Boolean = true,
        var relativeConversationalAwarenessVolume: Boolean = true,
        var headGestures: Boolean = true,
        var disconnectWhenNotWearing: Boolean = false,
        var conversationalAwarenessVolume: Int = 43,
        var qsClickBehavior: String = "cycle",
        var bleOnlyMode: Boolean = false,

        // AirPods state-based takeover
        var takeoverWhenDisconnected: Boolean = true,
        var takeoverWhenIdle: Boolean = true,
        var takeoverWhenMusic: Boolean = false,
        var takeoverWhenCall: Boolean = true,

        // Phone state-based takeover
        var takeoverWhenRingingCall: Boolean = true,
        var takeoverWhenMediaStart: Boolean = true,

        var leftSinglePressAction: StemAction = StemAction.defaultActions[StemPressType.SINGLE_PRESS]!!,
        var rightSinglePressAction: StemAction = StemAction.defaultActions[StemPressType.SINGLE_PRESS]!!,

        var leftDoublePressAction: StemAction = StemAction.defaultActions[StemPressType.DOUBLE_PRESS]!!,
        var rightDoublePressAction: StemAction = StemAction.defaultActions[StemPressType.DOUBLE_PRESS]!!,

        var leftTriplePressAction: StemAction = StemAction.defaultActions[StemPressType.TRIPLE_PRESS]!!,
        var rightTriplePressAction: StemAction = StemAction.defaultActions[StemPressType.TRIPLE_PRESS]!!,

        var leftLongPressAction: StemAction = StemAction.defaultActions[StemPressType.LONG_PRESS]!!,
        var rightLongPressAction: StemAction = StemAction.defaultActions[StemPressType.LONG_PRESS]!!,

        var cameraAction: StemPressType? = null,

        // AirPods device information
        var airpodsName: String = "",
        var airpodsModelNumber: String = "",
        var airpodsManufacturer: String = "",
        var airpodsSerialNumber: String = "",
        var airpodsLeftSerialNumber: String = "",
        var airpodsRightSerialNumber: String = "",
        var airpodsVersion1: String = "",
        var airpodsVersion2: String = "",
        var airpodsVersion3: String = "",
        var airpodsHardwareRevision: String = "",
        var airpodsUpdaterIdentifier: String = "",

        // phone's mac, needed for tipi
        var selfMacAddress: String = ""
    )

    private lateinit var config: ServiceConfig

    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    private lateinit var sharedPreferencesLogs: SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences
    private val packetLogKey = "packet_log"
    private val _packetLogsFlow = MutableStateFlow<Set<String>>(emptySet())
    val packetLogsFlow: StateFlow<Set<String>> get() = _packetLogsFlow

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: TelephonyCallback
    private val maxLogEntries = 1000
    private val inMemoryLogs = mutableSetOf<String>()

    @Volatile private var handleIncomingCallOnceConnected = false
    @Volatile private var phoneCallPhase = PhoneCallPhase.IDLE
    @Volatile private var callGestureDecisionRequested = false
    private var lastWearPosition: EarWearPosition? = null
    @Volatile private var pendingWearResume = false
    @Volatile private var wearResumeRouteAttempts = 0
    private val wearResumeHandler = Handler(Looper.getMainLooper())
    private val wearResumeRunnable = object : Runnable {
        override fun run() {
            if (!pendingWearResume) return
            if (!MediaController.iPausedTheMedia) {
                pendingWearResume = false
                wearResumeRouteAttempts = 0
                Log.i(TAG, "Cancelled pending wear resume: media is no longer paused by this app")
                return
            }
            if (MediaController.isAirPodsAudioRouteActive(logResult = false)) {
                pendingWearResume = false
                wearResumeRouteAttempts = 0
                MediaController.sendPlay()
                Log.i(TAG, "Automatic wear policy resumed media after the AirPods route became active")
                return
            }

            wearResumeRouteAttempts += 1
            if (wearResumeRouteAttempts < MAX_WEAR_RESUME_ROUTE_ATTEMPTS) {
                wearResumeHandler.postDelayed(this, WEAR_RESUME_ROUTE_RETRY_MS)
            } else {
                pendingWearResume = false
                wearResumeRouteAttempts = 0
                Log.w(TAG, "Timed out waiting for the AirPods audio route; media remains paused")
            }
        }
    }

    lateinit var bleManager: BLEManager

    companion object {
        private const val WEAR_RESUME_ROUTE_RETRY_MS = 250L
        private const val MAX_WEAR_RESUME_ROUTE_ATTEMPTS = 32
        private const val LOW_BATTERY_CHANNEL_ID = "airpods_low_battery_alert_v1"
        private const val LOW_BATTERY_NOTIFICATION_ID = 7_200
        private const val LOW_BATTERY_QUEUE_LIMIT = 8

        init {
            System.loadLibrary("bluetooth_socket")
        }
    }

    private val bleStatusListener = object : BLEManager.AirPodsStatusListener {
        @SuppressLint("NewApi")
        override fun onDeviceStatusChanged(
            device: BLEManager.AirPodsStatus, previousStatus: BLEManager.AirPodsStatus?
        ) {
            Log.d(TAG, "Device status changed")
            updateBatteryFromBle(device)
        }

        override fun onBroadcastFromNewAddress(device: BLEManager.AirPodsStatus) {
            Log.d(TAG, "New address detected")
            updateBatteryFromBle(device)
        }

        override fun onLidStateChanged(
            lidOpen: Boolean,
        ) {
            if (lidOpen) {
                Log.d(TAG, "Lid opened")
                showConnectionPopup(
                    this@AirPodsService,
                    getSharedPreferences("settings", MODE_PRIVATE).getString("name", "AirPods Pro")
                        ?: "AirPods",
                    IslandType.CASE_OPENED
                )
                bleManager.getMostRecentStatus()?.let(::updateBatteryFromBle)
                sendBatteryBroadcast()
            } else {
                Log.d(TAG, "Lid closed")
            }
        }

        override fun onEarStateChanged(
            device: BLEManager.AirPodsStatus, leftInEar: Boolean, rightInEar: Boolean
        ) {
            Log.d(TAG, "Ear state changed - Left: $leftInEar, Right: $rightInEar")

            // In BLE-only mode, ear detection is purely based on BLE data
            if (config.bleOnlyMode) {
                Log.d(TAG, "BLE-only mode: ear detection from BLE data")
            }
        }

        override fun onBatteryChanged(device: BLEManager.AirPodsStatus) {
            updateBatteryFromBle(device)
            Log.d(TAG, "Battery changed")
        }

        override fun onDeviceDisappeared() {
            Log.d(TAG, "All disappeared")
            updateNotificationContent(
                false
            )
        }
    }

    fun isBluetoothSocketExempted(): Boolean {
        return try {
            BluetoothSocket::class.java.declaredConstructors // will throw if still blocked
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag", "HardwareIds")
    override fun onCreate() {
        super.onCreate()
        // Promote before doing initialization; onStartCommand repeats this safely
        // for later starts through the per-instance idempotency guard below.
        if (!startForegroundNotification()) return
        if (!BuildConfig.PLAY_BUILD) {
            updateCheckScope.launch {
                val manager = UpdateManager.shared(this@AirPodsService)
                while (isActive) {
                    manager.check(automatic = true)
                    delay(30 * 60 * 1000L)
                }
            }
        }
        Log.i(TAG, "lib exempt worked: ${isBluetoothSocketExempted()}")

        sharedPreferencesLogs = getSharedPreferences("packet_logs", MODE_PRIVATE)

        // Earlier builds persisted raw AAP packets, which may contain proximity keys.
        // Purge those entries once; subsequent diagnostics retain metadata only.
        if (!sharedPreferencesLogs.getBoolean("packet_log_redaction_migrated_v1", false)) {
            sharedPreferencesLogs.edit {
                remove(packetLogKey)
                putBoolean("packet_log_redaction_migrated_v1", true)
            }
        }

        inMemoryLogs.addAll(
            sharedPreferencesLogs.getStringSet(packetLogKey, emptySet()) ?: emptySet()
        )
        _packetLogsFlow.value = inMemoryLogs.toSet()

        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        // AACP socket state never survives process/service recreation. Do not
        // resurrect the old persisted "connected" bit as a live capability.
        sharedPreferences.edit { putBoolean("connection_successful", false) }
        val cachedBattery = BatteryWidget.readCachedLevels(this)
        batteryNotification.restoreLastKnownBattery(
            leftLevel = cachedBattery.left,
            rightLevel = cachedBattery.right,
            caseLevel = cachedBattery.case,
        )
        initializeConfig()

        aacpManager = AACPManager()
        initializeAACPManagerCallback()

        attManager = ATTManagerv2()

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        localMac = config.selfMacAddress
        if (localMac.isEmpty()) {
            if (checkSelfPermission("android.permission.LOCAL_MAC_ADDRESS") == PackageManager.PERMISSION_GRANTED) {
                val bluetoothManager = getSystemService(BluetoothManager::class.java)
                val bluetoothAdapter = bluetoothManager.adapter
                localMac = bluetoothAdapter.address
                config.selfMacAddress = localMac
                sharedPreferences.edit { putString("self_mac_address", localMac) }
            } else {
                localMac = ""
                updateCheckScope.launch {
                    val resolvedMac = runCatching {
                        val process = Runtime.getRuntime().exec(
                            arrayOf("su", "-c", "settings get secure bluetooth_address")
                        )
                        if (!process.waitFor(1_500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            process.destroyForcibly()
                            ""
                        } else if (process.exitValue() == 0) {
                            process.inputStream.bufferedReader().use { it.readLine()?.trim().orEmpty() }
                        } else {
                            ""
                        }
                    }.onFailure { exception ->
                        Log.d(TAG, "Root-only local Bluetooth address lookup unavailable: ${exception.message}")
                    }.getOrDefault("")
                    if (resolvedMac.isNotBlank()) {
                        localMac = resolvedMac
                        config.selfMacAddress = resolvedMac
                        sharedPreferences.edit { putString("self_mac_address", resolvedMac) }
                    }
                }
            }
        }

        ServiceManager.setService(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            initGestureDetector()
        } else {
            gestureDetector = null
            config.headGestures = false
            sharedPreferences.edit { putBoolean("head_gestures", false) }
            Log.d(TAG, "Head gestures disabled as device is running Android 9 or below")
        }

        bleManager = BLEManager(this)
        bleManager.setAirPodsStatusListener(bleStatusListener)

        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)

        with(sharedPreferences) {
            edit {
                if (!contains("conversational_awareness_pause_music")) putBoolean(
                    "conversational_awareness_pause_music", false
                )
                if (!contains("personalized_volume")) putBoolean("personalized_volume", false)
                if (!contains("automatic_ear_detection")) putBoolean(
                    "automatic_ear_detection", true
                )
                if (!contains("long_press_nc")) putBoolean("long_press_nc", true)
                if (!contains("show_phone_battery_in_widget")) putBoolean(
                    "show_phone_battery_in_widget", true
                )
                if (!contains("single_anc")) putBoolean("single_anc", true)
                if (!contains("long_press_transparency")) putBoolean(
                    "long_press_transparency", true
                )
                if (!contains("conversational_awareness")) putBoolean(
                    "conversational_awareness", true
                )
                if (!contains("relative_conversational_awareness_volume")) putBoolean(
                    "relative_conversational_awareness_volume", true
                )
                if (!contains("long_press_adaptive")) putBoolean("long_press_adaptive", true)
                if (!contains("loud_sound_reduction")) putBoolean("loud_sound_reduction", true)
                if (!contains("long_press_off")) putBoolean("long_press_off", false)
                if (!contains("volume_control")) putBoolean("volume_control", true)
                if (!contains("head_gestures")) putBoolean("head_gestures", true)
                if (!contains("disconnect_when_not_wearing")) putBoolean(
                    "disconnect_when_not_wearing", false
                )

                // AirPods state-based takeover
                if (!contains("takeover_when_disconnected")) putBoolean(
                    "takeover_when_disconnected", false
                )
                if (!contains("takeover_when_idle")) putBoolean("takeover_when_idle", false)
                if (!contains("takeover_when_music")) putBoolean("takeover_when_music", false)
                if (!contains("takeover_when_call")) putBoolean("takeover_when_call", false)

                // Phone state-based takeover
                if (!contains("takeover_when_ringing_call")) putBoolean(
                    "takeover_when_ringing_call", false
                )
                if (!contains("takeover_when_media_start")) putBoolean(
                    "takeover_when_media_start", false
                )

                if (!contains("adaptive_strength")) putInt("adaptive_strength", 51)
                if (!contains("tone_volume")) putInt("tone_volume", 75)
                if (!contains("conversational_awareness_volume")) putInt(
                    "conversational_awareness_volume", 43
                )

                if (!contains("qs_click_behavior")) putString("qs_click_behavior", "cycle")
                if (!contains("name")) putString("name", "AirPods")

                if (!contains("left_single_press_action")) putString(
                    "left_single_press_action",
                    StemAction.defaultActions[StemPressType.SINGLE_PRESS]!!.name
                )
                if (!contains("right_single_press_action")) putString(
                    "right_single_press_action",
                    StemAction.defaultActions[StemPressType.SINGLE_PRESS]!!.name
                )
                if (!contains("left_double_press_action")) putString(
                    "left_double_press_action",
                    StemAction.defaultActions[StemPressType.DOUBLE_PRESS]!!.name
                )
                if (!contains("right_double_press_action")) putString(
                    "right_double_press_action",
                    StemAction.defaultActions[StemPressType.DOUBLE_PRESS]!!.name
                )
                if (!contains("left_triple_press_action")) putString(
                    "left_triple_press_action",
                    StemAction.defaultActions[StemPressType.TRIPLE_PRESS]!!.name
                )
                if (!contains("right_triple_press_action")) putString(
                    "right_triple_press_action",
                    StemAction.defaultActions[StemPressType.TRIPLE_PRESS]!!.name
                )
                if (!contains("left_long_press_action")) putString(
                    "left_long_press_action",
                    StemAction.defaultActions[StemPressType.LONG_PRESS]!!.name
                )
                if (!contains("right_long_press_action")) putString(
                    "right_long_press_action",
                    StemAction.defaultActions[StemPressType.LONG_PRESS]!!.name
                )
                if (!contains("camera_action")) putString("camera_action", "SINGLE_PRESS")

            }
        }

        initializeConfig()

        externalBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "me.kavishdevar.librepods.SET_ANC_MODE") {
                    if (intent.hasExtra("mode")) {
                        val mode = intent.getIntExtra("mode", -1)
                        if (mode in 1..4) {
                            aacpManager.sendControlCommand(
                                AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value,
                                mode
                            )
                        }
                    } else {
                        val currentMode = ancNotification.status
                        val configByte = sharedPreferences.getInt("long_press_byte", 0b0111)
                        val allowOffMode = sharedPreferences.getBoolean("off_listening_mode", true)
                        val nextMode = getNextMode(currentMode = currentMode, configByte = configByte, allowOffMode)

                        aacpManager.sendControlCommand(
                            AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value,
                            nextMode
                        )
                        Log.d(
                            TAG,
                            "Cycling ANC mode from $currentMode to $nextMode"
                        )
                    }
                } else  if (intent?.action == "me.kavishdevar.librepods.CONVO_DETECT") {
                    if (intent.hasExtra("enabled")) {
                        val enabled = intent.getBooleanExtra("enabled", false)
                        aacpManager.sendControlCommand(
                            AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG.value,
                            enabled
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(externalBroadcastReceiver, externalBroadcastFilter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(
                externalBroadcastReceiver, externalBroadcastFilter
            )
        }
        val audioManager = this@AirPodsService.getSystemService(AUDIO_SERVICE) as AudioManager
        MediaController.initialize(
            audioManager, this@AirPodsService.getSharedPreferences(
                "settings", MODE_PRIVATE
            )
        )
//        Log.d(TAG, "Initializing CrossDevice")
//        CoroutineScope(Dispatchers.IO).launch {
//            CrossDevice.init(this@AirPodsService)
//            Log.d(TAG, "CrossDevice initialized")
//        }

        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        macAddress = sharedPreferences.getString("mac_address", "") ?: ""

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object: TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        phoneCallPhase = PhoneCallPhase.RINGING
                        val leAvailableForAudio =
                            bleManager.getMostRecentStatus()?.isLeftInEar == true || bleManager.getMostRecentStatus()?.isRightInEar == true
//                        if ((CrossDevice.isAvailable && !isConnectedLocally && earDetectionNotification.status.contains(0x00)) || leAvailableForAudio) CoroutineScope(Dispatchers.IO).launch {
                        if (leAvailableForAudio) updateCheckScope.launch {
                            takeOver("call")
                        }
                        if (config.headGestures) {
                            handleIncomingCall()
                        }
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        val leAvailableForAudio =
                            bleManager.getMostRecentStatus()?.isLeftInEar == true || bleManager.getMostRecentStatus()?.isRightInEar == true
//                        if ((CrossDevice.isAvailable && !isConnectedLocally && earDetectionNotification.status.contains(0x00)) || leAvailableForAudio) CoroutineScope(
                        if (leAvailableForAudio) updateCheckScope.launch {
                            takeOver("call")
                        }
                        phoneCallPhase = PhoneCallPhase.ACTIVE
                        callGestureDecisionRequested = false
                        isInCall = true
                        stopCallGestureRecognition("call became active")
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        phoneCallPhase = PhoneCallPhase.IDLE
                        callGestureDecisionRequested = false
                        isInCall = false
                        stopCallGestureRecognition("call ended")
                    }
                }
            }
        }
        if (checkSelfPermission("android.permission.READ_PHONE_STATE") == PackageManager.PERMISSION_GRANTED) {
            telephonyManager.registerTelephonyCallback(mainExecutor, phoneStateListener)
            // TelephonyCallback is change-driven; initialize from the current
            // call state too so a service started mid-call cannot leave head
            // tracking enabled until the next call-state transition.
            @Suppress("DEPRECATION")
            val currentCallState = telephonyManager.callState
            when (currentCallState) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    phoneCallPhase = PhoneCallPhase.RINGING
                    if (config.headGestures) handleIncomingCall()
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    phoneCallPhase = PhoneCallPhase.ACTIVE
                    isInCall = true
                    stopCallGestureRecognition("service started during active call")
                }
                else -> {
                    phoneCallPhase = PhoneCallPhase.IDLE
                    isInCall = false
                }
            }
        }

        if (config.showPhoneBatteryInWidget) {
            widgetMobileBatteryEnabled = true
            val batteryChangedIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            batteryChangedIntentFilter.addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    BatteryChangedIntentReceiver, batteryChangedIntentFilter, RECEIVER_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(
                    BatteryChangedIntentReceiver, batteryChangedIntentFilter
                )
            }
        }
        val serviceIntentFilter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
            addAction("android.bluetooth.device.action.NAME_CHANGED")
            addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED")
            addAction("android.bluetooth.device.action.UUID")
        }

        connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AirPodsNotifications.AIRPODS_AUDIO_PROFILE_EVENT) {
                    val profile = intent.getIntExtra("profile", -1)
                    val connected = intent.getBooleanExtra("connected", false)
                    synchronized(connectedAndroidAudioProfiles) {
                        if (connected) connectedAndroidAudioProfiles.add(profile)
                        else connectedAndroidAudioProfiles.remove(profile)
                        isAndroidAudioProfileConnected = connectedAndroidAudioProfiles.isNotEmpty()
                    }
                    sendBroadcast(
                        Intent(AirPodsNotifications.AIRPODS_AUDIO_ROUTE_STATE)
                            .putExtra("connected", isAndroidAudioProfileConnected)
                            .setPackage(packageName)
                    )
                } else if (intent?.action == AirPodsNotifications.AIRPODS_CONNECTION_DETECTED) {
                    val connectedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("device", BluetoothDevice::class.java)
                    } else {
                        intent.getParcelableExtra("device") as BluetoothDevice?
                    } ?: run {
                        Log.w(TAG, "Ignoring AirPods connection event without a Bluetooth device")
                        return
                    }
                    device = connectedDevice

                    if (config.deviceName == "AirPods" && connectedDevice.name != null) {
                        config.deviceName = connectedDevice.name ?: "AirPods"
                        sharedPreferences.edit { putString("name", config.deviceName) }
                    }

                    macAddress = connectedDevice.address
                    sharedPreferences.edit { putString("mac_address", macAddress) }
                    bleManager.refreshSelectedBondFilter()

                    val androidAudioProfileConnected =
                        intent.getBooleanExtra(EXTRA_ANDROID_AUDIO_PROFILE_CONNECTED, false)
                    if (androidAudioProfileConnected) {
                        Log.i(TAG, "Selected AirPods Android audio profile confirmed; showing popup")
                        showConnectionPopup(
                            this@AirPodsService,
                            config.deviceName.ifBlank { "AirPods" },
                            IslandType.CONNECTED,
                        )
                    }

                    if (BluetoothConnectionEventPolicy.shouldStartControlSession(
                            androidAudioProfileConnected = androidAudioProfileConnected,
                        )
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val bluetoothManager = getSystemService(BluetoothManager::class.java)
                            connectToSocket(bluetoothManager.adapter, connectedDevice)
                        }
                        Log.d(TAG, "Setting metadata")
                        setMetadatas(connectedDevice)
                    } else {
                        Log.d(
                            TAG,
                            "ACL link only; deferring AACP control socket until Android audio profile is connected",
                        )
                    }

                } else if (intent?.action == AirPodsNotifications.AIRPODS_DISCONNECTED) {
                    sharedPreferences.edit { putBoolean("connection_successful", false) }
                    lastWearPosition = null
                    cancelPendingWearResume()
                    MediaController.cancelAutoResumeAfterExternalMediaChange()
                    runCatching { BluetoothConnectionManager.aacpSocket?.close() }
                    runCatching { BluetoothConnectionManager.attSocket?.close() }
                    device = null
                    popupShown = false
                    updateNotificationContent(false)
                    aacpManager.disconnected()
                    BluetoothConnectionManager.aacpSocket = null
                    BluetoothConnectionManager.attSocket = null
                }
            }
        }
        val showIslandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "me.kavishdevar.librepods.cross_device_island") {
                    showIsland(
                        this@AirPodsService,
                        lowestKnownBatteryLevel()
                    )
                } else if (intent?.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                    try {
                        context?.unregisterReceiver(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val showIslandIntentFilter = IntentFilter().apply {
            addAction("me.kavishdevar.librepods.cross_device_island")
            addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showIslandReceiver, showIslandIntentFilter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(
                showIslandReceiver, showIslandIntentFilter
            )
        }

        val deviceIntentFilter = IntentFilter().apply {
            addAction(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED)
            addAction(AirPodsNotifications.AIRPODS_DISCONNECTED)
            addAction(AirPodsNotifications.AIRPODS_AUDIO_PROFILE_EVENT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionReceiver, deviceIntentFilter, RECEIVER_EXPORTED)
            registerReceiver(bluetoothReceiver, serviceIntentFilter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(
                connectionReceiver, deviceIntentFilter
            )
            registerReceiver(bluetoothReceiver, serviceIntentFilter)
        }

        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter

        for (audioProfile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            bluetoothAdapter.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            if (profile != audioProfile) return
                            val connectedDevices = proxy.connectedDevices
                            val selectedAddress = BluetoothConnectionEventPolicy.selectConnectedAddress(
                                selectedAddress = macAddress,
                                connectedCandidates = connectedDevices.map { candidate ->
                                    candidate.address to isLikelyAirPods(candidate)
                                },
                            )
                            val selected = connectedDevices.firstOrNull { it.address == selectedAddress }
                            if (selected != null) {
                                Log.i(TAG, "Found selected AirPods in an active audio profile at service start")
                                sendBroadcast(
                                    Intent(AirPodsNotifications.AIRPODS_AUDIO_PROFILE_EVENT)
                                        .putExtra("profile", profile)
                                        .putExtra("connected", true)
                                        .setPackage(packageName)
                                )
                                sendBroadcast(
                                    Intent(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED)
                                        .putExtra("device", selected)
                                        .putExtra(EXTRA_ANDROID_AUDIO_PROFILE_CONNECTED, true)
                                        .setPackage(packageName)
                                )
                            } else {
                                Log.i(TAG, "No unambiguous selected AirPods device found in the active audio profile")
                            }
                        } finally {
                            bluetoothAdapter.closeProfileProxy(profile, proxy)
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) = Unit
                },
                audioProfile
            )
        }

//        if (!isConnectedLocally && !CrossDevice.isAvailable) {
//            clearPacketLogs()
//        }

        CoroutineScope(Dispatchers.IO).launch {
            bleManager.startScanning()
        }
    }

    @Suppress("unused")
    fun cameraOpened() {
        Log.d(TAG, "Camera opened, gonna handle stem presses and take action if visible")
        cameraActive = true
        setupStemActions()
    }

    @Suppress("unused")
    fun cameraClosed() {
        cameraActive = false
        setupStemActions()
    }

    fun isCustomAction(
        action: StemAction?, default: StemAction?
    ): Boolean {
        return action != default
    }

    fun setupStemActions() {
        val singlePressDefault = StemAction.defaultActions[StemPressType.SINGLE_PRESS]
        val doublePressDefault = StemAction.defaultActions[StemPressType.DOUBLE_PRESS]
        val triplePressDefault = StemAction.defaultActions[StemPressType.TRIPLE_PRESS]
        val longPressDefault = StemAction.defaultActions[StemPressType.LONG_PRESS]

        val singlePressCustomized =
            isCustomAction(config.leftSinglePressAction, singlePressDefault) || isCustomAction(
                config.rightSinglePressAction, singlePressDefault
            ) || (cameraActive && config.cameraAction == StemPressType.SINGLE_PRESS)
        val doublePressCustomized =
            isCustomAction(config.leftDoublePressAction, doublePressDefault) || isCustomAction(
                config.rightDoublePressAction, doublePressDefault
            )
        val triplePressCustomized =
            isCustomAction(config.leftTriplePressAction, triplePressDefault) || isCustomAction(
                config.rightTriplePressAction, triplePressDefault
            )
        val longPressCustomized = isCustomAction(
            config.leftLongPressAction, longPressDefault
        ) || isCustomAction(
            config.rightLongPressAction, longPressDefault
        ) || (cameraActive && config.cameraAction == StemPressType.LONG_PRESS)
        Log.d(
            TAG,
            "Setting up stem actions: Single Press Customized: $singlePressCustomized, Double Press Customized: $doublePressCustomized, Triple Press Customized: $triplePressCustomized, Long Press Customized: $longPressCustomized"
        )
        aacpManager.sendStemConfigPacket(
            singlePressCustomized,
            doublePressCustomized,
            triplePressCustomized,
            longPressCustomized,
        )
    }

    @SuppressLint("MissingPermission")
    private fun isLikelyAirPods(device: BluetoothDevice): Boolean {
        val accessoryUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        return device.uuids?.contains(accessoryUuid) == true ||
            device.name?.contains("AirPods", ignoreCase = true) == true
    }

    @ExperimentalEncodingApi
    private fun initializeAACPManagerCallback() {
        aacpManager.setPacketCallback(object : AACPManager.PacketCallback {
            @SuppressLint("MissingPermission")
            override fun onBatteryInfoReceived(batteryInfo: ByteArray) {
                batteryNotification.setBattery(batteryInfo)
                updateBattery()
                updateNotificationContent(
                    true,
                    this@AirPodsService.getSharedPreferences("settings", MODE_PRIVATE)
                        .getString("name", device?.name),
                    batteryNotification.getBattery()
                )
//                CrossDevice.sendRemotePacket(batteryInfo)
//                CrossDevice.batteryBytes = batteryInfo

                for (battery in batteryNotification.getBattery()) {
                    Log.d(
                        "AirPodsParser",
                        "${battery.getComponentName()}: ${battery.getStatusName()} at ${battery.level}% "
                    )
                }

                // Battery reports describe pod/case power, not Android audio-route
                // connectivity. Re-requesting hidden A2DP/HEADSET profile operations
                // for every battery frame caused repeated CONNECT requests while the
                // profile was already connected (and could disconnect audio while the
                // pods were charging).
            }

            override fun onEarDetectionReceived(earDetection: ByteArray) {
                processEarDetectionChange(earDetection)
                sendBroadcast(Intent(AirPodsNotifications.EAR_DETECTION_DATA).apply {
                    putExtra("data", byteArrayOf(
                        earDetectionNotification.status[0],
                        earDetectionNotification.status[1],
                    ))
                    setPackage(packageName)
                })
                Log.d("AirPodsParser", "Ear Detection: ${earDetectionNotification.status[0]} ${earDetectionNotification.status[1]}")
            }

            override fun onConversationAwarenessReceived(conversationAwareness: ByteArray) {
                conversationAwarenessNotification.setData(conversationAwareness)
                sendBroadcast(Intent(AirPodsNotifications.CA_DATA).apply {
                    putExtra("data", conversationAwarenessNotification.status)
                }.apply {
                    setPackage(packageName)
                })

                if (conversationAwarenessNotification.status == 1.toByte() || conversationAwarenessNotification.status == 2.toByte()) {
                    MediaController.startSpeaking()
                } else if (conversationAwarenessNotification.status == 6.toByte() ||conversationAwarenessNotification.status == 8.toByte() || conversationAwarenessNotification.status == 9.toByte()) {
                    MediaController.stopSpeaking()
                }

                Log.d(
                    "AirPodsParser",
                    "Conversation Awareness: ${conversationAwarenessNotification.status}"
                )
            }

            override fun onControlCommandReceived(controlCommand: ByteArray) {
                val command = AACPManager.ControlCommand.fromByteArray(controlCommand)
                if (command.identifier == AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value) {
                    ancNotification.setStatus(byteArrayOf(command.value.takeIf { it.isNotEmpty() }
                        ?.get(0) ?: 0x00.toByte()))
                    sendANCBroadcast()
                    updateNoiseControlWidget()
                }
            }

            override fun onOwnershipChangeReceived(owns: Boolean) {
                if (!owns) {
                    MediaController.recentlyLostOwnership = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        MediaController.recentlyLostOwnership = false
                    }, 3000)
                    Log.d(TAG, "ownership lost")
                    MediaController.sendPause()
                    MediaController.pausedForOtherDevice = true
                    otherDeviceTookOver = true
                    disconnectAudio(
                        this@AirPodsService, device
                    )
                }
            }

            override fun onOwnershipToFalseRequest(sender: String, reasonReverseTapped: Boolean) {
                // TODO: Show a reverse button, but that's a lot of effort -- i'd have to change the UI too, which i hate doing, and handle other device's reverses too, and disconnect audio etc... so for now, just pause the audio and show the island without asking to reverse.
                // handling reverse is a problem because we'd have to disconnect the audio, but there's no option connect audio again natively, so notification would have to be changed. I wish there was a way to just "change the audio output device".
                // (20 minutes later) i've done it nonetheless :]
                val senderName =
                    aacpManager.connectedDevices.find { it.mac == sender }?.type ?: "Other device"
                Log.d(
                    TAG,
                    "other device has hijacked the connection, reasonReverseTapped: $reasonReverseTapped"
                )
                aacpManager.sendControlCommand(
                    AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION.value,
                    byteArrayOf(0x00)
                )
                otherDeviceTookOver = true
                disconnectAudio(
                    this@AirPodsService, device
                )
                if (reasonReverseTapped) {
                    Log.d(TAG, "reverse tapped, disconnecting audio")
                    disconnectedBecauseReversed = true
                    disconnectAudio(this@AirPodsService, device)
                    showIsland(
                        this@AirPodsService,
                        (batteryNotification.getBattery()
                            .find { it.component == BatteryComponent.LEFT }?.level
                            ?: 0).coerceAtMost(
                            batteryNotification.getBattery()
                                .find { it.component == BatteryComponent.RIGHT }?.level ?: 0
                        ),
                        IslandType.MOVED_TO_OTHER_DEVICE,
                        reversed = true,
                        otherDeviceName = senderName
                    )
                }
                if (!aacpManager.owns) {
                    showIsland(
                        this@AirPodsService,
                        (batteryNotification.getBattery()
                            .find { it.component == BatteryComponent.LEFT }?.level
                            ?: 0).coerceAtMost(
                            batteryNotification.getBattery()
                                .find { it.component == BatteryComponent.RIGHT }?.level ?: 0
                        ),
                        IslandType.MOVED_TO_OTHER_DEVICE,
                        reversed = reasonReverseTapped,
                        otherDeviceName = senderName
                    )
                }
                MediaController.sendPause()
            }

            override fun onShowNearbyUI(sender: String) {
                val senderName =
                    aacpManager.connectedDevices.find { it.mac == sender }?.type ?: "Other device"
                showIsland(
                    this@AirPodsService,
                    (batteryNotification.getBattery()
                        .find { it.component == BatteryComponent.LEFT }?.level ?: 0).coerceAtMost(
                        batteryNotification.getBattery()
                            .find { it.component == BatteryComponent.RIGHT }?.level ?: 0
                    ),
                    IslandType.MOVED_TO_OTHER_DEVICE,
                    reversed = false,
                    otherDeviceName = senderName
                )
            }

            override fun onDeviceInformationReceived(deviceInformation: AACPManager.Companion.AirPodsInformation) {
                Log.d(
                    "AirPodsParser",
                    "AirPods information received: model=${deviceInformation.modelNumber}, manufacturer=${deviceInformation.manufacturer}, firmware=${deviceInformation.version1}/${deviceInformation.version2}/${deviceInformation.version3}"
                )
                // Store in SharedPreferences
                sharedPreferences.edit {
                    putString("name", deviceInformation.name)
                    putString("airpods_model_number", deviceInformation.modelNumber)
                    putString("airpods_manufacturer", deviceInformation.manufacturer)
                    putString("airpods_serial_number", deviceInformation.serialNumber)
                    putString("airpods_left_serial_number", deviceInformation.leftSerialNumber)
                    putString("airpods_right_serial_number", deviceInformation.rightSerialNumber)
                    putString("airpods_version1", deviceInformation.version1)
                    putString("airpods_version2", deviceInformation.version2)
                    putString("airpods_version3", deviceInformation.version3)
                    putString("airpods_hardware_revision", deviceInformation.hardwareRevision)
                    putString("airpods_updater_identifier", deviceInformation.updaterIdentifier)
                }
                // Update config
                config.airpodsName = deviceInformation.name
                config.airpodsModelNumber = deviceInformation.modelNumber
                config.airpodsManufacturer = deviceInformation.manufacturer
                config.airpodsSerialNumber = deviceInformation.serialNumber
                config.airpodsLeftSerialNumber = deviceInformation.leftSerialNumber
                config.airpodsRightSerialNumber = deviceInformation.rightSerialNumber
                config.airpodsVersion1 = deviceInformation.version1
                config.airpodsVersion2 = deviceInformation.version2
                config.airpodsVersion3 = deviceInformation.version3
                config.airpodsHardwareRevision = deviceInformation.hardwareRevision
                config.airpodsUpdaterIdentifier = deviceInformation.updaterIdentifier

                val model = AirPodsModels.getModelByModelNumber(config.airpodsModelNumber)
                if (model != null) {
                    airpodsInstance = AirPodsInstance(
                        name = config.airpodsName,
                        model = model,
                        actualModelNumber = config.airpodsModelNumber,
                        serialNumber = config.airpodsSerialNumber,
                        leftSerialNumber = config.airpodsLeftSerialNumber,
                        rightSerialNumber = config.airpodsRightSerialNumber,
                        version1 = config.airpodsVersion1,
                        version2 = config.airpodsVersion2,
                        version3 = config.airpodsVersion3,
                    )
                    if (device != null) setMetadatas(device!!)
                }
                sendBroadcast(
                    Intent(AirPodsNotifications.AIRPODS_INFORMATION_UPDATED).setPackage(
                        packageName
                    )
                )
            }

            @SuppressLint("NewApi")
            override fun onHeadTrackingReceived(headTracking: ByteArray) {
                if (isHeadTrackingActive) {
                    HeadTracking.processPacket(headTracking)
                    processHeadTrackingData(headTracking)
                }
            }

            override fun onProximityKeysReceived(proximityKeys: ByteArray) {
                val keys = aacpManager.parseProximityKeysResponse(proximityKeys)
                Log.d("AirPodsParser", "Received ${keys.size} proximity-key record(s)")
                sharedPreferences.edit {
                    for (key in keys) {
                        putString(key.key.name, Base64.encode(key.value))
                    }
                }
            }

            override fun onStemPressReceived(stemPress: ByteArray) {

                val (stemPressType, bud) = aacpManager.parseStemPressResponse(stemPress)

                Log.d(
                    "AirPodsParser",
                    "Stem press received: $stemPressType on $bud, cameraActive: $cameraActive, cameraAction: ${config.cameraAction}"
                )
                if (cameraActive && config.cameraAction != null && stemPressType == config.cameraAction) {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 27"))
                } else {
                    val action = getActionFor(bud, stemPressType)
                    Log.d("AirPodsParser", "$bud $stemPressType action: $action")
                    action?.let { executeStemAction(it) }
                }
            }

            override fun onAudioSourceReceived(audioSource: ByteArray) {
                Log.d(
                    "AirPodsParser",
                    "Audio source changed (type=${aacpManager.audioSource?.type?.name})"
                )
                if (localMac!="" && (aacpManager.audioSource?.type != AACPManager.Companion.AudioSourceType.NONE && aacpManager.audioSource?.mac != localMac)) {
                    Log.d(
                        "AirPodsParser",
                        "Audio source is another device, better to give up aacp control"
                    )
                    aacpManager.sendControlCommand(
                        AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION.value,
                        byteArrayOf(0x00)
                    )
                    // this also means that the other device has start playing the audio, and if that's true, we can again start listening for audio config changes
//                    Log.d(TAG, "Another device started playing audio, listening for audio config changes again")
//                    MediaController.pausedForOtherDevice = false
// future me: what the heck is this? this just means it will not be taking over again if audio source doesn't change???
                }
            }

            override fun onConnectedDevicesReceived(connectedDevices: List<AACPManager.Companion.ConnectedDevice>) {
                for (device in connectedDevices) {
                    Log.d(
                        "AirPodsParser",
                        "Connected device detected (info=${device.info1}/${device.info2})"
                    )
                }
                val newDevices = connectedDevices.filter { newDevice ->
                    val notInOld =
                        aacpManager.oldConnectedDevices.none { oldDevice -> oldDevice.mac == newDevice.mac }
                    val notLocal = newDevice.mac != localMac
                    notInOld && notLocal
                }

                for (device in newDevices) {
                    Log.d(
                        "AirPodsParser",
                        "New connected device detected (info=${device.info1}/${device.info2})"
                    )
                    Log.d(
                        TAG,
                        "Sending device registration and media-routing information"
                    )
                    aacpManager.sendMediaInformationNewDevice(
                        selfMacAddress = localMac, targetMacAddress = device.mac
                    )
                    aacpManager.sendAddTiPiDevice(
                        selfMacAddress = localMac, targetMacAddress = device.mac
                    )
                }
            }

            override fun onHeadphoneAccommodationReceived(eqData: FloatArray) {
                sendBroadcast(
                    Intent(AirPodsNotifications.EQ_DATA).putExtra("eqData", eqData).apply {
                        setPackage(packageName)
                    })
            }

            override fun onCustomEqReceived(customEq: CustomEq) {
                // TODO
            }

            override fun onCapabilitiesReceived(capabilities: List<Capability>) {
                // TODO
            }

            override fun onUnknownPacketReceived(packet: ByteArray) {
                Log.d(
                    "AACPManager",
                    "Unknown packet received: ${packet.joinToString(" ") { "%02X".format(it) }}"
                )
            }
        })
    }

    private fun getActionFor(
        bud: AACPManager.Companion.StemPressBudType, type: StemPressType
    ): StemAction? {
        return when (type) {
            StemPressType.SINGLE_PRESS -> if (bud == AACPManager.Companion.StemPressBudType.LEFT) config.leftSinglePressAction else config.rightSinglePressAction
            StemPressType.DOUBLE_PRESS -> if (bud == AACPManager.Companion.StemPressBudType.LEFT) config.leftDoublePressAction else config.rightDoublePressAction
            StemPressType.TRIPLE_PRESS -> if (bud == AACPManager.Companion.StemPressBudType.LEFT) config.leftTriplePressAction else config.rightTriplePressAction
            StemPressType.LONG_PRESS -> if (bud == AACPManager.Companion.StemPressBudType.LEFT) config.leftLongPressAction else config.rightLongPressAction
        }
    }

    private fun executeStemAction(action: StemAction) {
        when (action) {
            StemAction.defaultActions[StemPressType.SINGLE_PRESS] -> {
                Log.d(
                    "AirPodsParser", "Default single press action: Play/Pause, not taking action."
                )
            }

            StemAction.PLAY_PAUSE -> MediaController.sendPlayPause()
            StemAction.PREVIOUS_TRACK -> MediaController.sendPreviousTrack()
            StemAction.NEXT_TRACK -> MediaController.sendNextTrack()
            StemAction.DIGITAL_ASSISTANT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } else {
                    Log.w(
                        "AirPodsParser",
                        "Digital Assistant action is not supported on this Android version."
                    )
                }
            }

            StemAction.CYCLE_NOISE_CONTROL_MODES -> {
                Log.d("AirPodsParser", "Cycling noise control modes")
                sendBroadcast(Intent("me.kavishdevar.librepods.SET_ANC_MODE").apply {
                    setPackage(packageName)
                })
            }
        }
    }

    private fun processEarDetectionChange(earDetection: ByteArray) {
        if (!earDetectionNotification.isEarDetectionData(earDetection)) {
            Log.w(TAG, "Ignoring malformed AAP ear-detection event (length=${earDetection.size})")
            lastWearPosition = null
            return
        }

        val current = EarWearTransitionPolicy.decode(earDetection[6], earDetection[7])
        if (current == null) {
            Log.w(TAG, "Ignoring unknown AAP ear-detection values")
            earDetectionNotification.setStatus(earDetection)
            lastWearPosition = null
            return
        }

        val previous = lastWearPosition
        earDetectionNotification.setStatus(earDetection)
        lastWearPosition = current
        Log.d(TAG, "AAP wear state ${previous ?: "INITIAL"} -> $current; enabled=${config.earDetectionEnabled}")

        if (!config.earDetectionEnabled) {
            lastWearPosition = null
            return
        }

        // Remember the verified route on every fresh AAP frame so an output switch racing
        // the final out-of-ear frame does not suppress the pause event.
        val airPodsRouteActive = MediaController.isAirPodsAudioRouteActive()

        if (previous == null) {
            // A fresh first report may already say both buds are out. If the AirPods
            // are the active Android route, enforce the configured pause policy once.
            if (current == EarWearPosition.NONE &&
                config.mediaControlOnWearChangeEnabled &&
                airPodsRouteActive &&
                MediaController.getMusicActive()
            ) {
                MediaController.sendPause(force = true)
                Log.i(TAG, "Paused active playback because the first fresh ear report says both buds are out")
            }
            return
        }

        val action = EarWearTransitionPolicy.action(previous, current)
        if (action == EarWearAction.NONE) return

        if (config.popupOnWearChange && EarWearTransitionPolicy.shouldShowWornPopup(previous, current)) {
            if (islandWindow?.isVisible == true) islandWindow?.forceClose()
            showConnectionPopup(
                this@AirPodsService,
                sharedPreferences.getString("name", "AirPods Pro") ?: "AirPods",
                IslandType.EARBUD_WORN,
                forceReplay = true,
            )
        }

        if (!config.mediaControlOnWearChangeEnabled) {
            Log.d(TAG, "Wear media control is disabled by the user")
            return
        }

        if (action == EarWearAction.PAUSE) {
            cancelPendingWearResume()
            val routeActive = airPodsRouteActive || MediaController.wasAirPodsAudioRouteActiveRecently()
            if (routeActive && EarWearTransitionPolicy.shouldPause(previous, current, MediaController.getMusicActive())) {
                MediaController.sendPause(force = true)
            } else {
                Log.i(TAG, "Skipping automatic pause: AirPods are not the active Android audio output")
            }
            if (current == EarWearPosition.NONE) {
                if (config.disconnectWhenNotWearing) disconnectAudio(this@AirPodsService, device)
            }
            Log.i(TAG, "Automatic wear policy requested pause for $previous -> $current")
            return
        }

        if (previous == EarWearPosition.NONE && current != EarWearPosition.NONE) {
            connectAudio(this@AirPodsService, device)
        }
        // A prior manual stop/pause leaves iPausedTheMedia false, so no automatic play is sent.
        if (EarWearTransitionPolicy.shouldResume(previous, current, MediaController.iPausedTheMedia)) {
            queueWearResume()
        } else {
            Log.d(TAG, "Wear transition will not resume: media was not paused by this app")
        }
    }

    private fun queueWearResume() {
        pendingWearResume = true
        wearResumeRouteAttempts = 0
        wearResumeHandler.removeCallbacks(wearResumeRunnable)
        wearResumeHandler.post(wearResumeRunnable)
    }

    private fun cancelPendingWearResume() {
        pendingWearResume = false
        wearResumeRouteAttempts = 0
        wearResumeHandler.removeCallbacks(wearResumeRunnable)
    }

    private fun initializeConfig() {
        config = ServiceConfig(
            deviceName = sharedPreferences.getString("name", "AirPods") ?: "AirPods",
            earDetectionEnabled = sharedPreferences.getBoolean("automatic_ear_detection", true),
            mediaControlOnWearChangeEnabled = sharedPreferences.getBoolean("media_control_on_wear_change", true),
            popupOnWearChange = sharedPreferences.getBoolean("popup_on_wear_change", true),
            conversationalAwarenessPauseMusic = sharedPreferences.getBoolean(
                "conversational_awareness_pause_music", false
            ),
            showPhoneBatteryInWidget = sharedPreferences.getBoolean(
                "show_phone_battery_in_widget", true
            ),
            relativeConversationalAwarenessVolume = sharedPreferences.getBoolean(
                "relative_conversational_awareness_volume", true
            ),
            headGestures = sharedPreferences.getBoolean("head_gestures", true),
            disconnectWhenNotWearing = sharedPreferences.getBoolean(
                "disconnect_when_not_wearing", false
            ),
            conversationalAwarenessVolume = sharedPreferences.getInt(
                "conversational_awareness_volume", 43
            ),
            qsClickBehavior = sharedPreferences.getString("qs_click_behavior", "cycle") ?: "cycle",

            // AirPods state-based takeover
            takeoverWhenDisconnected = sharedPreferences.getBoolean(
                "takeover_when_disconnected", false
            ),
            takeoverWhenIdle = sharedPreferences.getBoolean("takeover_when_idle", false),
            takeoverWhenMusic = sharedPreferences.getBoolean("takeover_when_music", false),
            takeoverWhenCall = sharedPreferences.getBoolean("takeover_when_call", false),

            // Phone state-based takeover
            takeoverWhenRingingCall = sharedPreferences.getBoolean(
                "takeover_when_ringing_call", false
            ),
            takeoverWhenMediaStart = sharedPreferences.getBoolean(
                "takeover_when_media_start", false
            ),

            // Stem actions
            leftSinglePressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "left_single_press_action", "PLAY_PAUSE"
                ) ?: "PLAY_PAUSE"
            )!!,
            rightSinglePressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "right_single_press_action", "PLAY_PAUSE"
                ) ?: "PLAY_PAUSE"
            )!!,

            leftDoublePressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "left_double_press_action", "PREVIOUS_TRACK"
                ) ?: "NEXT_TRACK"
            )!!,
            rightDoublePressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "right_double_press_action", "NEXT_TRACK"
                ) ?: "NEXT_TRACK"
            )!!,

            leftTriplePressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "left_triple_press_action", "PREVIOUS_TRACK"
                ) ?: "PREVIOUS_TRACK"
            )!!,
            rightTriplePressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "right_triple_press_action", "PREVIOUS_TRACK"
                ) ?: "PREVIOUS_TRACK"
            )!!,

            leftLongPressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "left_long_press_action", "CYCLE_NOISE_CONTROL_MODES"
                ) ?: "CYCLE_NOISE_CONTROL_MODES"
            )!!,
            rightLongPressAction = StemAction.fromString(
                sharedPreferences.getString(
                    "right_long_press_action", "DIGITAL_ASSISTANT"
                ) ?: "DIGITAL_ASSISTANT"
            )!!,

            cameraAction = sharedPreferences.getString("camera_action", null)
                ?.let { StemPressType.valueOf(it) },

            // AirPods device information
            airpodsName = sharedPreferences.getString("airpods_name", "") ?: "",
            airpodsModelNumber = sharedPreferences.getString("airpods_model_number", "") ?: "",
            airpodsManufacturer = sharedPreferences.getString("airpods_manufacturer", "") ?: "",
            airpodsSerialNumber = sharedPreferences.getString("airpods_serial_number", "") ?: "",
            airpodsLeftSerialNumber = sharedPreferences.getString("airpods_left_serial_number", "")
                ?: "",
            airpodsRightSerialNumber = sharedPreferences.getString(
                "airpods_right_serial_number", ""
            ) ?: "",
            airpodsVersion1 = sharedPreferences.getString("airpods_version1", "") ?: "",
            airpodsVersion2 = sharedPreferences.getString("airpods_version2", "") ?: "",
            airpodsVersion3 = sharedPreferences.getString("airpods_version3", "") ?: "",
            airpodsHardwareRevision = sharedPreferences.getString("airpods_hardware_revision", "")
                ?: "",
            airpodsUpdaterIdentifier = sharedPreferences.getString("airpods_updater_identifier", "")
                ?: "",

            selfMacAddress = sharedPreferences.getString("self_mac_address", "") ?: ""
        )
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if (preferences == null || key == null) return

        when (key) {
            "name" -> config.deviceName = preferences.getString(key, "AirPods") ?: "AirPods"
            "mac_address" -> macAddress = preferences.getString(key, "") ?: ""
            "automatic_ear_detection" -> {
                config.earDetectionEnabled = preferences.getBoolean(key, true)
                lastWearPosition = null
                if (!config.earDetectionEnabled) cancelPendingWearResume()
            }
            "popup_on_wear_change" -> config.popupOnWearChange = preferences.getBoolean(key, true)
            "media_control_on_wear_change" -> {
                config.mediaControlOnWearChangeEnabled = preferences.getBoolean(key, true)
                if (!config.mediaControlOnWearChangeEnabled) {
                    cancelPendingWearResume()
                    MediaController.cancelAutoResumeAfterExternalMediaChange()
                }
            }

            "conversational_awareness_pause_music" -> config.conversationalAwarenessPauseMusic =
                preferences.getBoolean(key, false)

            "show_phone_battery_in_widget" -> {
                config.showPhoneBatteryInWidget = preferences.getBoolean(key, true)
                widgetMobileBatteryEnabled = config.showPhoneBatteryInWidget
                updateBattery()
            }

            "relative_conversational_awareness_volume" -> config.relativeConversationalAwarenessVolume =
                preferences.getBoolean(key, true)

            "head_gestures" -> config.headGestures = preferences.getBoolean(key, true)
            "disconnect_when_not_wearing" -> config.disconnectWhenNotWearing =
                preferences.getBoolean(key, false)

            "conversational_awareness_volume" -> config.conversationalAwarenessVolume =
                preferences.getInt(key, 43)

            "qs_click_behavior" -> config.qsClickBehavior =
                preferences.getString(key, "cycle") ?: "cycle"

            // AirPods state-based takeover
            "takeover_when_disconnected" -> config.takeoverWhenDisconnected =
                preferences.getBoolean(key, true)

            "takeover_when_idle" -> config.takeoverWhenIdle = preferences.getBoolean(key, true)
            "takeover_when_music" -> config.takeoverWhenMusic = preferences.getBoolean(key, false)
            "takeover_when_call" -> config.takeoverWhenCall = preferences.getBoolean(key, true)

            // Phone state-based takeover
            "takeover_when_ringing_call" -> config.takeoverWhenRingingCall =
                preferences.getBoolean(key, true)

            "takeover_when_media_start" -> config.takeoverWhenMediaStart =
                preferences.getBoolean(key, true)

            "left_single_press_action" -> {
                config.leftSinglePressAction = StemAction.fromString(
                    preferences.getString(key, "PLAY_PAUSE") ?: "PLAY_PAUSE"
                )!!
                setupStemActions()
            }

            "right_single_press_action" -> {
                config.rightSinglePressAction = StemAction.fromString(
                    preferences.getString(key, "PLAY_PAUSE") ?: "PLAY_PAUSE"
                )!!
                setupStemActions()
            }

            "left_double_press_action" -> {
                config.leftDoublePressAction = StemAction.fromString(
                    preferences.getString(key, "PREVIOUS_TRACK") ?: "PREVIOUS_TRACK"
                )!!
                setupStemActions()
            }

            "right_double_press_action" -> {
                config.rightDoublePressAction = StemAction.fromString(
                    preferences.getString(key, "NEXT_TRACK") ?: "NEXT_TRACK"
                )!!
                setupStemActions()
            }

            "left_triple_press_action" -> {
                config.leftTriplePressAction = StemAction.fromString(
                    preferences.getString(key, "PREVIOUS_TRACK") ?: "PREVIOUS_TRACK"
                )!!
                setupStemActions()
            }

            "right_triple_press_action" -> {
                config.rightTriplePressAction = StemAction.fromString(
                    preferences.getString(key, "PREVIOUS_TRACK") ?: "PREVIOUS_TRACK"
                )!!
                setupStemActions()
            }

            "left_long_press_action" -> {
                config.leftLongPressAction = StemAction.fromString(
                    preferences.getString(key, "CYCLE_NOISE_CONTROL_MODES")
                        ?: "CYCLE_NOISE_CONTROL_MODES"
                )!!
                setupStemActions()
            }

            "right_long_press_action" -> {
                config.rightLongPressAction = StemAction.fromString(
                    preferences.getString(key, "DIGITAL_ASSISTANT") ?: "DIGITAL_ASSISTANT"
                )!!
                setupStemActions()
            }

            "camera_action" -> config.cameraAction =
                preferences.getString(key, null)?.let { StemPressType.valueOf(it) }

            // AirPods device information
            "airpods_name" -> config.airpodsName = preferences.getString(key, "") ?: ""
            "airpods_model_number" -> config.airpodsModelNumber =
                preferences.getString(key, "") ?: ""

            "airpods_manufacturer" -> config.airpodsManufacturer =
                preferences.getString(key, "") ?: ""

            "airpods_serial_number" -> config.airpodsSerialNumber =
                preferences.getString(key, "") ?: ""

            "airpods_left_serial_number" -> config.airpodsLeftSerialNumber =
                preferences.getString(key, "") ?: ""

            "airpods_right_serial_number" -> config.airpodsRightSerialNumber =
                preferences.getString(key, "") ?: ""

            "airpods_version1" -> config.airpodsVersion1 = preferences.getString(key, "") ?: ""
            "airpods_version2" -> config.airpodsVersion2 = preferences.getString(key, "") ?: ""
            "airpods_version3" -> config.airpodsVersion3 = preferences.getString(key, "") ?: ""
            "airpods_hardware_revision" -> config.airpodsHardwareRevision =
                preferences.getString(key, "") ?: ""

            "airpods_updater_identifier" -> config.airpodsUpdaterIdentifier =
                preferences.getString(key, "") ?: ""

            "self_mac_address" -> config.selfMacAddress = preferences.getString(key, "") ?: ""
        }
    }

    private fun logPacket(packet: ByteArray, @Suppress("SameParameterValue") source: String) {
        val logEntry = "$source: packet received (${packet.size} bytes)"

        synchronized(inMemoryLogs) {
            inMemoryLogs.add(logEntry)
            if (inMemoryLogs.size > maxLogEntries) {
                inMemoryLogs.iterator().next().let {
                    inMemoryLogs.remove(it)
                }
            }

            _packetLogsFlow.value = inMemoryLogs.toSet()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val logs =
                sharedPreferencesLogs.getStringSet(packetLogKey, mutableSetOf())?.toMutableSet()
                    ?: mutableSetOf()
            logs.add(logEntry)

            if (logs.size > maxLogEntries) {
                val toKeep = logs.toList().takeLast(maxLogEntries).toSet()
                sharedPreferencesLogs.edit { putStringSet(packetLogKey, toKeep) }
            } else {
                sharedPreferencesLogs.edit { putStringSet(packetLogKey, logs) }
            }
        }
    }

    private fun clearPacketLogs() {
        synchronized(inMemoryLogs) {
            inMemoryLogs.clear()
            _packetLogsFlow.value = emptySet()
        }
        sharedPreferencesLogs.edit { remove(packetLogKey) }
    }

    fun clearLogs() {
        clearPacketLogs()
        _packetLogsFlow.value = emptySet()
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    private var gestureDetector: GestureDetector? = null
    @Volatile private var isInCall = false
    private var callNumber: String? = null

    private fun initGestureDetector() {
        if (gestureDetector == null) {
            gestureDetector = GestureDetector(this)
        }
    }


    var popupShown = false
    private var popupWindow: PopupWindow? = null
    private var lowBatteryAlertWindow: LowBatteryAlertWindow? = null
    private val pendingLowBatteryWarnings = ArrayDeque<Battery>()
    private var isLowBatteryWarningShowing = false

    fun showPopup(service: Service, name: String, replayIfVisible: Boolean = false) {
        if (!Settings.canDrawOverlays(service)) {
            Log.d(TAG, "No permission for SYSTEM_ALERT_WINDOW")
            return
        }
        val model = AirPodsModels.getModelByModelNumber(
            sharedPreferences.getString("airpods_model_number", "") ?: ""
        )
        val artwork = if (model?.formFactor == AirPodsFormFactor.OVER_EAR_HEADPHONES) {
            model.budsRes
        } else {
            model?.caseRes ?: R.drawable.airpods
        }
        if (popupShown) {
            if (replayIfVisible) {
                val replayed = popupWindow?.replay(name, batteryNotification, artwork) == true
                if (!replayed) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (popupShown) {
                            popupWindow?.replay(name, batteryNotification, artwork)
                        } else {
                            showPopup(service, name, replayIfVisible = true)
                        }
                    }, 240L)
                }
            }
            return
        }
        popupShown = true
        lateinit var newPopup: PopupWindow
        newPopup = PopupWindow(service.applicationContext) {
            popupShown = false
            if (popupWindow === newPopup) popupWindow = null
        }
        popupWindow = newPopup
        if (!newPopup.open(name, batteryNotification, artwork)) {
            popupShown = false
            popupWindow = null
        }
    }

    fun showConnectionPopup(
        service: Service,
        name: String,
        type: IslandType = IslandType.CONNECTED,
        forceReplay: Boolean = false,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
            showConnectionPopup(service, name, type, forceReplay)
            }
            return
        }
        if (!Settings.canDrawOverlays(service)) {
            showPopupNotificationFallback(service, name, type)
            return
        }
        val style = ConnectionPopupStyle.fromPreferences(
            storedValue = sharedPreferences.getString("connection_popup_style", null),
            legacyBottomSheetEnabled = sharedPreferences.getBoolean("show_bottom_sheet_popup", true),
            legacyIslandEnabled = sharedPreferences.getBoolean("show_island_popup", true)
        )
        when (style) {
            ConnectionPopupStyle.BOTTOM_SHEET -> showPopup(service, name, replayIfVisible = forceReplay)
            ConnectionPopupStyle.DYNAMIC_ISLAND -> showIsland(
                service = service,
                batteryPercentage = lowestKnownBatteryLevel(),
                type = type,
                respectLegacyPreference = false,
                forceReplay = forceReplay,
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showPopupNotificationFallback(service: Service, name: String, type: IslandType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            service.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Popup fallback skipped: overlay and notification permissions are unavailable")
            return
        }
        val manager = service.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "airpods_popup_fallback",
                    getString(R.string.notification_channel_connection),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { setSound(null, null) }
            )
        }
        val intent = Intent(service, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            service,
            410,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val eventText = when (type) {
            IslandType.EARBUD_WORN -> getString(R.string.island_earbud_worn_text)
            IslandType.EARBUD_REMOVED -> getString(R.string.island_earbud_removed_text)
            IslandType.CASE_OPENED -> getString(R.string.island_case_opened_text)
            else -> getString(R.string.island_connected_text)
        }
        manager.notify(
            410,
            NotificationCompat.Builder(service, "airpods_popup_fallback")
                .setSmallIcon(R.drawable.airpods)
                .setContentTitle(name)
                .setContentText(eventText)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun lowestKnownBatteryLevel(): Int = batteryNotification.getBattery()
        .asSequence()
        .filter { it.component == BatteryComponent.LEFT || it.component == BatteryComponent.RIGHT }
        .filter { it.isLevelKnown }
        .map { it.level }
        .filter { it in 0..100 }
        .minOrNull() ?: 0

    var islandOpen = false
    var islandWindow: IslandWindow? = null

    @SuppressLint("MissingPermission")
    fun showIsland(
        service: Service,
        batteryPercentage: Int,
        type: IslandType = IslandType.CONNECTED,
        reversed: Boolean = false,
        otherDeviceName: String? = null,
        respectLegacyPreference: Boolean = true,
        forceReplay: Boolean = false,
    ) {
        Log.d(TAG, "Showing island window")
        if (respectLegacyPreference && !sharedPreferences.getBoolean("show_island_popup", true)) {
            return
        }
        if (!Settings.canDrawOverlays(service)) {
            Log.d(TAG, "No permission for SYSTEM_ALERT_WINDOW")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            if (forceReplay && (islandOpen || islandWindow?.isVisible == true)) {
                islandWindow?.forceClose()
                islandOpen = false
            }
            if (islandOpen || islandWindow?.isVisible == true) return@launch
            val window = IslandWindow(service.applicationContext)
            islandWindow = window
            window.show(
                sharedPreferences.getString("name", "AirPods Pro").toString(),
                batteryPercentage,
                this@AirPodsService,
                type,
                reversed,
                otherDeviceName
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    //    var isConnectedLocally = false
    var device: BluetoothDevice? = null

    private lateinit var earReceiver: BroadcastReceiver
    var widgetMobileBatteryEnabled = false

    object BatteryChangedIntentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                ServiceManager.getService()?.updateBattery()
            } else if (intent.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun startForegroundNotification(): Boolean {
        // Foreground services cannot rely on a channel created with IMPORTANCE_NONE.
        // Use a dedicated low-importance channel so a previously disabled legacy
        // channel cannot prevent promotion on a new install or an upgrade.
        val serviceNotificationChannel = NotificationChannel(
            "echopods_background_service",
            getString(R.string.notification_channel_background),
            NotificationManager.IMPORTANCE_LOW
        )

        val connectedNotificationChannel = NotificationChannel(
            "airpods_connection_status",
            getString(R.string.notification_channel_connection),
            NotificationManager.IMPORTANCE_LOW,
        )

        val socketFailureChannel = NotificationChannel(
            "socket_connection_failure",
            getString(R.string.notification_channel_connection_issues),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_connection_issues_description)
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceNotificationChannel)
        notificationManager.createNotificationChannel(connectedNotificationChannel)
        notificationManager.createNotificationChannel(socketFailureChannel)

        val notificationSettingsIntent =
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, "echopods_background_service")
            }
        val pendingIntentNotifDisable = PendingIntent.getActivity(
            this,
            0,
            notificationSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "echopods_background_service")
            .setSmallIcon(R.drawable.airpods).setContentTitle(getString(R.string.notification_background_running))
            .setContentText(getString(R.string.notification_disable_background))
            .setContentIntent(pendingIntentNotifDisable).setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()

        try {
            // This app's minSdk is 31, so use the platform typed overload directly.
            // It keeps the declared connectedDevice type visible to Samsung's FGS
            // tracker instead of relying on a compatibility wrapper.
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            Log.d(TAG, "Foreground service promoted with connectedDevice type")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Could not promote AirPodsService to foreground", e)
            // Do not leave startForegroundService() pending until Android raises
            // ForegroundServiceDidNotStartInTimeException/ANR.
            stopSelf()
            return false
        }
    }

    @Suppress("KotlinUnreachableCode")
    @OptIn(ExperimentalMaterial3Api::class)
    private fun showSocketConnectionFailureNotification(errorMessage: String) {
        return // something causes too many notifications. turning off for now
        if (BuildConfig.FLAVOR != "xposed") {
            Log.w(
                TAG,
                "Not showing BluetoothConnectionManager.aacpSocket? error notification to user, the service shouldn't be running if it isn't supported."
            )
            return
        }
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "socket_connection_failure")
            .setSmallIcon(R.drawable.airpods).setContentTitle(getString(R.string.notification_connection_issue))
            .setContentText(getString(R.string.notification_l2cap_connection_failed)).setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your AirPods are connected via Bluetooth, but EchoPods couldn't connect using L2CAP. Error: $errorMessage"
                )
            ).setContentIntent(pendingIntent).setCategory(Notification.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()

        notificationManager.notify(3, notification)
    }

    fun sendANCBroadcast() {
        sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
            putExtra("data", ancNotification.status)
            setPackage(packageName)
        })
    }

    private fun updateBatteryFromBle(status: BLEManager.AirPodsStatus) {
        batteryNotification.mergeBleBattery(
            leftLevel = status.leftBattery,
            leftCharging = status.leftBattery?.let { status.isLeftCharging },
            rightLevel = status.rightBattery,
            rightCharging = status.rightBattery?.let { status.isRightCharging },
            caseLevel = status.caseBattery,
            caseCharging = status.caseBattery?.let { status.isCaseCharging },
        )
        updateBattery()
    }

    fun sendBatteryBroadcast() {
        broadcastBatteryInformation()
        sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
            putParcelableArrayListExtra("data", ArrayList(batteryNotification.getBattery()))
            setPackage(packageName)
        })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendBatteryNotification() {
        updateNotificationContent(
            true,
            getSharedPreferences("settings", MODE_PRIVATE).getString("name", device?.name),
            batteryNotification.getBattery()
        )
    }

    fun setBatteryMetadata() {
        if (checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") != PackageManager.PERMISSION_GRANTED) {
            device?.let { it ->
                SystemApisUtils.setMetadata(
                    it,
                    it.METADATA_UNTETHERED_CASE_BATTERY,
                    batteryNotification.getBattery()
                        .find { it.component == BatteryComponent.CASE }?.level.toString()
                        .toByteArray()
                )
                SystemApisUtils.setMetadata(
                    it,
                    it.METADATA_UNTETHERED_CASE_CHARGING,
                    (if (batteryNotification.getBattery()
                            .find { it.component == BatteryComponent.CASE }?.status == BatteryStatus.CHARGING
                    ) "1".toByteArray() else "0".toByteArray())
                )
                SystemApisUtils.setMetadata(
                    it,
                    it.METADATA_UNTETHERED_LEFT_BATTERY,
                    batteryNotification.getBattery()
                        .find { it.component == BatteryComponent.LEFT }?.level.toString()
                        .toByteArray()
                )
                SystemApisUtils.setMetadata(
                    it,
                    it.METADATA_UNTETHERED_LEFT_CHARGING,
                    (if (batteryNotification.getBattery()
                            .find { it.component == BatteryComponent.LEFT }?.status == BatteryStatus.CHARGING
                    ) "1".toByteArray() else "0".toByteArray())
                )
                SystemApisUtils.setMetadata(
                    it,
                    it.METADATA_UNTETHERED_RIGHT_BATTERY,
                    batteryNotification.getBattery()
                        .find { it.component == BatteryComponent.RIGHT }?.level.toString()
                        .toByteArray()
                )
                SystemApisUtils.setMetadata(
                    it,
                    it.METADATA_UNTETHERED_RIGHT_CHARGING,
                    (if (batteryNotification.getBattery()
                            .find { it.component == BatteryComponent.RIGHT }?.status == BatteryStatus.CHARGING
                    ) "1".toByteArray() else "0".toByteArray())
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun updateBatteryWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, BatteryWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

        val model = AirPodsModels.getModelByModelNumber(
            sharedPreferences.getString("airpods_model_number", "") ?: ""
        )
        val isOverEar = model?.formFactor == AirPodsFormFactor.OVER_EAR_HEADPHONES
        val batteries = batteryNotification.getBattery()
        val overallBattery = batteries
            .filter { it.isLevelKnown && (it.status != BatteryStatus.DISCONNECTED || it.level > 0) }
            .minByOrNull { it.level }
        val leftBattery = if (isOverEar) overallBattery else
            batteries.find { it.component == BatteryComponent.LEFT }
        val rightBattery = if (isOverEar) null else
            batteries.find { it.component == BatteryComponent.RIGHT }
        val caseBattery = if (isOverEar) null else
            batteries.find { it.component == BatteryComponent.CASE }
        val phoneBatteryManager = getSystemService(BatteryManager::class.java)
        val phoneLevel = if (widgetMobileBatteryEnabled) {
            phoneBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }
        } else null
        val phoneCharging = widgetMobileBatteryEnabled &&
            phoneBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ==
            BatteryManager.BATTERY_STATUS_CHARGING

        fun widgetComponent(battery: Battery?, visible: Boolean = true) = WidgetBatteryComponent(
            level = battery?.takeIf { it.isLevelKnown }?.level,
            charging = battery?.let { it.isLevelKnown && it.isChargingFresh() } == true,
            visible = visible,
        )

        val responsiveViews = BatteryWidget.responsiveViews(
            context = this,
            model = model,
            state = BatteryWidgetState(
                phone = WidgetBatteryComponent(phoneLevel, phoneCharging, widgetMobileBatteryEnabled),
                left = widgetComponent(leftBattery),
                right = widgetComponent(rightBattery, visible = !isOverEar),
                case = widgetComponent(caseBattery, visible = !isOverEar),
            ),
        )
        appWidgetManager.updateAppWidget(widgetIds, responsiveViews)
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalMaterial3Api::class)
    fun updateBattery() {
        persistLastKnownBatteryLevels()
        setBatteryMetadata()
        updateBatteryWidget()
        sendBatteryBroadcast()
        sendBatteryNotification()
        checkLowBatteryWarnings()
    }

    private fun checkLowBatteryWarnings() {
        if (!::sharedPreferences.isInitialized) return
        val components = listOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE)
        val current = batteryNotification.getBattery().associateBy { it.component }
        val newlyLow = mutableListOf<Battery>()

        sharedPreferences.edit {
            components.forEach { component ->
                val battery = current[component] ?: return@forEach
                if (!LowBatteryAlertPolicy.isFresh(battery)) return@forEach
                val key = lowBatteryAlertPreferenceKey(component)
                val thresholdKey = "${key}_threshold"
                val legacyKey = legacyLowBatteryAlertPreferenceKey(component)
                val lastThreshold = when {
                    sharedPreferences.contains(thresholdKey) ->
                        sharedPreferences.getInt(thresholdKey, -1).takeIf { it in 0..20 }
                    sharedPreferences.getBoolean(legacyKey, false) -> 20
                    else -> null
                }

                if (LowBatteryAlertPolicy.shouldRearm(battery)) {
                    putInt(thresholdKey, -1)
                    putBoolean(legacyKey, false)
                    putInt("${key}_last_level", battery.level)
                    remove("${key}_last_alert_at")
                    return@forEach
                }

                putInt("${key}_last_level", battery.level)
                val nextThreshold = LowBatteryAlertPolicy.nextThresholdToAlert(battery, lastThreshold)
                    ?: return@forEach
                newlyLow += battery
                putInt(thresholdKey, nextThreshold)
                putBoolean(legacyKey, true)
                putLong("${key}_last_alert_at", System.currentTimeMillis())
            }
        }

        if (newlyLow.isNotEmpty()) {
            Handler(Looper.getMainLooper()).post {
                val warnings = newlyLow.sortedBy { it.level }
                if (Settings.canDrawOverlays(this)) {
                    warnings.forEach { warning ->
                        pendingLowBatteryWarnings.removeIf { it.component == warning.component }
                        pendingLowBatteryWarnings.addLast(warning)
                    }
                    while (pendingLowBatteryWarnings.size > LOW_BATTERY_QUEUE_LIMIT) {
                        pendingLowBatteryWarnings.removeFirst()
                    }
                    showNextLowBatteryWarning()
                } else {
                    warnings.forEach(::postLowBatteryNotification)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showNextLowBatteryWarning() {
        if (isLowBatteryWarningShowing) return
        val battery = pendingLowBatteryWarnings.pollFirst() ?: return
        if (!Settings.canDrawOverlays(this)) {
            postLowBatteryNotification(battery)
            showNextLowBatteryWarning()
            return
        }

        isLowBatteryWarningShowing = true
        val alert = lowBatteryAlertWindow ?: LowBatteryAlertWindow(this) {
            Handler(Looper.getMainLooper()).post {
                isLowBatteryWarningShowing = false
                showNextLowBatteryWarning()
            }
        }.also { lowBatteryAlertWindow = it }
        val shown = alert.show(
            sharedPreferences.getString("name", "AirPods") ?: "AirPods",
            lowBatteryArtwork(battery),
            battery.level,
            lowBatteryComponentName(battery.component),
        )
        if (!shown) {
            isLowBatteryWarningShowing = false
            postLowBatteryNotification(battery)
            // If the prior view is still completing teardown, let its close
            // callback run before trying the next queued component.
            Handler(Looper.getMainLooper()).postDelayed({ showNextLowBatteryWarning() }, 300L)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun postLowBatteryNotification(battery: Battery) {
        val model = AirPodsModels.getModelByModelNumber(
            sharedPreferences.getString("airpods_model_number", "") ?: ""
        )
        val artworkRes = lowBatteryArtwork(battery)
        val deviceName = sharedPreferences.getString("name", model?.displayName ?: "AirPods")
            ?: model?.displayName
            ?: "AirPods"

        val notificationManager = getSystemService(NotificationManager::class.java)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ||
            !notificationManager.areNotificationsEnabled()
        ) {
            Log.w(TAG, "Low-battery warning suppressed: overlay and notification permissions are unavailable")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    LOW_BATTERY_CHANNEL_ID,
                    getString(R.string.notification_channel_low_battery),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = getString(R.string.notification_channel_low_battery_description)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }

        val componentName = when (battery.component) {
            BatteryComponent.LEFT -> getString(R.string.left)
            BatteryComponent.RIGHT -> getString(R.string.right)
            else -> getString(R.string.case_alt)
        }
        val notificationId = LOW_BATTERY_NOTIFICATION_ID + battery.component
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = getString(R.string.low_battery_notification_text, componentName)
        notificationManager.notify(
            notificationId,
            NotificationCompat.Builder(this, LOW_BATTERY_CHANNEL_ID)
                .setSmallIcon(R.drawable.airpods)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, artworkRes))
                .setContentTitle(getString(R.string.low_battery_notification_title, deviceName, battery.level))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun lowBatteryArtwork(battery: Battery): Int {
        val model = AirPodsModels.getModelByModelNumber(
            sharedPreferences.getString("airpods_model_number", "") ?: ""
        )
        return when (battery.component) {
            BatteryComponent.LEFT -> model?.leftBudsRes ?: R.drawable.airpods_pro_left_notification
            BatteryComponent.RIGHT -> model?.rightBudsRes ?: R.drawable.airpods_pro_right_notification
            BatteryComponent.CASE -> model?.caseRes ?: R.drawable.airpods_pro_case_notification
            else -> R.drawable.airpods
        }
    }

    private fun lowBatteryComponentName(component: Int): String = getString(
        when (component) {
            BatteryComponent.LEFT -> R.string.left
            BatteryComponent.RIGHT -> R.string.right
            else -> R.string.case_alt
        },
    )

    private fun lowBatteryAlertPreferenceKey(component: Int): String {
        val modelProfile = (sharedPreferences.getString("airpods_model_number", null) ?: "unknown")
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "unknown" }
        val part = when (component) {
            BatteryComponent.LEFT -> "left"
            BatteryComponent.RIGHT -> "right"
            else -> "case"
        }
        return "low_battery_${modelProfile}_$part"
    }

    private fun legacyLowBatteryAlertPreferenceKey(component: Int): String = when (component) {
        BatteryComponent.LEFT -> "low_battery_alerted_left"
        BatteryComponent.RIGHT -> "low_battery_alerted_right"
        else -> "low_battery_alerted_case"
    }

    private fun persistLastKnownBatteryLevels() {
        if (!::sharedPreferences.isInitialized) return
        val batteries = batteryNotification.getBattery().associateBy { it.component }
        sharedPreferences.edit {
            listOf(
                BatteryComponent.LEFT to "last_known_battery_left",
                BatteryComponent.RIGHT to "last_known_battery_right",
                BatteryComponent.CASE to "last_known_battery_case",
            ).forEach { (component, key) ->
                val battery = batteries[component] ?: return@forEach
                if (battery.level in 0..100 &&
                    (battery.status != BatteryStatus.DISCONNECTED || sharedPreferences.contains(key))
                ) {
                    putInt(key, battery.level)
                }
            }
            if (batteries.values.any {
                    it.isLevelKnown && it.level in 0..100 && it.status != BatteryStatus.DISCONNECTED
                }
            ) {
                sharedPreferences.getString("airpods_model_number", null)
                    ?.takeIf(String::isNotBlank)
                    ?.let { putString("last_known_battery_model_number", it) }
            }
        }
    }

    fun updateNoiseControlWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoiseControlWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val ancStatus = ancNotification.status
        val allowOffMode = sharedPreferences.getBoolean("off_listening_mode", true)
        appWidgetManager.updateAppWidget(
            widgetIds,
            NoiseControlWidget.responsiveViews(this, ancStatus, allowOffMode),
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun updateNotificationContent(
        connected: Boolean, airpodsName: String? = null, batteryList: List<Battery>? = null
    ) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (BluetoothConnectionManager.aacpSocket == null) {
            return
        }
        if (BluetoothConnectionManager.aacpSocket?.isConnected == true) {
            val updatedNotificationBuilder =
                NotificationCompat.Builder(this, "airpods_connection_status")
                    .setSmallIcon(R.drawable.airpods)
                    .setContentTitle(airpodsName ?: config.deviceName).setContentText(
                        """${
                        batteryList?.find { it.component == BatteryComponent.LEFT }?.let {
                            if (it.status != BatteryStatus.DISCONNECTED) {
                                "L: ${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
                            } else {
                                ""
                            }
                        } ?: ""
                    } ${
                        batteryList?.find { it.component == BatteryComponent.RIGHT }?.let {
                            if (it.status != BatteryStatus.DISCONNECTED) {
                                "R: ${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
                            } else {
                                ""
                            }
                        } ?: ""
                    } ${
                        batteryList?.find { it.component == BatteryComponent.CASE }?.let {
                            if (it.status != BatteryStatus.DISCONNECTED) {
                                "Case: ${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
                            } else {
                                ""
                            }
                        } ?: ""
                    }""").setContentIntent(pendingIntent).setCategory(Notification.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)

            if (disconnectedBecauseReversed) {
                updatedNotificationBuilder.addAction(
                    R.drawable.ic_bluetooth, "Reconnect", PendingIntent.getService(
                        this, 0, Intent(this, AirPodsService::class.java).apply {
                            action = "me.kavishdevar.librepods.RECONNECT_AFTER_REVERSE"
                        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            val updatedNotification = updatedNotificationBuilder.build()

            notificationManager.notify(2, updatedNotification)
        } else if (!connected) {
            notificationManager.cancel(2)
        } else if (!config.bleOnlyMode && BluetoothConnectionManager.aacpSocket?.isConnected != true) {
            showSocketConnectionFailureNotification("BluetoothConnectionManager.aacpSocket? created, but not connected. Check logs")
        }
    }

    fun handleIncomingCall() {
        if (isInCall || !CallGesturePolicy.canRecognize(config.headGestures, phoneCallPhase, callGestureDecisionRequested)) {
            Log.d(TAG, "Call head gestures are unavailable outside the incoming ringing state")
            return
        }
        initGestureDetector()
        startHeadTracking()
        gestureDetector?.startDetection { accepted ->
            // A gesture may have been recognized just before OFFHOOK was
            // delivered. Re-check immediately before acting so it can never
            // reject/end a call that has already been answered.
            if (isInCall || !CallGesturePolicy.canRecognize(
                    config.headGestures,
                    phoneCallPhase,
                    callGestureDecisionRequested,
                )
            ) {
                Log.d(TAG, "Ignoring call gesture because the call is no longer ringing")
            } else if (accepted) {
                answerCall()
            } else {
                rejectCall()
            }
        }
    }

    private fun stopCallGestureRecognition(reason: String) {
        handleIncomingCallOnceConnected = false
        gestureDetector?.stopDetection()
        if (isHeadTrackingActive) stopHeadTracking()
        Log.d(TAG, "Stopped call head-gesture recognition: $reason")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun testHeadGestures(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            gestureDetector?.startDetection(doNotStop = true) { accepted ->
                if (continuation.isActive) {
                    continuation.resume(accepted) { _, _, _ ->
                        gestureDetector?.stopDetection()
                    }
                }
            }
        }
    }

    private fun answerCall() {
        callGestureDecisionRequested = true
        stopCallGestureRecognition("answer requested")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager.acceptRingingCall() // TODO: Switch to InCallService (needs CDM association)
                } else {
                    callGestureDecisionRequested = false
                    sendToast("Phone permission is required to answer with a head gesture")
                    return
                }
            } else {
                val telephonyService = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                val telephonyClass = Class.forName(telephonyService.javaClass.name)
                val method = telephonyClass.getDeclaredMethod("getITelephony")
                method.isAccessible = true
                val telephonyInterface = method.invoke(telephonyService)
                val answerCallMethod =
                    telephonyInterface.javaClass.getDeclaredMethod("answerRingingCall")
                answerCallMethod.invoke(telephonyInterface)
            }

            sendToast("Call answered via head gesture")
        } catch (e: Exception) {
            callGestureDecisionRequested = false
            e.printStackTrace()
            sendToast("Failed to answer call: ${e.message}")
        } finally {
            islandWindow?.close()
        }
    }

    private fun rejectCall() {
        callGestureDecisionRequested = true
        stopCallGestureRecognition("reject requested")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager.endCall() // TODO: Switch to InCallService (needs CDM association)
                }
            } else {
                val telephonyService = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                val telephonyClass = Class.forName(telephonyService.javaClass.name)
                val method = telephonyClass.getDeclaredMethod("getITelephony")
                method.isAccessible = true
                val telephonyInterface = method.invoke(telephonyService)
                val endCallMethod = telephonyInterface.javaClass.getDeclaredMethod("endCall")
                endCallMethod.invoke(telephonyInterface)
            }

            sendToast("Call rejected via head gesture")
        } catch (e: Exception) {
            e.printStackTrace()
            sendToast("Failed to reject call: ${e.message}")
        } finally {
            islandWindow?.close()
        }
    }

    fun sendToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun processHeadTrackingData(data: ByteArray) {
        val horizontal = ByteBuffer.wrap(data, 51, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val vertical = ByteBuffer.wrap(data, 53, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        try {
            gestureDetector?.processHeadOrientation(horizontal, vertical)
        } catch (e: Exception) {
            Log.w(TAG, "Gesture frame processing failed (${data.size} bytes): ${e.message}")
        }
    }

    private lateinit var connectionReceiver: BroadcastReceiver

    private fun resToUri(resId: Int): Uri? {
        return try {
            Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(packageName)
                .appendPath(applicationContext.resources.getResourceTypeName(resId))
                .appendPath(applicationContext.resources.getResourceEntryName(resId)).build()
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    @Suppress("PrivatePropertyName")
    private val VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV = "+IPHONEACCEV"

    @Suppress("PrivatePropertyName")
    private val VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV_BATTERY_LEVEL = 1

    @Suppress("PrivatePropertyName")
    private val APPLE = 0x004C

    @Suppress("PrivatePropertyName")
    private val ACTION_BATTERY_LEVEL_CHANGED =
        "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    @Suppress("PrivatePropertyName")
    private val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    @Suppress("PrivatePropertyName")
    private val PACKAGE_ASI = "com.google.android.settings.intelligence"

    @Suppress("PrivatePropertyName")
    private val ACTION_ASI_UPDATE_BLUETOOTH_DATA = "batterywidget.impl.action.update_bluetooth_data"

    @SuppressLint("MissingPermission")
    fun broadcastBatteryInformation() {
        if (device == null || checkSelfPermission("android.permission.INTERACT_ACROSS_USERS") != PackageManager.PERMISSION_GRANTED) return

        val batteryList = batteryNotification.getBattery()
        val leftBattery = batteryList.find { it.component == BatteryComponent.LEFT }
        val rightBattery = batteryList.find { it.component == BatteryComponent.RIGHT }

        // Calculate unified battery level (minimum of left and right)
        val batteryUnified = minOf(
            leftBattery?.level ?: 100, rightBattery?.level ?: 100
        )

        // Check charging status
        val isLeftCharging = leftBattery?.status == BatteryStatus.CHARGING
        val isRightCharging = rightBattery?.status == BatteryStatus.CHARGING
        isLeftCharging && isRightCharging

        // Create arguments for vendor-specific event
        val arguments = arrayOf<Any>(
            1, // Number of key/value pairs
            VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV_BATTERY_LEVEL, // IndicatorType: Battery Level
            batteryUnified // Battery Level
        )

        // Broadcast vendor-specific event
        val intent = Intent(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT).apply {
            putExtra(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD,
                VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV
            )
            putExtra(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE,
                BluetoothHeadset.AT_CMD_TYPE_SET
            )
            putExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS, arguments)
            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            putExtra(BluetoothDevice.EXTRA_NAME, device?.name)
            addCategory("${BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY}.$APPLE")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sendBroadcastAsUser(
                    intent,
                    UserHandle.getUserHandleForUid(-1),
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                sendBroadcastAsUser(intent, UserHandle.getUserHandleForUid(-1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send vendor-specific event: ${e.message}")
        }

        // Broadcast battery level changes
        val batteryIntent = Intent(ACTION_BATTERY_LEVEL_CHANGED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            putExtra(EXTRA_BATTERY_LEVEL, batteryUnified)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sendBroadcast(batteryIntent, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                sendBroadcastAsUser(batteryIntent, UserHandle.getUserHandleForUid(-1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send battery level broadcast: ${e.message}")
        }

        // Update Android Settings Intelligence's battery widget
        val statusIntent = Intent(ACTION_ASI_UPDATE_BLUETOOTH_DATA).apply {
            setPackage(PACKAGE_ASI)
            putExtra(ACTION_BATTERY_LEVEL_CHANGED, intent)
        }

        try {
            sendBroadcastAsUser(statusIntent, UserHandle.getUserHandleForUid(-1))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ASI battery level broadcast: ${e.message}")
        }

        Log.d(TAG, "Broadcast battery level $batteryUnified% to system")
    }

    private fun setMetadatas(d: BluetoothDevice) {
        if (checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "no permission BLUETOOTH_PRIVILEGED, returning")
            return
        }
        Log.d(TAG, "has permission BLUETOOTH_PRIVILEGED, proceeding")
        d.let { device ->
            val instance = airpodsInstance
            if (instance != null) {
                val metadataSet = SystemApisUtils.setMetadata(
                    device, device.METADATA_MODEL_NAME, instance.model.name.toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_DEVICE_TYPE,
                    device.DEVICE_TYPE_UNTETHERED_HEADSET.toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_UNTETHERED_CASE_ICON,
                    resToUri(instance.model.caseRes).toString().toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_UNTETHERED_RIGHT_ICON,
                    resToUri(instance.model.rightBudsRes).toString().toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_UNTETHERED_LEFT_ICON,
                    resToUri(instance.model.leftBudsRes).toString().toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_MANUFACTURER_NAME,
                    instance.model.manufacturer.toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device, device.METADATA_COMPANION_APP, packageName.toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_UNTETHERED_CASE_LOW_BATTERY_THRESHOLD,
                    "20".toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_UNTETHERED_LEFT_LOW_BATTERY_THRESHOLD,
                    "20".toByteArray()
                ) && SystemApisUtils.setMetadata(
                    device,
                    device.METADATA_UNTETHERED_RIGHT_LOW_BATTERY_THRESHOLD,
                    "20".toByteArray()
                )
                Log.d(TAG, "Metadata set: $metadataSet")
            } else {
                Log.w(
                    TAG,
                    "AirPods demoInstance is not of type AirPodsInstance, skipping metadata setting"
                )
            }
        }
    }

    @Suppress("ClassName")
    private object bluetoothReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            val bluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    "android.bluetooth.device.extra.DEVICE", BluetoothDevice::class.java
                )
            } else {
                intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE") as BluetoothDevice?
            }
            val action = intent.action
            val context = context?.applicationContext
            if (bluetoothDevice != null && !action.isNullOrEmpty()) {
                val uuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

                val savedMac = context?.getSharedPreferences("settings", MODE_PRIVATE)
                    ?.getString("mac_address", "") ?: ""
                val matchedByMac = savedMac.isNotEmpty() && bluetoothDevice.address == savedMac
                val matchedByUuid = bluetoothDevice.uuids?.contains(uuid) == true
                val matchedByName = bluetoothDevice.name?.contains("AirPods", true) == true
                val matchesSelectedDevice = if (savedMac.isNotEmpty()) {
                    matchedByMac
                } else {
                    matchedByUuid || matchedByName
                }

                val profileState = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_DISCONNECTED,
                )
                val event = BluetoothConnectionEventPolicy.classify(
                    action = action,
                    profileState = profileState,
                    isSelectedAirPods = matchesSelectedDevice,
                )

                if (action == BluetoothDevice.ACTION_ACL_CONNECTED && !matchesSelectedDevice) {
                    bluetoothDevice.fetchUuidsWithSdp()
                    return
                }

                when (event) {
                    BluetoothConnectionEventPolicy.Event.LINK_CANDIDATE -> {
                        Log.d(TAG, "Selected AirPods link appeared; waiting for an audio profile")
                        context?.let { appContext ->
                            appContext.sendBroadcast(
                                Intent(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED)
                                    .putExtra("device", bluetoothDevice)
                                    .setPackage(appContext.packageName)
                            )
                        }
                    }

                    BluetoothConnectionEventPolicy.Event.CONNECTED -> {
                        Log.i(TAG, "Selected AirPods Android audio profile connected")
                        val profile = BluetoothConnectionEventPolicy.profileForAction(action)
                        if (profile != null) {
                            context?.let { appContext ->
                                appContext.sendBroadcast(
                                    Intent(AirPodsNotifications.AIRPODS_AUDIO_PROFILE_EVENT)
                                        .putExtra("profile", profile)
                                        .putExtra("connected", true)
                                        .setPackage(appContext.packageName)
                                )
                            }
                        }
                        val detected = Intent(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED)
                            .putExtra("device", bluetoothDevice)
                            .putExtra(EXTRA_ANDROID_AUDIO_PROFILE_CONNECTED, true)
                            .setPackage(context?.packageName)
                        context?.sendBroadcast(detected)
                    }

                    BluetoothConnectionEventPolicy.Event.PROFILE_DISCONNECTED -> {
                        val profile = BluetoothConnectionEventPolicy.profileForAction(action)
                        if (profile != null) {
                            context?.let { appContext ->
                                appContext.sendBroadcast(
                                    Intent(AirPodsNotifications.AIRPODS_AUDIO_PROFILE_EVENT)
                                        .putExtra("profile", profile)
                                        .putExtra("connected", false)
                                        .setPackage(appContext.packageName)
                                )
                            }
                        }
                    }

                    BluetoothConnectionEventPolicy.Event.DISCONNECTED -> {
                        Log.i(TAG, "Selected AirPods Bluetooth link disconnected")
                        context?.let { appContext ->
                            listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profile ->
                                appContext.sendBroadcast(
                                    Intent(AirPodsNotifications.AIRPODS_AUDIO_PROFILE_EVENT)
                                        .putExtra("profile", profile)
                                        .putExtra("connected", false)
                                        .setPackage(appContext.packageName)
                                )
                            }
                            appContext.sendBroadcast(
                                Intent(AirPodsNotifications.AIRPODS_DISCONNECTED)
                                    .setPackage(appContext.packageName)
                            )
                        }
                    }

                    BluetoothConnectionEventPolicy.Event.IGNORE -> Unit
                }
            }
        }
    }

    val externalBroadcastFilter = IntentFilter().apply {
        addAction("me.kavishdevar.librepods.SET_ANC_MODE")
        addAction("me.kavishdevar.librepods.CONVO_DETECT")
    }
    var externalBroadcastReceiver: BroadcastReceiver? = null

    @SuppressLint("InlinedApi", "MissingPermission", "UnspecifiedRegisterReceiverFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate is not guaranteed to run for each startForegroundService call.
        // Promote here too, before any command-specific work.
        if (!startForegroundNotification()) return START_NOT_STICKY
        Log.d(TAG, "Service started with intent action: ${intent?.action}")

        if (intent?.action == "me.kavishdevar.librepods.RECONNECT_AFTER_REVERSE") {
            Log.d(TAG, "reconnect after reversed received, taking over")
            disconnectedBecauseReversed = false
            otherDeviceTookOver = false
            updateCheckScope.launch {
                takeOver("music", manualTakeOverAfterReversed = true)
            }
        }

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission", "HardwareIds")
    fun takeOver(
        takingOverFor: String,
        manualTakeOverAfterReversed: Boolean = false,
        startHeadTrackingAgain: Boolean = false
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateCheckScope.launch {
                takeOver(takingOverFor, manualTakeOverAfterReversed, startHeadTrackingAgain)
            }
            return
        }
        if (takingOverFor == "reverse") {
            aacpManager.sendControlCommand(
                AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION.value, 1
            )
            aacpManager.sendMediaInformataion(
                localMac
            )
            aacpManager.sendHijackReversed(
                localMac
            )
            connectAudio(
                this@AirPodsService, device
            )
            otherDeviceTookOver = false
        }
        val ownsConnection = aacpManager.getControlCommandStatus(AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION)?.value?.get(0)?.toInt()
        Log.d(
            TAG, "owns connection: $ownsConnection"
        )
        if (BluetoothConnectionManager.aacpSocket?.isConnected == true) {
            if (!XposedRemotePrefProvider.create().getBoolean("vendor_id_hook", false) || ownsConnection == 0) {
                Log.d(TAG, "not taking over, vendorid is probably not set to apple")
                return
            }
            if (aacpManager.getControlCommandStatus(AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION)?.value[0]?.toInt() != 1 || (aacpManager.audioSource?.mac != localMac && aacpManager.audioSource?.type != AACPManager.Companion.AudioSourceType.NONE)) {
                if (disconnectedBecauseReversed) {
                    if (manualTakeOverAfterReversed) {
                        Log.d(TAG, "forcefully taking over despite reverse as user requested")
                        disconnectedBecauseReversed = false
                    } else {
                        Log.d(
                            TAG,
                            "connected locally, but can not hijack as other device had reversed"
                        )
                        return
                    }
                }

                Log.d(TAG, "already connected locally, hijacking connection by asking AirPods")
                aacpManager.sendControlCommand(
                    AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION.value, 1
                )
                aacpManager.sendMediaInformataion(
                    localMac
                )
                aacpManager.sendSmartRoutingShowUI(
                    localMac
                )
                aacpManager.sendHijackRequest(
                    localMac
                )
                otherDeviceTookOver = false
                connectAudio(this, device)
                showConnectionPopup(
                    this,
                    sharedPreferences.getString("name", "AirPods Pro") ?: "AirPods",
                    IslandType.CONNECTED
                )

                CoroutineScope(Dispatchers.IO).launch {
                    delay(500) // a2dp takes time, and so does taking control + AirPods pause it for no reason after connecting
                    if (takingOverFor == "music") {
                        Log.d(TAG, "Resuming music after taking control")
                        MediaController.sendPlay(replayWhenPaused = true)
                    } else if (startHeadTrackingAgain) {
                        Log.d(TAG, "Starting head tracking again after taking control")
                        Handler(Looper.getMainLooper()).postDelayed({
                            startHeadTracking()
                        }, 500)
                    }
                    delay(1000) // should ideally have a callback when it's taken over because for some reason android doesn't dispatch when it's paused
                    if (takingOverFor == "music") {
                        Log.d(TAG, "resuming again just in case")
                        MediaController.sendPlay(force = true)
                    }
                }
            } else {
                Log.d(
                    TAG, "Already connected locally and already own connection, skipping takeover"
                )
            }
            return
        }

//        if (CrossDevice.isAvailable) {
//            Log.d(TAG, "CrossDevice is available, continuing")
//        }
//        else if (bleManager.getMostRecentStatus()?.isLeftInEar == true || bleManager.getMostRecentStatus()?.isRightInEar == true) {
//            Log.d(TAG, "At least one AirPod is in ear, continuing")
//        }
//        else {
//            Log.d(TAG, "CrossDevice not available and AirPods not in ear, skipping")
//            return
//        }

        if (bleManager.getMostRecentStatus()?.isLeftInEar == false && bleManager.getMostRecentStatus()?.isRightInEar == false) {
            Log.d(TAG, "Both AirPods are out of ear, not taking over audio")
            return
        }

        val shouldTakeOverPState = when (takingOverFor) {
            "music" -> config.takeoverWhenMediaStart
            "call" -> config.takeoverWhenRingingCall
            else -> false
        }

        if (!shouldTakeOverPState) {
            Log.d(TAG, "Not taking over audio, phone state takeover disabled")
            return
        }

        val shouldTakeOver = when (bleManager.getMostRecentStatus()?.connectionState) {
            "Disconnected" -> config.takeoverWhenDisconnected
            "Idle" -> config.takeoverWhenIdle
            "Music" -> config.takeoverWhenMusic
            "Call" -> config.takeoverWhenCall
            "Ringing" -> config.takeoverWhenCall
            "Hanging Up" -> config.takeoverWhenCall
            else -> false
        }

        if (!shouldTakeOver) {
            Log.d(TAG, "Not taking over audio, airpods state takeover disabled")
            return
        }

        if (takingOverFor == "music") {
            Log.d(TAG, "Pausing music so that it doesn't play through speakers")
            MediaController.pausedWhileTakingOver = true
            MediaController.sendPause(true)
        } else {
            handleIncomingCallOnceConnected = true
        }

        Log.d(TAG, "Taking over audio")
//        CrossDevice.sendRemotePacket(CrossDevicePackets.REQUEST_DISCONNECT.packet)
        Log.d(TAG, "Preparing to connect to the selected paired AirPods device")

//        sharedPreferences.edit { putBoolean("CrossDeviceIsAvailable", false) }
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        device = bluetoothAdapter.bondedDevices.find {
            it.address == macAddress
        }

        if (device != null) {
            if (config.bleOnlyMode) {
                // In BLE-only mode, just show connecting status without actual L2CAP connection
                Log.d(TAG, "BLE-only mode: showing connecting status without L2CAP connection")
                updateNotificationContent(
                    true, config.deviceName, batteryNotification.getBattery()
                )
                // Set a temporary connecting state
//                isConnectedLocally = false // Keep as false since we're not actually connecting to L2CAP
            } else {
                connectToSocket(bluetoothAdapter, device!!)
                connectAudio(this, device)
//                isConnectedLocally = true
            }
        }
        showIsland(
            this,
            lowestKnownBatteryLevel(),
            IslandType.TAKING_OVER
        )

//        CrossDevice.isAvailable = false
    }

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun connectToSocket(
        adapter: BluetoothAdapter, device: BluetoothDevice, manual: Boolean = false
    ) {
        // BluetoothSocket.connect() is a blocking platform call. Never let an unexpected
        // caller run the connection procedure on the Android main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateCheckScope.launch {
                connectToSocket(adapter, device, manual)
            }
            return
        }
        if (!canAttemptAacpControlSession(
                rootlessBluetoothSupported = hasRootlessBluetoothSupport(),
                bluetoothScopeEnabled = XposedState.bluetoothScopeEnabled,
            )
        ) {
            Log.w(
                TAG,
                "Skipping AACP control socket on an unsupported rootless Bluetooth stack; keeping standard audio profile intact",
            )
            if (manual) sendToast(getString(R.string.device_bluetooth_support_limited))
            return
        }
        if (!socketConnectInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "AACP connection attempt already in progress; ignoring duplicate trigger")
            return
        }
        try {
        if (BluetoothConnectionManager.aacpSocket?.isConnected == true) return
        Log.d(TAG, "<LogCollector:Start> Connecting to socket")
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
//        if (!isConnectedLocally) {
        val socket = try {
            createBluetoothSocket(adapter, device, uuid, 4097)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create BluetoothSocket: ${e.message}")
            showSocketConnectionFailureNotification("Failed to create Bluetooth socket: ${e.localizedMessage}")
            return
        }

        try {
            try {
                BluetoothSocketConnectTimeout.connect(
                    timeoutMs = 5_000,
                    closeSocket = { socket.close() },
                    connectSocket = { socket.connect() },
                )
                        this@AirPodsService.device = device
                        val xposedRemotePref = XposedRemotePrefProvider.create()
                        val attSocket = if (xposedRemotePref.getBoolean("vendor_id_hook", false)) {
                            createBluetoothSocket(
                                adapter,
                                device,
                                ParcelUuid.fromString("00000000-0000-0000-0000-000000000000"),
                                31
                            )
                        } else null
                        attSocket?.let { socket ->
                            BluetoothSocketConnectTimeout.connect(
                                timeoutMs = 5_000,
                                closeSocket = { socket.close() },
                                connectSocket = { socket.connect() },
                            )
                        }

                        if (attSocket != null) {
                            attManager.startReader()
                            attManager.readCharacteristic(ATTHandles.LOUD_SOUND_REDUCTION)
                            attManager.readCharacteristic(ATTHandles.TRANSPARENCY)
                            attManager.readCharacteristic(ATTHandles.HEARING_AID)
                        }

                        BluetoothConnectionManager.aacpSocket = socket
                        BluetoothConnectionManager.attSocket = attSocket

                        // Create AirPodsInstance from stored config if available
                        if (airpodsInstance == null && config.airpodsModelNumber.isNotEmpty()) {
                            val model =
                                AirPodsModels.getModelByModelNumber(config.airpodsModelNumber)
                            if (model != null) {
                                airpodsInstance = AirPodsInstance(
                                    name = config.airpodsName,
                                    model = model,
                                    actualModelNumber = config.airpodsModelNumber,
                                    serialNumber = config.airpodsSerialNumber,
                                    leftSerialNumber = config.airpodsLeftSerialNumber,
                                    rightSerialNumber = config.airpodsRightSerialNumber,
                                    version1 = config.airpodsVersion1,
                                    version2 = config.airpodsVersion2,
                                    version3 = config.airpodsVersion3,
                                )
                                setMetadatas(device)
                            }
                        }

                        updateNotificationContent(
                            true, config.deviceName, batteryNotification.getBattery()
                        )
                        Log.d(TAG, "<LogCollector:Complete:Success> Socket connected")
                        sharedPreferences.edit { putBoolean("connection_successful", true) }
                        if (!sharedPreferences.contains("first_connection_successful_time")) {
                            sharedPreferences.edit {
                                putLong(
                                    "first_connection_successful_time",
                                    System.currentTimeMillis()
                                )
                            }
                        }
                        sendBroadcast(
                            Intent(AirPodsNotifications.AIRPODS_L2CAP_CONNECTED)
                                .setPackage(packageName)
                        )
                        showConnectionPopup(
                            this@AirPodsService,
                            config.deviceName.ifBlank { "AirPods" },
                            IslandType.CONNECTED,
                        )
            } catch (e: Exception) {
//                        sharedPreferences.edit { putBoolean("connection_successful", false) }
                        runCatching { socket.close() }
                        Log.d(
                            TAG, "<LogCollector:Complete:Failed> Socket not connected, ${e.message}"
                        )
                        if (manual) {
                            sendToast(
                                "Couldn't connect to socket: ${e.localizedMessage}"
                            )
                        } else {
                            showSocketConnectionFailureNotification("Couldn't connect to socket: ${e.localizedMessage}")
                        }
                        return
//                            throw e // lol how did i not catch this before... gonna comment this line instead of removing to preserve history
            }
            if (!socket.isConnected) {
                runCatching { socket.close() }
                Log.d(TAG, "<LogCollector:Complete:Failed> socket not connected")
                if (manual) {
                    sendToast(
                        "Couldn't connect to socket: timeout."
                    )
                } else {
                    showSocketConnectionFailureNotification("Couldn't connect to socket: Timeout")
                }
                return
            }
            this@AirPodsService.device = device
            BluetoothConnectionManager.aacpSocket?.let {
                aacpManager.sendPacket(aacpManager.createHandshakePacket())
                aacpManager.sendSetFeatureFlagsPacket()
                aacpManager.sendNotificationRequest()
                Log.d(TAG, "Requesting proximity keys")
                aacpManager.sendRequestProximityKeys((AACPManager.Companion.ProximityKeyType.IRK.value + AACPManager.Companion.ProximityKeyType.ENC_KEY.value).toByte())
                CoroutineScope(Dispatchers.IO).launch {
                    delay(200)
                    aacpManager.sendPacket(aacpManager.createHandshakePacket())
                    delay(200)
                    aacpManager.sendSetFeatureFlagsPacket()
                    delay(200)
                    aacpManager.sendNotificationRequest()
                    delay(200)
                    aacpManager.sendSomePacketIDontKnowWhatItIs()
                    delay(200)
                    aacpManager.sendRequestProximityKeys((AACPManager.Companion.ProximityKeyType.IRK.value + AACPManager.Companion.ProximityKeyType.ENC_KEY.value).toByte())
                    when {
                        isInCall || phoneCallPhase == PhoneCallPhase.ACTIVE || callGestureDecisionRequested -> {
                            stopCallGestureRecognition("AAP session connected after call answer")
                        }
                        !handleIncomingCallOnceConnected -> startHeadTracking()
                        else -> handleIncomingCall()
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        aacpManager.sendPacket(aacpManager.createHandshakePacket())
                        aacpManager.sendSetFeatureFlagsPacket()
                        aacpManager.sendNotificationRequest()
                        aacpManager.sendRequestProximityKeys(AACPManager.Companion.ProximityKeyType.IRK.value)
                        if (isInCall || phoneCallPhase == PhoneCallPhase.ACTIVE ||
                            callGestureDecisionRequested || !handleIncomingCallOnceConnected
                        ) {
                            stopHeadTracking()
                        }
                    }, 5000)

                    sendBroadcast(
                        Intent(AirPodsNotifications.AIRPODS_CONNECTED).putExtra("device", device)
                            .apply {
                                setPackage(packageName)
                            })

                    setupStemActions()

                    while (socket.isConnected) {
                        try {
                            val buffer = ByteArray(1024)
                            val bytesRead = it.inputStream.read(buffer)
                            var data: ByteArray
                            if (bytesRead > 0) {
                                data = buffer.copyOfRange(0, bytesRead)
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DATA).apply {
                                    putExtra("data", buffer.copyOfRange(0, bytesRead))
                                    setPackage(packageName)
                                })
                                val bytes = buffer.copyOfRange(0, bytesRead)
//                                    CrossDevice.sendReceivedPacket(bytes)
                                updateNotificationContent(
                                    true,
                                    sharedPreferences.getString("name", device.name),
                                    batteryNotification.getBattery()
                                )

                                aacpManager.receivePacket(data)

                                if (!isHeadTrackingData(data)) {
                                    logPacket(data, "AirPods")
                                }

                            } else if (bytesRead == -1) {
                                Log.d("AirPodsService", "socket closed (bytesRead = -1)")
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED).apply {
                                    setPackage(packageName)
                                })
                                aacpManager.disconnected()
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error reading data, we have probably disconnected.")
                            e.printStackTrace()
                            sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED).apply {
                                setPackage(packageName)
                            })
                            aacpManager.disconnected()
                            return@launch
                        }

                    }
                    Log.d("AirPods Service", "socket closed")
//                        isConnectedLocally = false
                    aacpManager.disconnected()
                    updateNotificationContent(false)
                    sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED).apply {
                        setPackage(packageName)
                    })
                }
            }
        } catch (e: Exception) {
            runCatching { socket.close() }
            e.printStackTrace()
            Log.d(TAG, "Failed to connect to BluetoothConnectionManager.aacpSocket?: ${e.message}")
            showSocketConnectionFailureNotification("Failed to establish connection: ${e.localizedMessage}")
//                isConnectedLocally = false
            this@AirPodsService.device = device
            updateNotificationContent(false)
        }
//        } else {
//            Log.d(TAG, "Already connected locally, skipping BluetoothConnectionManager.aacpSocket? connection (isConnectedLocally = $isConnectedLocally, BluetoothConnectionManager.aacpSocket?.isConnected = ${this::BluetoothConnectionManager.aacpSocket?.isInitialized && BluetoothConnectionManager.aacpSocket?.isConnected})")
//        }
        } finally {
            socketConnectInProgress.set(false)
        }
    }

    fun disconnectForCD() {
        BluetoothConnectionManager.aacpSocket?.close()
        MediaController.pausedWhileTakingOver = false
        Log.d(TAG, "Disconnected from AirPods, showing island.")
        showIsland(
            this,
            lowestKnownBatteryLevel(),
            IslandType.MOVED_TO_REMOTE
        )
        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter
        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val connectedDevices = proxy.connectedDevices
                    if (connectedDevices.isNotEmpty()) {
                        MediaController.sendPause()
                    }
                }
                bluetoothAdapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
//        isConnectedLocally = false
//        CrossDevice.isAvailable = true
    }

    fun disconnectAirPods() {
        if (BluetoothConnectionManager.aacpSocket == null) return
        try {
            BluetoothConnectionManager.aacpSocket?.close()
        } catch(e: Exception) {
            Log.e(TAG, "error closing aacp socket ${e.message}")
        }
//        isConnectedLocally = false
        aacpManager.disconnected()

        BluetoothConnectionManager.aacpSocket = null
        BluetoothConnectionManager.attSocket = null

        updateNotificationContent(false)
        sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED).apply {
            setPackage(packageName)
        })

        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter
        if (checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") == PackageManager.PERMISSION_GRANTED){
            bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP) {
                        val connectedDevices = proxy.connectedDevices
                        if (connectedDevices.isNotEmpty()) {
                            MediaController.sendPause()
                        }
                        try {
                            val selectedDevice = device
                            if (selectedDevice != null) {
                                if (Build.VERSION.SDK_INT >= 37) {
                                    selectedDevice.disconnect()
                                } else {
                                    proxy.javaClass
                                        .getMethod("disconnect", BluetoothDevice::class.java)
                                        .invoke(proxy, selectedDevice)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "Bluetooth profile disconnect request failed (${e.javaClass.simpleName})"
                            )
                        }
                    }
                    bluetoothAdapter.closeProfileProxy(profile, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        }
        if (checkSelfPermission("android.permission.MODIFY_PHONE_STATE") == PackageManager.PERMISSION_GRANTED){
            bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        val connectedDevices = proxy.connectedDevices
                        if (connectedDevices.isNotEmpty()) {
                            MediaController.sendPause()
                        }
                    }
                    bluetoothAdapter.closeProfileProxy(profile, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.HEADSET)
        }
        Log.d(TAG, "Disconnected AirPods upon user request")
    }

    val earDetectionNotification = AirPodsNotifications.EarDetection()
    val ancNotification = AirPodsNotifications.ANC()
    val batteryNotification = AirPodsNotifications.BatteryNotification()
    val conversationAwarenessNotification =
        AirPodsNotifications.ConversationalAwarenessNotification()

    @Suppress("unused")
    fun setEarDetection(enabled: Boolean) {
        if (config.earDetectionEnabled != enabled) {
            config.earDetectionEnabled = enabled
            lastWearPosition = null
            sharedPreferences.edit { putBoolean("automatic_ear_detection", enabled) }
        }
    }

    fun getBattery(): List<Battery> {
//        if (!isConnectedLocally && CrossDevice.isAvailable) {
//            batteryNotification.setBattery(CrossDevice.batteryBytes)
//        }
        return batteryNotification.getBattery()
    }

    fun getANC(): Int {
//        if (!isConnectedLocally && CrossDevice.isAvailable) {
//            ancNotification.setStatus(CrossDevice.ancBytes)
//        }
        return ancNotification.status
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        if (device == null) {
            Log.w(TAG, "Skipping audio profile disconnect: no selected AirPods device")
            return
        }
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
        if (checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.A2DP) {
                        bluetoothAdapter.closeProfileProxy(profile, proxy)
                        return
                    }
                    try {
                        val state = proxy.getConnectionState(device)
                        if (!BluetoothProfileConnectPolicy.shouldRequestDisconnect(state)) {
                            Log.d(TAG, "Skipping A2DP disconnect request; profile state=$state")
                            return
                        }
                        val method = proxy.javaClass.getMethod(
                            "setConnectionPolicy", BluetoothDevice::class.java, Int::class.java
                        )
                        Log.d(TAG, "Requesting A2DP disconnect policy")
                        method.invoke(proxy, device, 0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to request A2DP disconnect", e)
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        } else {
            Log.d(TAG, "not disconnecting A2DP, no BLUETOOTH_PRIVILEGED permission")
        }
        if (checkSelfPermission("android.permission.MODIFY_PHONE_STATE") == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.HEADSET) {
                        bluetoothAdapter.closeProfileProxy(profile, proxy)
                        return
                    }
                    try {
                        val state = proxy.getConnectionState(device)
                        if (!BluetoothProfileConnectPolicy.shouldRequestDisconnect(state)) {
                            Log.d(TAG, "Skipping HEADSET disconnect request; profile state=$state")
                            return
                        }
                        val method = proxy.javaClass.getMethod(
                            "setConnectionPolicy", BluetoothDevice::class.java, Int::class.java
                        )
                        Log.d(TAG, "Requesting HEADSET disconnect policy")
                        method.invoke(proxy, device, 0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to request HEADSET disconnect", e)
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.HEADSET)
        } else {
            Log.d(TAG, "not disconnecting HEADSET, no MODIFY_PHONE_STATE permission")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectAudio(context: Context, device: BluetoothDevice?) {
        if (device == null) {
            Log.w(TAG, "Skipping audio profile connect: no selected AirPods device")
            return
        }
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.A2DP) {
                    bluetoothAdapter.closeProfileProxy(profile, proxy)
                    return
                }
                try {
                    val state = proxy.getConnectionState(device)
                    if (!BluetoothProfileConnectPolicy.shouldRequestConnect(state)) {
                        Log.d(TAG, "Skipping A2DP connect request; profile state=$state")
                        return
                    }
                    val hasPrivilegedPermission =
                        context.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") == PackageManager.PERMISSION_GRANTED
                    if (hasPrivilegedPermission) {
                        val policyMethod = proxy.javaClass.getMethod(
                            "setConnectionPolicy",
                            BluetoothDevice::class.java,
                            Int::class.java
                        )
                        Log.d(TAG, "Requesting A2DP connect policy")
                        policyMethod.invoke(proxy, device, 100)
                    }
                    val connectMethod =
                        proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    connectMethod.invoke(proxy, device)
                    Log.d(TAG, "Requested A2DP connection for disconnected AirPods")
                    if (hasPrivilegedPermission && MediaController.pausedWhileTakingOver) {
                        MediaController.sendPlay()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to request A2DP connection", e)
                } finally {
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HEADSET) {
                    bluetoothAdapter.closeProfileProxy(profile, proxy)
                    return
                }
                try {
                    val state = proxy.getConnectionState(device)
                    if (!BluetoothProfileConnectPolicy.shouldRequestConnect(state)) {
                        Log.d(TAG, "Skipping HEADSET connect request; profile state=$state")
                        return
                    }
                    if (checkSelfPermission("android.permission.MODIFY_PHONE_STATE") != PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Skipping HEADSET connect request; privileged phone-state permission unavailable")
                        return
                    }
                    val policyMethod = proxy.javaClass.getMethod(
                        "setConnectionPolicy",
                        BluetoothDevice::class.java,
                        Int::class.java
                    )
                    Log.d(TAG, "Requesting HEADSET connect policy")
                    policyMethod.invoke(proxy, device, 100)
                    val connectMethod =
                        proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    connectMethod.invoke(proxy, device)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to request HEADSET connection", e)
                } finally {
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HEADSET)
    }

    fun setName(name: String) {
        aacpManager.sendRename(name)

        if (config.deviceName != name) {
            config.deviceName = name
            device?.alias = name
            sharedPreferences.edit { putString("name", name) }
        }

        updateNotificationContent(true, name, batteryNotification.getBattery())
        Log.d(TAG, "setName: $name")
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        cancelPendingWearResume()
        pendingLowBatteryWarnings.clear()
        lowBatteryAlertWindow?.dismissImmediately()
        lowBatteryAlertWindow = null
        updateCheckScope.cancel()
        clearPacketLogs()
        Log.d(TAG, "Service stopped is being destroyed for some reason!")

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(externalBroadcastReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(earReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            bleManager.stopScanning()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (ServiceManager.getService() === this) {
            ServiceManager.setService(null)
        }
        if (checkSelfPermission("android.permission.READ_PHONE_STATE") == PackageManager.PERMISSION_GRANTED) {
            telephonyManager.unregisterTelephonyCallback(phoneStateListener)
        }
//        isConnectedLocally = false
//        CrossDevice.isAvailable = true
        super.onDestroy()
    }

    @Volatile var isHeadTrackingActive = false

    fun startHeadTracking() {
        if (isInCall || phoneCallPhase == PhoneCallPhase.ACTIVE || callGestureDecisionRequested) {
            Log.d(TAG, "Refusing to start head tracking after call answer")
            isHeadTrackingActive = false
            gestureDetector?.stopDetection()
            return
        }
        isHeadTrackingActive = true
        val useAlternatePackets =
            sharedPreferences.getBoolean("use_alternate_head_tracking_packets", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && aacpManager.getControlCommandStatus(
                AACPManager.Companion.ControlCommandIdentifiers.OWNS_CONNECTION
            )?.value?.get(0)?.toInt() != 1
        ) {
            takeOver("call", startHeadTrackingAgain = true)
            Log.d(TAG, "Taking over for head tracking")
        } else {
            Log.w(TAG, "Will not be taking over for head tracking, might not work.")
        }
        if (useAlternatePackets) {
            aacpManager.sendDataPacket(aacpManager.createAlternateStartHeadTrackingPacket())
        } else {
            aacpManager.sendStartHeadTracking()
        }
        HeadTracking.reset()
    }

    fun stopHeadTracking() {
        val useAlternatePackets =
            sharedPreferences.getBoolean("use_alternate_head_tracking_packets", true)
        if (useAlternatePackets) {
            aacpManager.sendDataPacket(aacpManager.createAlternateStopHeadTrackingPacket())
        } else {
            aacpManager.sendStopHeadTracking()
        }
        isHeadTrackingActive = false
        gestureDetector?.stopDetection()
    }

    @SuppressLint("MissingPermission")
    fun reconnectFromSavedMac() {
        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter
        device = bluetoothAdapter.bondedDevices.find {
            it.address == macAddress
        }
        if (device != null) {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Reconnecting to the saved paired AirPods device")
                connectToSocket(bluetoothAdapter, device!!, manual = true)
                if (!isAndroidAudioProfileConnected) {
                    connectAudio(this@AirPodsService, device!!)
                } else {
                    Log.d(TAG, "Audio profiles already connected; retrying only the AirPods control socket")
                }
            }
        }
    }
}

private fun Int.dpToPx(): Int {
    val density = Resources.getSystem().displayMetrics.density
    return (this * density).toInt()
}

fun getNextMode(currentMode: Int, configByte: Int, offmodeEnabled: Boolean): Int {
    val enabledModes = buildList {
        if ((configByte and 0x01) != 0 && offmodeEnabled) add(1)
        if ((configByte and 0x04) != 0) add(3)
        if ((configByte and 0x08) != 0) add(4)
        if ((configByte and 0x02) != 0) add(2)
    }
    Log.d(TAG, "currentMode: $currentMode, config: ${configByte.toString(2)}")

    if (enabledModes.isEmpty()) return currentMode

    val currentIndex = enabledModes.indexOf(currentMode)
    val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % enabledModes.size

    return enabledModes[nextIndex]
}
