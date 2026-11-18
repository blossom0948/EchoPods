/*
    EchoPods - AirPods controls for Android
    Based on LibrePods (GPL-3.0-or-later).
*/

package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledSlider
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

private const val LIVE_LISTEN_SAMPLE_RATE = 48_000

@Composable
fun LiveListenScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = remember { CoroutineScope(Dispatchers.Main.immediate + Job()) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val backdrop = rememberLayerBackdrop()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var listening by remember { mutableStateOf(false) }
    var gain by remember { mutableFloatStateOf(1f) }
    var level by remember { mutableFloatStateOf(0f) }
    var outputName by remember { mutableStateOf(currentBluetoothOutput(audioManager)?.productName?.toString()) }
    val gainRef = remember { AtomicReference(1f) }
    var listenJob by remember { mutableStateOf<Job?>(null) }
    var focusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }
    var activeRecorder by remember { mutableStateOf<AudioRecord?>(null) }
    var activeTrack by remember { mutableStateOf<AudioTrack?>(null) }
    val monitoringRef = remember { AtomicBoolean(false) }

    fun stopListening() {
        monitoringRef.set(false)
        listening = false
        listenJob?.cancel()
        listenJob = null
        activeRecorder?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            }
        }
        activeTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
        }
        activeRecorder = null
        activeTrack = null
        focusRequest?.let { request ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager.abandonAudioFocusRequest(request)
        }
        focusRequest = null
        level = 0f
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || listening) return
        val inputMin = AudioRecord.getMinBufferSize(
            LIVE_LISTEN_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val outputMin = AudioTrack.getMinBufferSize(
            LIVE_LISTEN_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (inputMin <= 0 || outputMin <= 0) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val focus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { change -> if (change < 0) stopListening() }
                .build()
        } else null
        if (focus != null && audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return
        }
        focusRequest = focus

        val recordBufferSize = maxOf(inputMin, LIVE_LISTEN_SAMPLE_RATE / 5)
        val trackBufferSize = maxOf(outputMin, LIVE_LISTEN_SAMPLE_RATE / 5)
        val record = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(LIVE_LISTEN_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(recordBufferSize)
                .build()
        }.getOrNull()
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(LIVE_LISTEN_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull()
        if (record == null || track == null || record.state != AudioRecord.STATE_INITIALIZED || track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { record?.release() }
            runCatching { track?.release() }
            stopListening()
            return
        }
        currentBluetoothOutput(audioManager)?.let { runCatching { track.setPreferredDevice(it) } }
        outputName = currentBluetoothOutput(audioManager)?.productName?.toString()
        activeRecorder = record
        activeTrack = track
        monitoringRef.set(true)
        listening = true
        listenJob = scope.launch(Dispatchers.IO) {
            val thisJob = coroutineContext[Job]
            val input = ShortArray(recordBufferSize / 2)
            val output = ShortArray(input.size)
            var lastLevelUpdate = 0L
            try {
                record.startRecording()
                track.play()
                while (monitoringRef.get()) {
                    val read = record.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) break
                    val volume = gainRef.get()
                    var sumSquares = 0.0
                    for (index in 0 until read) {
                        val sample = input[index].toInt()
                        sumSquares += sample.toDouble() * sample.toDouble()
                        output[index] = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    track.write(output, 0, read, AudioTrack.WRITE_BLOCKING)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastLevelUpdate >= 160) {
                        lastLevelUpdate = now
                        val rms = sqrt(sumSquares / read) / Short.MAX_VALUE
                        withContext(Dispatchers.Main.immediate) { level = rms.toFloat().coerceIn(0f, 1f) }
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: SecurityException) {
                withContext(Dispatchers.Main.immediate) { listening = false }
            } catch (_: IllegalStateException) {
                withContext(Dispatchers.Main.immediate) { listening = false }
            } finally {
                runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
                runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
                record.release()
                track.release()
                withContext(Dispatchers.Main.immediate) {
                    if (listenJob === thisJob) {
                        monitoringRef.set(false)
                        listenJob = null
                        activeRecorder = null
                        activeTrack = null
                        listening = false
                        level = 0f
                        focusRequest?.let { request ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                audioManager.abandonAudioFocusRequest(request)
                            }
                        }
                        focusRequest = null
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        if (it) startListening()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopListening()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopListening()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        StyledList {
            StyledListItem(
                name = stringResource(R.string.live_listen_status),
                description = stringResource(
                    if (listening) R.string.live_listen_active else R.string.live_listen_inactive,
                ),
                onClick = null,
                enabled = false,
                orientation = ListItemOrientation.Vertical,
            )
            StyledListItem(
                name = stringResource(R.string.live_listen_output),
                description = outputName ?: stringResource(R.string.live_listen_no_bluetooth),
                onClick = null,
                enabled = false,
                orientation = ListItemOrientation.Vertical,
            )
        }
        Text(stringResource(R.string.live_listen_level), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        StyledSlider(
            label = stringResource(R.string.live_listen_gain, gain),
            value = gain,
            onValueChange = {
                gain = it
                gainRef.set(it)
            },
            valueRange = 0.5f..2.0f,
            enabled = true,
            backdrop = backdrop,
            independent = true,
        )
        StyledButton(
            onClick = {
                if (listening) stopListening()
                else if (hasPermission) startListening()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            maxScale = 0.05f,
        ) {
            Text(
                stringResource(if (listening) R.string.live_listen_stop else R.string.live_listen_start),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(R.string.live_listen_privacy_note),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun currentBluetoothOutput(audioManager: AudioManager): AudioDeviceInfo? =
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
