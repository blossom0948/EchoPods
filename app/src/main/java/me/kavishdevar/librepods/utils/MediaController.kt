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

package me.kavishdevar.librepods.utils

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.services.MediaSessionResolver
import me.kavishdevar.librepods.services.ServiceManager
import kotlin.io.encoding.ExperimentalEncodingApi

object MediaController {
    private var initialVolume: Int? = null
    private lateinit var audioManager: AudioManager
    @Volatile var iPausedTheMedia = false
    @Volatile var userPlayedTheMedia = false
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    var pausedWhileTakingOver = false
    var pausedForOtherDevice = false

    private var lastSelfActionAt: Long = 0L
    private const val SELF_ACTION_IGNORE_MS = 800L
    private const val PLAYBACK_DEBOUNCE_MS = 300L
    private var lastPlaybackCallbackAt: Long = 0L
    private var lastKnownIsMusicActive: Boolean? = null

    private const val PAUSED_FOR_OTHER_DEVICE_CLEAR_MS = 500L
    private val clearPausedForOtherDeviceRunnable = Runnable {
        pausedForOtherDevice = false
        Log.d("MediaController", "Cleared pausedForOtherDevice after timeout, resuming normal playback monitoring")
    }

    private var relativeVolume: Boolean = false
    private var conversationalAwarenessVolume: Int = 2
    private var conversationalAwarenessPauseMusic: Boolean = false

    var recentlyLostOwnership: Boolean = false

    fun cancelAutoResumeAfterExternalMediaChange() {
        iPausedTheMedia = false
        userPlayedTheMedia = false
        Log.i("MediaController", "Auto-resume cancelled because the paused media session changed or was manually resumed/stopped")
    }

    private var lastPlayWithReplay: Boolean = false
    private var lastPlayTime: Long = 0L
    @Volatile private var lastAirPodsAudioRouteAtMs: Long = 0L

    fun initialize(audioManager: AudioManager, sharedPreferences: SharedPreferences) {
        if (this::audioManager.isInitialized) {
            return
        }
        this.audioManager = audioManager
        this.sharedPreferences = sharedPreferences
        Log.d("MediaController", "Initializing MediaController")
        relativeVolume = sharedPreferences.getBoolean("relative_conversational_awareness_volume", false)
        conversationalAwarenessVolume = sharedPreferences.getInt("conversational_awareness_volume", (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 0.4).toInt())
        conversationalAwarenessPauseMusic = sharedPreferences.getBoolean("conversational_awareness_pause_music", false)

        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "relative_conversational_awareness_volume" -> {
                    relativeVolume = sharedPreferences.getBoolean("relative_conversational_awareness_volume", false)
                }
                "conversational_awareness_volume" -> {
                    conversationalAwarenessVolume = sharedPreferences.getInt("conversational_awareness_volume", (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.4).toInt())
                }
                "conversational_awareness_pause_music" -> {
                    conversationalAwarenessPauseMusic = sharedPreferences.getBoolean("conversational_awareness_pause_music", false)
                }
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        audioManager.registerAudioPlaybackCallback(cb, null)
    }

