package com.galaxyairpods.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.galaxyairpods.data.bluetooth.AirPodsBleScanner
import com.galaxyairpods.data.bluetooth.BluetoothAirPodsEvent
import com.galaxyairpods.domain.control.HeadGesture
import com.galaxyairpods.domain.control.HeadGestureRecognizer
import com.galaxyairpods.domain.model.AirPodsConnectionState
import com.galaxyairpods.domain.model.AirPodsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.os.SystemClock

/** Handles call gestures only while the user opted in and the AAP stream is live. */
internal class HeadGestureCallController(
    private val context: Context,
    private val scanner: AirPodsBleScanner,
    private val airPodsState: StateFlow<AirPodsState>,
    enabledFlow: Flow<Boolean>,
    scope: CoroutineScope,
) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val recognizer = HeadGestureRecognizer()
    private val routeGate = AndroidAudioRouteGate(context)
    private val jobs = mutableListOf<Job>()

    @Volatile private var enabled = false
    @Volatile private var callState = TelephonyManager.CALL_STATE_IDLE
    @Volatile private var activeProfileId: String? = null
    @Volatile private var activeRouteState: AirPodsState = airPodsState.value
    @Volatile private var trackingProfileId: String? = null
    @Volatile private var trackingActive = false
    @Volatile private var registered = false
    private var callback31: TelephonyCallback? = null
    private var callback30: PhoneStateListener? = null

    init {
        jobs += scope.launch {
            enabledFlow.collect { isEnabled ->
                enabled = isEnabled
                if (isEnabled) registerForCalls() else unregisterFromCalls()
            }
        }
        jobs += scope.launch {
            scanner.bluetoothEvents.collect { event ->
                if (trackingProfileId != null && trackingProfileId != event.deviceProfileId) {
                    stopTracking()
                }
                activeRouteState = event.toRouteState(airPodsState.value)
                val routeReady = routeGate.check(activeRouteState).activeForTarget
                activeProfileId = event.deviceProfileId.takeIf { event.aapReady && routeReady }
                if (!event.aapReady || !routeReady) {
                    stopTracking()
                } else if (enabled && callState == TelephonyManager.CALL_STATE_RINGING) {
                    startTrackingIfReady()
                }
            }
        }
        jobs += scope.launch {
            airPodsState.collect { state ->
                if (state.deviceProfileId == activeProfileId) {
                    activeRouteState = state
                    if (!routeGate.check(state).activeForTarget) {
                        activeProfileId = null
                        stopTracking()
                    } else if (enabled && callState == TelephonyManager.CALL_STATE_RINGING) {
                        startTrackingIfReady()
                    }
                }
            }
        }
        jobs += scope.launch {
            scanner.classicHeadTrackingEvents.collect { event ->
                if (!enabled || !trackingActive || callState != TelephonyManager.CALL_STATE_RINGING) {
                    return@collect
                }
                if (event.deviceProfileId != trackingProfileId) return@collect
                val now = SystemClock.elapsedRealtime()
                val gesture = recognizer.accept(
                    horizontal = event.horizontal,
                    vertical = event.vertical,
                    capturedAtMs = event.seenAt,
                    nowMs = now,
                ) ?: return@collect
                executeCallGesture(gesture)
            }
        }
    }

    private fun registerForCalls() {
        if (registered) return
        if (!hasCallPermissions()) {
            Log.w(TAG, "head_gesture disabled reason=PHONE_PERMISSION_MISSING")
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                }
                callback31 = callback
                telephony?.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val callback = object : PhoneStateListener() {
                    @Deprecated("Deprecated by Android; retained for API 29 and 30")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
                }
                callback30 = callback
                @Suppress("DEPRECATION")
                telephony?.listen(callback, PhoneStateListener.LISTEN_CALL_STATE)
            }
            registered = true
            telephony?.callState?.let(::onCallState)
            Log.i(TAG, "head_gesture_call_state_listener registered")
        }.onFailure { error ->
            Log.w(TAG, "head_gesture listener unavailable", error)
        }
    }

    private fun unregisterFromCalls() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callback31?.let { telephony?.unregisterTelephonyCallback(it) }
            }
            callback31 = null
            callback30?.let {
                @Suppress("DEPRECATION")
                telephony?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
            callback30 = null
        }
        registered = false
        callState = TelephonyManager.CALL_STATE_IDLE
        stopTracking()
    }

    private fun onCallState(state: Int) {
        callState = state
        recognizer.reset()
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            startTrackingIfReady()
        } else {
            stopTracking()
        }
    }

    private fun startTrackingIfReady() {
        if (!enabled || callState != TelephonyManager.CALL_STATE_RINGING ||
            activeProfileId == null || !routeGate.check(activeRouteState).activeForTarget ||
            !scanner.isAapControlReady(activeProfileId) || trackingActive
        ) return
        trackingActive = true
        trackingProfileId = activeProfileId
        recognizer.reset()
        scanner.startHeadTracking(activeProfileId!!)
        Log.i(TAG, "head_tracking started reason=INCOMING_CALL")
    }

    private fun stopTracking() {
        if (!trackingActive) return
        trackingActive = false
        recognizer.reset()
        trackingProfileId?.let(scanner::stopHeadTracking)
        trackingProfileId = null
        Log.i(TAG, "head_tracking stopped")
    }

    @SuppressLint("MissingPermission")
    private fun executeCallGesture(gesture: HeadGesture) {
        if (!hasCallPermissions() || callState != TelephonyManager.CALL_STATE_RINGING ||
            !routeGate.check(activeRouteState).activeForTarget ||
            !scanner.isAapControlReady(trackingProfileId)
        ) {
            Log.i(TAG, "head_gesture ignored reason=CALL_OR_ROUTE_CHANGED")
            stopTracking()
            return
        }
        val request = runCatching {
            when (gesture) {
                HeadGesture.NOD -> {
                    telecom?.acceptRingingCall()
                    true
                }
                HeadGesture.SHAKE -> telecom?.endCall() == true
            }
        }.onFailure { error ->
            Log.w(TAG, "head_gesture call action failed gesture=$gesture", error)
        }.getOrDefault(false)
        Log.i(TAG, "head_gesture gesture=$gesture call_request=$request")
        stopTracking()
    }

    private fun hasCallPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED

    private fun BluetoothAirPodsEvent.toRouteState(current: AirPodsState): AirPodsState {
        val base = if (current.deviceProfileId == deviceProfileId) current else AirPodsState.empty()
        val androidConnected = connectionState == AirPodsConnectionState.ANDROID_CONNECTED
        val detectedNearby = connectionState != AirPodsConnectionState.DISCONNECTED &&
            connectionState != AirPodsConnectionState.UNKNOWN
        return base.copy(
            deviceId = deviceId,
            deviceProfileId = deviceProfileId,
            model = model,
            deviceName = deviceName,
            connected = androidConnected,
            detected = detectedNearby,
            connectionState = connectionState,
            a2dpConnected = a2dpConnected,
            headsetConnected = headsetConnected,
            aapReady = aapReady,
        )
    }

    fun close() {
        unregisterFromCalls()
        jobs.forEach(Job::cancel)
        jobs.clear()
    }

    private companion object {
        const val TAG = "AirPodsHeadGestures"
    }
}
