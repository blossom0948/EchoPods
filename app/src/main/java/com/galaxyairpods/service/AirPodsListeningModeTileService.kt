package com.galaxyairpods.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.galaxyairpods.data.bluetooth.AirPodsScannerHub
import com.galaxyairpods.data.bluetooth.AirPodsScannerLease
import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.ListeningMode
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

    override fun onStartListening() {
        super.onStartListening()
        if (lease == null) {
            lease = AirPodsScannerHub.acquire(applicationContext).also { it.start() }
        }
        controlJob?.cancel()
        controlJob = scope.launch {
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
        val next = nextMode(currentMode)
        currentMode = next
        lease?.scanner?.sendAapControlCommand(
            AapControlId.LISTENING_MODE,
            byteArrayOf(next.wireValue.toByte()),
        )
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
        val ready = lease?.scanner?.isAapControlReady() == true
        tile.state = when {
            !ready -> Tile.STATE_UNAVAILABLE
            currentMode != null -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = currentMode?.label ?: "소음 제어"
        tile.subtitle = if (ready) "탭하여 모드 변경" else "AAP 연결 필요"
        tile.updateTile()
    }

    private fun nextMode(current: ListeningMode?): ListeningMode {
        val modes = ListeningMode.entries
        val index = current?.let(modes::indexOf) ?: -1
        return modes[(index + 1) % modes.size]
    }
}