    val cb = object : AudioManager.AudioPlaybackCallback() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)
            val now = SystemClock.uptimeMillis()
            val isActive = audioManager.isMusicActive
            Log.d("MediaController", "Playback config changed, iPausedTheMedia: $iPausedTheMedia, isActive: $isActive, pausedForOtherDevice: $pausedForOtherDevice, lastKnownIsMusicActive: $lastKnownIsMusicActive")

            if (!isActive && lastPlayWithReplay && now - lastPlayTime < 2500L) {
                Log.d("MediaController", "Music paused shortly after play with replay; retrying play")
                lastPlayWithReplay = false
                sendPlay()
                lastKnownIsMusicActive = true
                return
            }

            if (now - lastPlaybackCallbackAt < PLAYBACK_DEBOUNCE_MS) {
                Log.d("MediaController", "Ignoring playback callback due to debounce (${now - lastPlaybackCallbackAt}ms)")
                lastPlaybackCallbackAt = now
                return
            }
            lastPlaybackCallbackAt = now

            if (now - lastSelfActionAt < SELF_ACTION_IGNORE_MS) {
                Log.d("MediaController", "Ignoring playback callback because it's likely caused by our own action (${now - lastSelfActionAt}ms since last self-action)")
                lastKnownIsMusicActive = isActive
                return
            }

            if (iPausedTheMedia && isActive) {
                Log.i("MediaController", "External media playback resumed while auto-paused; cancelling wear auto-resume token")
                cancelAutoResumeAfterExternalMediaChange()
            }

            Log.d("MediaController", "Configs received: ${configs?.size ?: 0} configurations")
            val currentActiveContentTypes = configs?.flatMap { config ->
                Log.d("MediaController", "Processing config: ${config}, audioAttributes: ${config.audioAttributes}")
                config.audioAttributes?.let { attrs ->
                    val contentType = attrs.contentType
                    Log.d("MediaController", "Config content type: $contentType")
                    listOf(contentType)
                } ?: run {
                    Log.d("MediaController", "Config has no audioAttributes")
                    emptyList()
                }
            }?.toSet() ?: emptySet()

            Log.d("MediaController", "Current active content types: $currentActiveContentTypes")

            val hasNewMusicOrMovie = currentActiveContentTypes.any { contentType ->
                contentType == android.media.AudioAttributes.CONTENT_TYPE_MUSIC ||
                contentType == android.media.AudioAttributes.CONTENT_TYPE_MOVIE
            }

            Log.d("MediaController", "Has new music or movie: $hasNewMusicOrMovie")

            if (pausedForOtherDevice) {
                handler.removeCallbacks(clearPausedForOtherDeviceRunnable)
                handler.postDelayed(clearPausedForOtherDeviceRunnable, PAUSED_FOR_OTHER_DEVICE_CLEAR_MS)

                if (isActive) {
                    Log.d("MediaController", "Detected play while pausedForOtherDevice; attempting to take over")
                    if (!recentlyLostOwnership && hasNewMusicOrMovie) {
                        pausedForOtherDevice = false
                        userPlayedTheMedia = true
                        if (!pausedWhileTakingOver) {
                            ServiceManager.getService()?.takeOver("music")
                        }
                    } else {
                        Log.d("MediaController", "Skipping take-over due to recent ownership loss or no new music/movie")
                    }
                } else {
                    Log.d("MediaController", "Still not active while pausedForOtherDevice; will clear state after timeout")
                }

                lastKnownIsMusicActive = isActive
                return
            }

            if (configs != null && !iPausedTheMedia) {
                val localMac = ServiceManager.getService()?.localMac ?: return
                if (localMac == "") return
                ServiceManager.getService()?.aacpManager?.sendMediaInformataion(
                    localMac,
                    isActive
                )
                Log.d("MediaController", "User changed media state themselves; will wait for ear detection pause before auto-play")
                handler.postDelayed({
                    userPlayedTheMedia = audioManager.isMusicActive
                    if (audioManager.isMusicActive) {
                        pausedForOtherDevice = false
                    }
                }, 7)
            }

            Log.d("MediaController", "pausedWhileTakingOver: $pausedWhileTakingOver")
            if (!pausedWhileTakingOver && isActive && hasNewMusicOrMovie) {
                if (lastKnownIsMusicActive != true) {
                    if (!recentlyLostOwnership) {
                        Log.d("MediaController", "Music/movie is active and not pausedWhileTakingOver; requesting takeOver")
                        ServiceManager.getService()?.takeOver("music")
                    } else {
                        Log.d("MediaController", "Skipping take-over due to recent ownership loss")
                    }
                }
            }

            lastKnownIsMusicActive = hasNewMusicOrMovie && isActive
        }
    }

    @Synchronized
    fun getMusicActive(): Boolean {
        return audioManager.isMusicActive || MediaSessionResolver.hasPlayingSession()
    }

    @Synchronized
    fun isAirPodsAudioRouteActive(logResult: Boolean = true): Boolean {
        if (!this::audioManager.isInitialized) return false
        val service = ServiceManager.getService() ?: return false
        val pairedDevice = service.device ?: return false
        val targetAddress = runCatching { pairedDevice.address }.getOrNull().orEmpty()
        val canReadBluetoothName = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                service,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        val targetName = if (canReadBluetoothName) {
            runCatching { pairedDevice.name }.getOrNull().orEmpty()
        } else {
            ""
        }
        if (targetAddress.isBlank() && targetName.isBlank()) return false

        val bluetoothOutputTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
        val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
            .getOrElse { return false }
        val matched = outputs.any { output ->
            if (output.type !in bluetoothOutputTypes) return@any false
            val outputAddress = output.address.orEmpty()
            val outputName = output.productName?.toString().orEmpty()
            (targetAddress.isNotBlank() && outputAddress.equals(targetAddress, ignoreCase = true)) ||
                (targetName.isNotBlank() && outputName.equals(targetName, ignoreCase = true))
        }
        if (matched) lastAirPodsAudioRouteAtMs = SystemClock.elapsedRealtime()
        if (logResult) {
            Log.d(
                "MediaController",
                "AirPods audio route active=$matched; bluetoothOutputs=${outputs.count { it.type in bluetoothOutputTypes }}"
            )
        }
        return matched
    }

    @Synchronized
    fun wasAirPodsAudioRouteActiveRecently(maxAgeMs: Long = 8_000L): Boolean {
        if (isAirPodsAudioRouteActive()) return true
        val lastActiveAt = lastAirPodsAudioRouteAtMs
        return lastActiveAt > 0L && SystemClock.elapsedRealtime() - lastActiveAt <= maxAgeMs
    }

    @Synchronized
    fun sendPlayPause() {
        if (audioManager.isMusicActive) {
            Log.d("MediaController", "Sending pause because music is active")
            sendPause()
        } else {
            Log.d("MediaController", "Sending play because music is not active")
            sendPlay()
        }
    }

    @Synchronized
    fun sendPreviousTrack() {
        Log.d("MediaController", "Sending previous track")
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        lastSelfActionAt = SystemClock.uptimeMillis()
    }

    @Synchronized
    fun sendNextTrack() {
        Log.d("MediaController", "Sending next track")
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        lastSelfActionAt = SystemClock.uptimeMillis()
    }

    @Synchronized
    fun sendPause(force: Boolean = false) {
        val isPlaying = getMusicActive()
        Log.d("MediaController", "Pause request: autoPaused=$iPausedTheMedia, userPlayed=$userPlayedTheMedia, playing=$isPlaying, force=$force")
        if (isPlaying && (!userPlayedTheMedia || force)) {
            val controlledBySession = MediaSessionResolver.pausePlayingSession()
            if (!controlledBySession) dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            iPausedTheMedia = true
            userPlayedTheMedia = false
            lastSelfActionAt = SystemClock.uptimeMillis()
            Log.i("MediaController", "Pause dispatched via ${if (controlledBySession) "MediaSession" else "media-key fallback"}")
        }
    }

    @Synchronized
    fun sendPlay(replayWhenPaused: Boolean = false, force: Boolean = false) {
        Log.d("MediaController", "Sending play with iPausedTheMedia: $iPausedTheMedia, replayWhenPaused: $replayWhenPaused, force: $force")
        if (replayWhenPaused) {
            lastPlayWithReplay = true
            lastPlayTime = SystemClock.uptimeMillis()
        }
        if (iPausedTheMedia || force) { // very creative, ik. thanks.
            val resumeResult = MediaSessionResolver.resumePausedSession()
            val shouldUseMediaKey = resumeResult == MediaSessionResolver.ResumeResult.NO_PAUSED_SESSION
            if (shouldUseMediaKey) dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            Log.i("MediaController", "Resume dispatched via ${if (resumeResult == MediaSessionResolver.ResumeResult.RESUMED) "same MediaSession" else if (shouldUseMediaKey) "media-key fallback" else "no-op (session changed or unsupported)"}")
            userPlayedTheMedia = false
            // Consume the auto-pause token once. A later wear frame cannot issue a duplicate play.
            iPausedTheMedia = false
            lastSelfActionAt = SystemClock.uptimeMillis()
        }
        if (!audioManager.isMusicActive && !MediaSessionResolver.hasPlayingSession()) {
            Log.d("MediaController", "Setting iPausedTheMedia to false")
            iPausedTheMedia = false
        }
        if (pausedWhileTakingOver) {
            Log.d("MediaController", "Setting pausedWhileTakingOver to false")
            pausedWhileTakingOver = false
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Synchronized
    fun startSpeaking() {
        Log.d("MediaController", "Starting speaking max vol: ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}, current vol: ${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}, conversationalAwarenessVolume: $conversationalAwarenessVolume, relativeVolume: $relativeVolume")

        if (initialVolume == null) {
            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            Log.d("MediaController", "Initial Volume: $initialVolume")
            val targetVolume = if (relativeVolume) {
                (initialVolume!! * conversationalAwarenessVolume / 100)
            } else if (initialVolume!! > (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * conversationalAwarenessVolume / 100)) {
                (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * conversationalAwarenessVolume / 100)
            } else {
                initialVolume!!
            }
            smoothVolumeTransition(initialVolume!!, targetVolume)
            if (conversationalAwarenessPauseMusic) {
                sendPause(force = true)
            }
        }
        Log.d("MediaController", "Initial Volume: $initialVolume")
    }

    @Synchronized
    fun stopSpeaking() {
        Log.d("MediaController", "Stopping speaking, initialVolume: $initialVolume")
        if (initialVolume != null) {
            smoothVolumeTransition(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), initialVolume!!)
            if (conversationalAwarenessPauseMusic) {
                sendPlay()
            }
            initialVolume = null
        }
    }

    private fun smoothVolumeTransition(fromVolume: Int, toVolume: Int) {
        Log.d("MediaController", "Smooth volume transition from $fromVolume to $toVolume")
        val step = if (fromVolume < toVolume) 1 else -1
        val delay = 50L
        var currentVolume = fromVolume

        handler.post(object : Runnable {
            override fun run() {
                if (currentVolume != toVolume) {
                    currentVolume += step
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                    handler.postDelayed(this, delay)
                }
            }
        })
    }
}
