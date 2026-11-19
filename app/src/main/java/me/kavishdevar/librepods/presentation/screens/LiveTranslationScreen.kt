/*
    EchoPods - AirPods controls for Android
    Based on LibrePods (GPL-3.0-or-later).
*/

package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.ModelDownloadListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private data class TranslationLanguage(
    val label: String,
    val translateTag: String,
    val localeTag: String,
)

private val translationLanguages = listOf(
    TranslationLanguage("한국어", TranslateLanguage.KOREAN, "ko-KR"),
    TranslationLanguage("English", TranslateLanguage.ENGLISH, "en-US"),
    TranslationLanguage("日本語", TranslateLanguage.JAPANESE, "ja-JP"),
    TranslationLanguage("中文", TranslateLanguage.CHINESE, "zh-CN"),
    TranslationLanguage("Español", TranslateLanguage.SPANISH, "es-ES"),
    TranslationLanguage("Français", TranslateLanguage.FRENCH, "fr-FR"),
    TranslationLanguage("Deutsch", TranslateLanguage.GERMAN, "de-DE"),
)

@Composable
fun LiveTranslationScreen() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val backdrop = rememberLayerBackdrop()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var source by remember { mutableStateOf(translationLanguages[0]) }
    var target by remember { mutableStateOf(translationLanguages[1]) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(R.string.translation_ready) }
    var recognizedText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var translator by remember { mutableStateOf<Translator?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsInitializationFinished by remember { mutableStateOf(false) }
    var recognitionOnDevice by remember { mutableStateOf(true) }
    var airPodsMicRouteActive by remember { mutableStateOf(false) }
    var previousAudioMode by remember { mutableIntStateOf(AudioManager.MODE_NORMAL) }
    var previousCommunicationDevice by remember { mutableStateOf<AudioDeviceInfo?>(null) }
    var speechModelProgress by remember { mutableIntStateOf(-1) }
    var statusDetail by remember { mutableStateOf("") }
    var startRequested by remember { mutableStateOf(false) }
    val sessionToken = remember { AtomicInteger(0) }
    val restartRecognition = remember { AtomicReference<(() -> Unit)?>(null) }

    fun releaseSpeechRoute() {
        if (!airPodsMicRouteActive) return
        runCatching {
            val previous = previousCommunicationDevice
            if (previous != null) audioManager.setCommunicationDevice(previous)
            else audioManager.clearCommunicationDevice()
            audioManager.mode = previousAudioMode
        }
        airPodsMicRouteActive = false
        previousCommunicationDevice = null
    }

    fun routeSpeechToAirPodsIfAvailable(): Boolean {
        val airPodsCommunicationDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
            device.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                device.productName.toString().contains("AirPods", ignoreCase = true)
        } ?: return false
        previousAudioMode = audioManager.mode
        previousCommunicationDevice = audioManager.communicationDevice
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        airPodsMicRouteActive = runCatching {
            audioManager.setCommunicationDevice(airPodsCommunicationDevice)
        }.getOrDefault(false)
        if (!airPodsMicRouteActive) {
            audioManager.mode = previousAudioMode
            previousCommunicationDevice = null
        }
        return airPodsMicRouteActive
    }
    val recognizerIntent = remember(source) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, source.localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, source.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    DisposableEffect(context) {
        val tts = runCatching {
            TextToSpeech(appContext) { result ->
                mainHandler.post {
                    ttsInitializationFinished = true
                    ttsReady = result == TextToSpeech.SUCCESS
                    if (result != TextToSpeech.SUCCESS && (active || startRequested)) {
                        status = R.string.translation_voice_unavailable
                        startRequested = false
                    }
                }
            }
        }.getOrNull()
        textToSpeech = tts
        tts?.let { engine ->
            runCatching {
                engine.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { restartRecognition.get()?.invoke() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { restartRecognition.get()?.invoke() }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post { restartRecognition.get()?.invoke() }
                    }
                })
            }.onFailure {
                ttsReady = false
            }
        }
        onDispose {
            active = false
            sessionToken.incrementAndGet()
            mainHandler.removeCallbacksAndMessages(null)
            releaseSpeechRoute()
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            runCatching { translator?.close() }
            translator = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            textToSpeech = null
            restartRecognition.set(null)
        }
    }

    fun stopTranslation() {
        active = false
        startRequested = false
        sessionToken.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
        releaseSpeechRoute()
        restartRecognition.set(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { translator?.close() }
        translator = null
        runCatching { textToSpeech?.stop() }
        status = R.string.translation_stopped
    }

    fun beginRecognition(instance: SpeechRecognizer, token: Int) {
        if (!active || sessionToken.get() != token) return
        status = when {
            airPodsMicRouteActive -> R.string.translation_listening_airpods_mic
            recognitionOnDevice -> R.string.translation_listening_on_device
            else -> R.string.translation_listening_system
        }
        runCatching { instance.startListening(recognizerIntent) }
            .onFailure {
                status = R.string.translation_recognizer_error
                active = false
                restartRecognition.set(null)
            }
    }

    fun startTranslation() {
        if (active) return
        if (textToSpeech == null) {
            status = R.string.translation_voice_unavailable
            return
        }
        if (!ttsReady) {
            if (ttsInitializationFinished) status = R.string.translation_voice_unavailable
            else {
                startRequested = true
                status = R.string.translation_voice_preparing
            }
            return
        }
        if (!hasMicPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val onDeviceSpeechAvailable = runCatching {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        }.getOrDefault(false)
        val speechAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        }.getOrDefault(false)
        if (!speechAvailable) {
            status = R.string.translation_offline_unavailable
            return
        }
        recognitionOnDevice = onDeviceSpeechAvailable
        speechModelProgress = -1
        statusDetail = ""

        val token = sessionToken.incrementAndGet()
        active = true
        recognizedText = ""
        translatedText = ""
        status = R.string.translation_downloading_model
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source.translateTag)
            .setTargetLanguage(target.translateTag)
            .build()
        val client = runCatching { Translation.getClient(options) }.getOrElse { error ->
            Log.e("EchoPodsTranslation", "Could not create translation client", error)
            active = false
            status = R.string.translation_model_failed
            statusDetail = error.localizedMessage.orEmpty()
            return
        }
        translator = client
        val downloadTask = runCatching {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build())
        }.getOrElse { error ->
            Log.e("EchoPodsTranslation", "Could not request translation language model", error)
            active = false
            translator = null
            runCatching { client.close() }
            status = R.string.translation_model_failed
            statusDetail = error.localizedMessage.orEmpty()
            return
        }
        downloadTask
            .addOnSuccessListener {
                if (!active || sessionToken.get() != token) {
                    runCatching { client.close() }
                    return@addOnSuccessListener
                }
                airPodsMicRouteActive = routeSpeechToAirPodsIfAvailable()
                val speech = runCatching {
                    if (onDeviceSpeechAvailable) SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    else SpeechRecognizer.createSpeechRecognizer(appContext)
                }
                    .getOrElse {
                        status = R.string.translation_offline_unavailable
                        active = false
                        sessionToken.incrementAndGet()
                        translator = null
                        runCatching { client.close() }
                        releaseSpeechRoute()
                        return@addOnSuccessListener
                    }
                recognizer = speech
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        if (active && sessionToken.get() == token) {
                            status = when {
                                airPodsMicRouteActive -> R.string.translation_listening_airpods_mic
                                recognitionOnDevice -> R.string.translation_listening_on_device
                                else -> R.string.translation_listening_system
                            }
                        }
                    }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        if (active && sessionToken.get() == token) status = R.string.translation_processing
                    }
                    override fun onError(error: Int) {
                        if (!active || sessionToken.get() != token) return
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            status = R.string.translation_mic_permission
                            active = false
                            sessionToken.incrementAndGet()
                            return
                        }
                        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                        ) {
                            active = false
                            sessionToken.incrementAndGet()
                            mainHandler.removeCallbacksAndMessages(null)
                            restartRecognition.set(null)
                            runCatching { speech.cancel() }
                            runCatching { client.close() }
                            translator = null
                            status = R.string.translation_speech_model_requested
                            val finishModelRequest: (Int, String?) -> Unit = { message, detail ->
                                mainHandler.post {
                                    speechModelProgress = -1
                                    status = message
                                    statusDetail = detail.orEmpty()
                                    runCatching { speech.destroy() }
                                    if (recognizer === speech) recognizer = null
                                    releaseSpeechRoute()
                                }
                            }
                            val requested = when {
                                Build.VERSION.SDK_INT >= 34 -> runCatching {
                                    speech.triggerModelDownload(
                                        recognizerIntent,
                                        appContext.mainExecutor,
                                        object : ModelDownloadListener {
                                            override fun onProgress(progress: Int) {
                                                speechModelProgress = progress.coerceIn(0, 100)
                                            }
                                            override fun onSuccess() {
                                                finishModelRequest(R.string.translation_speech_model_ready, null)
                                            }
                                            override fun onScheduled() {
                                                finishModelRequest(R.string.translation_speech_model_requested, null)
                                            }
                                            override fun onError(error: Int) {
                                                finishModelRequest(
                                                    R.string.translation_speech_model_download_failed,
                                                    error.toString(),
                                                )
                                            }
                                        },
                                    )
                                    true
                                }.getOrDefault(false)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> runCatching {
                                    speech.triggerModelDownload(recognizerIntent)
                                    runCatching { speech.destroy() }
                                    recognizer = null
                                    releaseSpeechRoute()
                                    true
                                }.getOrDefault(false)
                                else -> false
                            }
                            if (!requested) {
                                finishModelRequest(R.string.translation_speech_model_missing, null)
                            }
                            return
                        }
                        status = when {
                            airPodsMicRouteActive -> R.string.translation_listening_airpods_mic
                            recognitionOnDevice -> R.string.translation_listening_on_device
                            else -> R.string.translation_listening_system
                        }
                        mainHandler.postDelayed({ beginRecognition(speech, token) }, 700L)
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        val phrase = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (phrase.isBlank() || !active || sessionToken.get() != token) {
                            mainHandler.postDelayed({ beginRecognition(speech, token) }, 350L)
                            return
                        }
                        recognizedText = phrase
                        status = R.string.translation_processing
                        runCatching { client.translate(phrase) }
                            .getOrElse { error ->
                                Log.e("EchoPodsTranslation", "Translation request failed", error)
                                status = R.string.translation_failed
                                statusDetail = error.localizedMessage.orEmpty()
                                mainHandler.postDelayed({ beginRecognition(speech, token) }, 800L)
                                return
                            }
                            .addOnSuccessListener { result ->
                                if (!active || sessionToken.get() != token) return@addOnSuccessListener
                                translatedText = result
                                val tts = textToSpeech
                                if (ttsReady && tts != null && result.isNotBlank()) {
                                    val localeSet = runCatching {
                                        tts.setLanguage(Locale.forLanguageTag(target.localeTag))
                                    }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                                    if (localeSet != TextToSpeech.LANG_MISSING_DATA && localeSet != TextToSpeech.LANG_NOT_SUPPORTED) {
                                        status = R.string.translation_speaking
                                        statusDetail = ""
                                        val speakResult = runCatching {
                                            tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                                        }.getOrDefault(TextToSpeech.ERROR)
                                        if (speakResult == TextToSpeech.ERROR) {
                                            status = R.string.translation_voice_output_failed
                                            mainHandler.postDelayed({ beginRecognition(speech, token) }, 400L)
                                        }
                                    } else {
                                        status = R.string.translation_voice_unavailable
                                        mainHandler.postDelayed({ beginRecognition(speech, token) }, 900L)
                                    }
                                } else {
                                    status = R.string.translation_text_ready
                                    mainHandler.postDelayed({ beginRecognition(speech, token) }, 900L)
                                }
                            }
                            .addOnFailureListener { error ->
                                if (!active || sessionToken.get() != token) return@addOnFailureListener
                                Log.e("EchoPodsTranslation", "Translation model request failed", error)
                                status = R.string.translation_failed
                                statusDetail = error.localizedMessage.orEmpty()
                                mainHandler.postDelayed({ beginRecognition(speech, token) }, 700L)
                            }
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        if (!active || sessionToken.get() != token) return
                        recognizedText = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                })
                restartRecognition.set { beginRecognition(speech, token) }
                beginRecognition(speech, token)
            }
            .addOnFailureListener { error ->
                if (sessionToken.get() != token) return@addOnFailureListener
                Log.e("EchoPodsTranslation", "Translation language model download failed", error)
                status = R.string.translation_model_failed
                statusDetail = error.localizedMessage.orEmpty()
                active = false
                sessionToken.incrementAndGet()
                runCatching { client.close() }
                translator = null
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMicPermission = it
        if (it) startTranslation() else status = R.string.translation_mic_permission
    }

    LaunchedEffect(ttsReady, startRequested) {
        if (ttsReady && startRequested) {
            startRequested = false
            startTranslation()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && active) stopTranslation()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(active) {
        if (active && !hasMicPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            active = false
        }
    }

    val material = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (material) 8.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (material) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .layerBackdrop(backdrop)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LanguagePicker(
                    label = stringResource(R.string.translation_source_language),
                    selected = source,
                    enabled = !active,
                    modifier = Modifier.weight(1f),
                    onSelect = { source = it },
                )
                LanguagePicker(
                    label = stringResource(R.string.translation_target_language),
                    selected = target,
                    enabled = !active,
                    modifier = Modifier.weight(1f),
                    onSelect = { target = it },
                )
            }
            StyledList {
                StyledListItem(
                    name = stringResource(R.string.translation_status),
                    description = when {
                        speechModelProgress >= 0 -> stringResource(
                            R.string.translation_speech_model_progress,
                            speechModelProgress,
                        )
                        statusDetail.isNotBlank() -> stringResource(
                            R.string.translation_status_detail,
                            statusDetail,
                        )
                        else -> stringResource(status)
                    },
                    onClick = null,
                    enabled = false,
                    orientation = ListItemOrientation.Vertical,
                    descriptionMaxLines = 3,
                )
            }
            StyledList {
                StyledListItem(
                    name = stringResource(R.string.translation_you_said),
                    description = recognizedText.ifBlank { stringResource(R.string.translation_waiting_text) },
                    onClick = null,
                    enabled = false,
                    orientation = ListItemOrientation.Vertical,
                )
                StyledListItem(
                    name = stringResource(R.string.translation_result),
                    description = translatedText.ifBlank { stringResource(R.string.translation_waiting_text) },
                    onClick = null,
                    enabled = false,
                    orientation = ListItemOrientation.Vertical,
                )
            }
            StyledButton(
                onClick = {
                    if (active) stopTranslation()
                    else if (hasMicPermission) startTranslation()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                maxScale = 0.05f,
            ) {
                Text(
                    stringResource(if (active) R.string.translation_stop else R.string.translation_start),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.translation_privacy_note),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: TranslationLanguage,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (TranslationLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        BoxAnchor(
            selected = selected,
            enabled = enabled,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            translationLanguages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.label) },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun BoxAnchor(
    selected: TranslationLanguage,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    StyledList {
        StyledListItem(
            name = selected.label,
            onClick = onClick.takeIf { enabled },
            enabled = enabled,
            orientation = ListItemOrientation.Vertical,
        )
    }
}
