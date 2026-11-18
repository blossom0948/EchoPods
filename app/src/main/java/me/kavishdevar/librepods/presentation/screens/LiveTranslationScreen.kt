/*
    EchoPods - AirPods controls for Android
    Based on LibrePods (GPL-3.0-or-later).
*/

package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import java.util.Locale
import java.util.UUID
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val backdrop = rememberLayerBackdrop()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var source by remember { mutableStateOf(translationLanguages[0]) }
    var target by remember { mutableStateOf(translationLanguages[1]) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getString(R.string.translation_ready)) }
    var recognizedText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var translator by remember { mutableStateOf<Translator?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val restartRecognition = remember { AtomicReference<(() -> Unit)?>(null) }
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
        val tts = TextToSpeech(context) { result -> ttsReady = result == TextToSpeech.SUCCESS }
        tts.setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        textToSpeech = tts
        onDispose {
            active = false
            recognizer?.destroy()
            recognizer = null
            translator?.close()
            translator = null
            tts.stop()
            tts.shutdown()
            textToSpeech = null
            restartRecognition.set(null)
        }
    }

    fun stopTranslation() {
        active = false
        restartRecognition.set(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        translator?.close()
        translator = null
        textToSpeech?.stop()
        status = context.getString(R.string.translation_stopped)
    }

    fun beginRecognition(instance: SpeechRecognizer) {
        if (!active) return
        status = context.getString(R.string.translation_listening)
        runCatching { instance.startListening(recognizerIntent) }
            .onFailure { status = context.getString(R.string.translation_recognizer_error) }
    }

    fun startTranslation() {
        if (active) return
        if (!hasMicPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            status = context.getString(R.string.translation_offline_unavailable)
            return
        }

        active = true
        recognizedText = ""
        translatedText = ""
        status = context.getString(R.string.translation_downloading_model)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source.translateTag)
            .setTargetLanguage(target.translateTag)
            .build()
        val client = Translation.getClient(options)
        translator = client
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (!active) return@addOnSuccessListener
                val speech = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                    .getOrElse {
                        status = context.getString(R.string.translation_offline_unavailable)
                        active = false
                        return@addOnSuccessListener
                    }
                recognizer = speech
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        status = context.getString(R.string.translation_listening)
                    }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        status = context.getString(R.string.translation_processing)
                    }
                    override fun onError(error: Int) {
                        if (!active) return
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            status = context.getString(R.string.translation_mic_permission)
                            active = false
                            return
                        }
                        status = context.getString(R.string.translation_listening)
                        mainHandler.postDelayed({ beginRecognition(speech) }, 500L)
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        val phrase = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (phrase.isBlank() || !active) {
                            mainHandler.postDelayed({ beginRecognition(speech) }, 250L)
                            return
                        }
                        recognizedText = phrase
                        status = context.getString(R.string.translation_processing)
                        client.translate(phrase)
                            .addOnSuccessListener { result ->
                                if (!active) return@addOnSuccessListener
                                translatedText = result
                                val tts = textToSpeech
                                if (ttsReady && tts != null && result.isNotBlank()) {
                                    val localeSet = tts.setLanguage(Locale.forLanguageTag(target.localeTag))
                                    if (localeSet != TextToSpeech.LANG_MISSING_DATA && localeSet != TextToSpeech.LANG_NOT_SUPPORTED) {
                                        status = context.getString(R.string.translation_speaking)
                                        restartRecognition.set { beginRecognition(speech) }
                                        val speakResult = tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                                        if (speakResult == TextToSpeech.ERROR) {
                                            mainHandler.postDelayed({ beginRecognition(speech) }, 400L)
                                        }
                                    } else {
                                        status = context.getString(R.string.translation_voice_unavailable)
                                        mainHandler.postDelayed({ beginRecognition(speech) }, 900L)
                                    }
                                } else {
                                    status = context.getString(R.string.translation_text_ready)
                                    mainHandler.postDelayed({ beginRecognition(speech) }, 900L)
                                }
                            }
                            .addOnFailureListener {
                                status = context.getString(R.string.translation_failed)
                                mainHandler.postDelayed({ beginRecognition(speech) }, 700L)
                            }
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        recognizedText = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                })
                restartRecognition.set { beginRecognition(speech) }
                beginRecognition(speech)
            }
            .addOnFailureListener {
                status = context.getString(R.string.translation_model_failed)
                active = false
                client.close()
                translator = null
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMicPermission = it
        if (it) startTranslation() else status = context.getString(R.string.translation_mic_permission)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
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
                description = status,
                onClick = null,
                enabled = false,
                orientation = ListItemOrientation.Vertical,
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
