package com.galaxyairpods.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyairpods.data.BleAirPodsRepository
import com.galaxyairpods.data.bluetooth.AirPodsBleScanner
import com.galaxyairpods.data.bluetooth.AirPodsScannerHub
import com.galaxyairpods.data.persistence.AirPodsDataStore
import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.AirPodsControlState
import com.galaxyairpods.domain.control.CallControlConfiguration
import com.galaxyairpods.domain.control.ControlCapabilityTier
import com.galaxyairpods.domain.control.ListeningMode
import com.galaxyairpods.domain.control.MicrophoneMode
import com.galaxyairpods.domain.control.StemPressActions
import com.galaxyairpods.domain.control.toAapValue
import com.galaxyairpods.domain.model.AirPodsModel
import com.galaxyairpods.domain.model.AirPodsConnectionState
import com.galaxyairpods.domain.model.AirPodsState
import com.galaxyairpods.domain.model.mergeKnownValuesFrom
import com.galaxyairpods.domain.motion.MotionLabSettings
import com.galaxyairpods.permissions.PermissionManager
import com.galaxyairpods.service.AirPodsMonitorService
import com.galaxyairpods.service.AirPodsOverlayService
import com.galaxyairpods.service.AndroidAudioRouteGate
import com.galaxyairpods.service.EXTRA_CASE_OPEN
import com.galaxyairpods.service.EXTRA_LEFT_IN_CASE
import com.galaxyairpods.service.EXTRA_RIGHT_IN_CASE
import com.galaxyairpods.update.UpdateManager
import com.galaxyairpods.update.UpdateInfo
import com.galaxyairpods.update.UpdateState
import com.galaxyairpods.widget.AirPodsWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = AirPodsDataStore(application)
    private val liveRepository = BleAirPodsRepository(dataStore)
    private val audioRouteGate = AndroidAudioRouteGate(application)
    private val updateManager = UpdateManager.shared(application)

    private val scannerLease = AirPodsScannerHub.acquire(application)
    val scanner: AirPodsBleScanner = scannerLease.scanner
    val scanStatus: StateFlow<String> = scanner.status
    val motionSettings: MotionLabSettings = MotionLabSettings.Default

    val airPodsState: StateFlow<AirPodsState> = combine(
        liveRepository.state,
        dataStore.latestState,
        dataStore.modelOverride,
        dataStore.wearDetectionEnabled,
    ) { live, stored, override, wearEnabled ->
        val source = if (live.deviceId != null) {
            live.mergeKnownValuesFrom(stored)
        } else {
            stored ?: AirPodsState.empty()
        }
        source.withResolvedConfidence()
            .copy(model = override ?: source.model)
            .let { if (wearEnabled) it else it.withoutWearDetection() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AirPodsState.empty(),
    )

    private val _aapControlState = MutableStateFlow(AirPodsControlState())
    /**
     * AAP controls are transient. The tier is derived from the current live
     * connection so an old response cannot make a new process look connected.
     */
    val aapControlState: StateFlow<AirPodsControlState> = combine(
        _aapControlState,
        airPodsState,
    ) { controls, state ->
        val androidRouteActive = audioRouteGate.check(state).activeForTarget
        val controlReady = state.aapReady && androidRouteActive
        val tier = when {
            controlReady -> ControlCapabilityTier.AAP_L2CAP_EXACT
            state.connectionState == AirPodsConnectionState.ANDROID_CONNECTED ->
                ControlCapabilityTier.AAP_PENDING
            state.detected -> ControlCapabilityTier.BLE_ONLY
            else -> ControlCapabilityTier.BLE_ONLY
        }
        controls.copy(
            tier = tier,
            aapReady = controlReady && controls.lastError == null,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AirPodsControlState(),
    )

    val hasPersistedState: StateFlow<Boolean> = dataStore.latestState
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoPopup: StateFlow<Boolean> = dataStore.autoPopup.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )
    val showOnCaseOpen: StateFlow<Boolean> = dataStore.showOnCaseOpen.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )
    val popupDuration: StateFlow<Int> = dataStore.popupDuration.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        5,
    )
    val backgroundDetection: StateFlow<Boolean> = dataStore.backgroundDetection.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )
    val wearDetectionEnabled: StateFlow<Boolean> = dataStore.wearDetectionEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )
    val automaticMediaControlEnabled: StateFlow<Boolean> = dataStore.automaticMediaControlEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )
    val headGesturesEnabled: StateFlow<Boolean> = dataStore.headGesturesEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )
    val stemPressActions: StateFlow<StemPressActions> = dataStore.stemPressActions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StemPressActions(),
    )
    val modelOverride: StateFlow<AirPodsModel?> = dataStore.modelOverride.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val updateState: StateFlow<UpdateState> = updateManager.state

    private val _reducedMotion = MutableStateFlow(false)
    val reducedMotion: StateFlow<Boolean> = _reducedMotion

    init {
        startAutomaticUpdateChecks()
        viewModelScope.launch {
            scanner.validatedPackets.collect { event ->
                liveRepository.applyParsedPacket(
                    deviceId = event.deviceId,
                    packet = event.packet,
                    seenAt = event.seenAt,
                    wearDetectionEnabled = wearDetectionEnabled.value,
                    deviceProfileId = event.deviceProfileId,
                    capturedAtElapsedMs = event.capturedAtElapsedMs,
                )
                val current = liveRepository.state.value
                val displayState = displayState(current)

                AirPodsWidget.updateState(
                    context = getApplication(),
                    left = current.leftBattery,
                    right = current.rightBattery,
                    caseBattery = current.caseBattery,
                    modelLabel = displayState.model.label,
                )
            }
        }
        viewModelScope.launch {
            scanner.bluetoothEvents.collect { event ->
                liveRepository.applyBluetoothConnection(event)
                if (event.connectionState != AirPodsConnectionState.ANDROID_CONNECTED) {
                    // Do not leave feature values looking live after an iPad,
                    // nearby-only, or disconnected transition.
                    _aapControlState.value = AirPodsControlState()
                } else if (!event.aapReady) {
                    _aapControlState.update {
                        it.copy(aapReady = false, lastError = null)
                    }
                } else {
                    // A previous tap may have recorded an unavailable-session
                    // error while AAP was still handshaking. A fresh READY
                    // event makes the controls usable again.
                    _aapControlState.update {
                        it.copy(aapReady = true, lastError = null)
                    }
                }
                val current = liveRepository.state.value
                val displayState = displayState(current)

                AirPodsWidget.updateState(
                    context = getApplication(),
                    left = current.leftBattery,
                    right = current.rightBattery,
                    caseBattery = current.caseBattery,
                    modelLabel = displayState.model.label,
                )
            }
        }
        viewModelScope.launch {
            scanner.classicBatteryEvents.collect { event ->
                liveRepository.applyClassicAapBattery(event)
                val current = liveRepository.state.value
                val displayState = displayState(current)

                AirPodsWidget.updateState(
                    context = getApplication(),
                    left = current.leftBattery,
                    right = current.rightBattery,
                    caseBattery = current.caseBattery,
                    modelLabel = displayState.model.label,
                )
            }
        }
        viewModelScope.launch {
            scanner.classicWearEvents.collect { event ->
                if (!wearDetectionEnabled.value) return@collect
                liveRepository.applyClassicAapWear(event)
            }
        }
        viewModelScope.launch {
            scanner.classicControlEvents.collect { event ->
                _aapControlState.update { current ->
                    current.withValue(event.identifier, event.value, event.seenAt)
                }
            }
        }
        viewModelScope.launch {
            combine(
                dataStore.backgroundDetection,
                dataStore.wearDetectionEnabled,
                dataStore.automaticMediaControlEnabled,
                dataStore.headGesturesEnabled,
                dataStore.stemPressActions,
            ) { backgroundEnabled, wearEnabled, mediaControlEnabled, headGesturesEnabled, actions ->
                backgroundEnabled || (wearEnabled && mediaControlEnabled) ||
                    headGesturesEnabled || actions.interceptLongPress
            }.collect { monitorRequired ->
                if (monitorRequired) startBackgroundServiceIfReady() else stopBackgroundService()
            }
        }
    }

    fun startScanning() {
        scannerLease.start()
        if (backgroundDetection.value ||
            (wearDetectionEnabled.value && automaticMediaControlEnabled.value) ||
            headGesturesEnabled.value || stemPressActions.value.interceptLongPress
        ) {
            startBackgroundServiceIfReady()
        }
    }

    fun setAutoPopup(enabled: Boolean) {
        viewModelScope.launch { dataStore.setAutoPopup(enabled) }
    }

    fun setShowOnCaseOpen(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShowOnCaseOpen(enabled) }
    }

    fun setPopupDuration(seconds: Int) {
        viewModelScope.launch { dataStore.setPopupDuration(seconds) }
    }

    fun setBackgroundDetection(enabled: Boolean) {
        viewModelScope.launch { dataStore.setBackgroundDetection(enabled) }
    }

    fun setWearDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setWearDetectionEnabled(enabled) }
    }

    fun setAutomaticMediaControlEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setAutomaticMediaControlEnabled(enabled) }
    }

    fun setHeadGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setHeadGesturesEnabled(enabled) }
    }

    fun setStemPressActions(actions: StemPressActions) {
        // The foreground monitor owns AAP interception so it can verify the
        // active Android audio route before changing firmware behavior.
        viewModelScope.launch { dataStore.setStemPressActions(actions) }
    }

    fun setListeningMode(mode: ListeningMode) {
        sendAapControl(
            AapControlId.LISTENING_MODE,
            byteArrayOf(mode.wireValue.toByte()),
        )
    }

    fun setListeningModeConfig(mask: Int) {
        sendAapControl(
            AapControlId.LISTENING_MODE_CONFIGS,
            byteArrayOf(mask.coerceIn(0, 0xFF).toByte()),
        )
    }

    fun setAapBoolean(id: AapControlId, enabled: Boolean) {
        sendAapControl(id, enabled.toAapValue())
    }

    fun setMicrophoneMode(mode: MicrophoneMode) {
        sendAapControl(
            AapControlId.MIC_MODE,
            byteArrayOf(mode.wireValue.toByte()),
        )
    }

    fun setAapByte(id: AapControlId, value: Int) {
        sendAapControl(id, byteArrayOf(value.coerceIn(0, 255).toByte()))
    }

    fun setChimeVolume(value: Int) {
        sendAapControl(
            AapControlId.CHIME_VOLUME,
            byteArrayOf(value.coerceIn(0, 100).toByte(), 0x50.toByte()),
        )
    }

    fun setCallControlConfiguration(configuration: CallControlConfiguration) {
        sendAapControl(
            AapControlId.CALL_MANAGEMENT_CONFIG,
            configuration.toWireValue(),
        )
    }

    fun renameConnectedAirPods(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) {
            _aapControlState.update { it.copy(lastError = "이름을 입력하세요") }
            return
        }
        val state = airPodsState.value
        if (!isAapRouteActive(state)) {
            _aapControlState.update { it.copy(lastError = "AirPods가 Galaxy 오디오 경로에 연결된 뒤 사용할 수 있습니다") }
            return
        }
        scanner.sendAapRename(normalized, state.deviceProfileId)
    }

    private fun sendAapControl(id: AapControlId, value: ByteArray) {
        val state = airPodsState.value
        if (!isAapRouteActive(state)) {
            _aapControlState.update {
                it.copy(lastError = "AirPods가 Galaxy 오디오 경로에 연결된 뒤 사용할 수 있습니다")
            }
            return
        }
        _aapControlState.update { it.copy(lastError = null) }
        scanner.sendAapControlCommand(id, value.copyOf(), state.deviceProfileId)
    }

    private fun isAapRouteActive(state: AirPodsState): Boolean =
        state.deviceProfileId != null &&
            state.aapReady &&
            audioRouteGate.check(state).activeForTarget &&
            scanner.isAapControlReady(state.deviceProfileId)

    fun setModelOverride(model: AirPodsModel?) {
        viewModelScope.launch { dataStore.setModelOverride(model) }
    }

    fun setReducedMotion(enabled: Boolean) {
        _reducedMotion.value = enabled
    }

    fun checkForUpdates() {
        viewModelScope.launch { updateManager.check() }
    }

    fun startUpdate(info: UpdateInfo) {
        viewModelScope.launch { updateManager.downloadAndInstall(info) }
    }

    fun installReadyUpdate(ready: UpdateState.Ready) {
        updateManager.installReady(ready.file)
    }

    fun testOverlay() {
        val context = getApplication<Application>()
        if (Settings.canDrawOverlays(context)) {
            // The test action must exercise the same open-case timeline as a
            // real lid event. The persisted snapshot is often closed because
            // the last battery sample arrived after the lid was closed; using
            // that snapshot here made the test show only a static closed case.
            context.startService(
                Intent(context, AirPodsOverlayService::class.java).apply {
                    putExtra(EXTRA_CASE_OPEN, true)
                    putExtra(EXTRA_LEFT_IN_CASE, false)
                    putExtra(EXTRA_RIGHT_IN_CASE, false)
                },
            )
        }
    }

    private fun displayState(state: AirPodsState): AirPodsState {
        val modelled = state.copy(model = modelOverride.value ?: state.model)
        return if (wearDetectionEnabled.value) modelled else modelled.withoutWearDetection()
    }

    private fun startBackgroundServiceIfReady() {
        val context = getApplication<Application>()
        if (!PermissionManager.allGranted(context, PermissionManager.bluetoothPermissions())) return
        val intent = Intent(context, AirPodsMonitorService::class.java)
        runCatching {
            context.startForegroundService(intent)
        }
    }

    private fun stopBackgroundService() {
        getApplication<Application>().stopService(Intent(getApplication(), AirPodsMonitorService::class.java))
    }

    private fun startAutomaticUpdateChecks() {
        viewModelScope.launch {
            while (isActive) {
                updateManager.check(automatic = true)
                delay(AUTO_UPDATE_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val AUTO_UPDATE_INTERVAL_MS = 30 * 60 * 1000L
    }

    override fun onCleared() {
        scannerLease.close()
        super.onCleared()
    }
}
