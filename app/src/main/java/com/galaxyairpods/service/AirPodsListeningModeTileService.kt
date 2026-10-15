package com.galaxyairpods.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.galaxyairpods.data.bluetooth.AirPodsScannerHub
import com.galaxyairpods.data.bluetooth.AirPodsScannerLease
import com.galaxyairpods.data.bluetooth.BluetoothAirPodsEvent
import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.ListeningMode
import com.galaxyairpods.domain.model.AirPodsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Optional Quick Settings entry point for the same listening-mode command
 * used by Settings. It never guesses a BLE-only device is Android-connected;
 * the shared scanner performs the AAP READY gate before writing.
 */
class AirPodsListeningModeTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lease: AirPodsScannerLease? = null
    private var controlJob: Job? = null
    private var currentMode: ListeningMode? = null
    private var latestBluetoothEvent: BluetoothAirPodsEvent? = null
    private val routeGate by lazy { AndroidAudioRouteGate(this) }

    override fun onStartListening() {
        super.onStartListening()
        if (lease == null) {
            lease = AirPodsScannerHub.acquire(applicationContext).also { it.start() }
        }
        controlJob?.cancel()
        controlJob = scope.launch {
            launch {
                lease?.scanner?.bluetoothEvents?.collect { event ->
                    if (latestBluetoothEvent?.deviceProfileId != event.deviceProfileId ||
                        !event.aapReady ||
                        event.connectionState != com.galaxyairpods.domain.model.AirPodsConnectionState.ANDROID_CONNECTED
                    ) {
                        currentMode = null
                    }
                    latestBluetoothEvent = event
                    updateTile()
                }
            }
            lease?.scanner?.classicControlEvents?.collect { event ->
                if (event.identifier == AapControlId.LISTENING_MODE) {
                    currentMode = event.value.firstOrNull()?.toInt()?.and(0xFF)
                        ?.let(ListeningMode::fromWireValue)
                    updateTile()
                }
            }
        }
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val event = latestBluetoothEvent
        val scanner = lease?.scanner
        if (event == null || scanner?.isAapControlReady(event.deviceProfileId) != true ||
            !routeGate.check(event.toAirPodsState()).activeForTarget
        ) {
            updateTile()
            return
        }
        scanner.cycleListeningMode(event.deviceProfileId)
        updateTile()
    }

    override fun onStopListening() {
        controlJob?.cancel()
        controlJob = null
        lease?.close()
        lease = null
        super.onStopListening()
    }

    override fun onDestroy() {
        controlJob?.cancel()
        scope.cancel()
        lease?.close()
        lease = null
        super.onDestroy()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val event = latestBluetoothEvent
        val ready = event != null &&
            lease?.scanner?.isAapControlReady(event.deviceProfileId) == true &&
            routeGate.check(event.toAirPodsState()).activeForTarget
        tile.state = when {
            !ready -> Tile.STATE_UNAVAILABLE
            currentMode != null -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = currentMode?.label ?: "소음 제어"
        tile.subtitle = if (ready) "탭하여 모드 변경" else "Galaxy 오디오 연결 필요"
        tile.updateTile()
    }

    private fun BluetoothAirPodsEvent.toAirPodsState(): AirPodsState = AirPodsState(
        deviceId = deviceId,
        deviceProfileId = deviceProfileId,
        model = model,
        connected = connectionState == com.galaxyairpods.domain.model.AirPodsConnectionState.ANDROID_CONNECTED,
        detected = connectionState != com.galaxyairpods.domain.model.AirPodsConnectionState.UNKNOWN,
        connectionState = connectionState,
        deviceName = deviceName,
        a2dpConnected = a2dpConnected,
        headsetConnected = headsetConnected,
        aapReady = aapReady,
    )
}
