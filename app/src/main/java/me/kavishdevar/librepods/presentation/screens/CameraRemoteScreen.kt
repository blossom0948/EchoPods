/*
    EchoPods - AirPods controls for Android
    Based on LibrePods (GPL-3.0-or-later).
*/

package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.AirPodsService
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class EchoCameraMode { PHOTO, VIDEO }

@Composable
fun CameraRemoteScreen(viewModel: AirPodsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backdrop = rememberLayerBackdrop()
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var mode by remember { mutableStateOf(EchoCameraMode.PHOTO) }
    var frontCamera by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordingHandle by remember { mutableStateOf<Recording?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var allowSilentVideo by remember { mutableStateOf(false) }
    var previewController by remember(context) {
        mutableStateOf(
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            },
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        audioGranted = granted
        allowSilentVideo = !granted
        if (!granted) Toast.makeText(context, R.string.camera_video_without_audio, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(cameraGranted, lifecycleOwner) {
        if (cameraGranted) {
            previewController.bindToLifecycle(lifecycleOwner)
            previewController.cameraSelector =
                if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    DisposableEffect(lifecycleOwner, previewController, cameraGranted) {
        viewModel.setCameraRemoteActive(
            cameraGranted && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && cameraGranted) {
                viewModel.setCameraRemoteActive(true)
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.setCameraRemoteActive(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { recordingHandle?.stop() }
            viewModel.setCameraRemoteActive(false)
            previewController.unbind()
        }
    }

    fun capturePhoto() {
        if (!cameraGranted || recording) return
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "EchoPods_${timestamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EchoPods")
        }
        val options = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(
            resolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        previewController.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: androidx.camera.core.ImageCapture.OutputFileResults) {
                    Toast.makeText(context, R.string.camera_photo_saved, Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    Toast.makeText(context, R.string.camera_save_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    fun toggleRecording() {
        if (!cameraGranted) return
        if (recording) {
            recordingHandle?.stop()
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "EchoPods_${timestamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EchoPods")
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()
        recordingHandle = previewController.startRecording(
            output,
            AudioConfig.create(audioGranted),
            ContextCompat.getMainExecutor(context),
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> recording = true
                is VideoRecordEvent.Finalize -> {
                    recording = false
                    recordingHandle = null
                    if (event.hasError()) {
                        Toast.makeText(context, R.string.camera_save_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.camera_video_saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun onAirPodsShutter() {
        when (mode) {
            EchoCameraMode.PHOTO -> capturePhoto()
            EchoCameraMode.VIDEO -> toggleRecording()
        }
    }

    DisposableEffect(context, mode, cameraGranted, recording) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == AirPodsService.ACTION_CAMERA_SHUTTER) onAirPodsShutter()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(AirPodsService.ACTION_CAMERA_SHUTTER),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val material = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (material) 8.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (material) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val previewHeight = minOf(maxHeight * 0.44f, maxWidth / 0.75f).coerceAtLeast(180.dp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            if (cameraGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                controller = previewController
                            }
                        },
                        update = { it.controller = previewController },
                    )
                    Text(
                        text = stringResource(if (recording) R.string.camera_recording else R.string.camera_live_preview),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(
                        text = stringResource(R.string.camera_mode_photo),
                        selected = mode == EchoCameraMode.PHOTO,
                        onClick = { mode = EchoCameraMode.PHOTO },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        enabled = !recording,
                    )
                    ModeButton(
                        text = stringResource(R.string.camera_mode_video),
                        selected = mode == EchoCameraMode.VIDEO,
                        onClick = { mode = EchoCameraMode.VIDEO },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        enabled = !recording,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(
                        text = stringResource(if (frontCamera) R.string.camera_use_rear else R.string.camera_use_front),
                        selected = false,
                        onClick = {
                            frontCamera = !frontCamera
                            previewController.cameraSelector =
                                if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        enabled = !recording,
                    )
                    ModeButton(
                        text = stringResource(if (torchEnabled) R.string.camera_torch_off else R.string.camera_torch_on),
                        selected = torchEnabled,
                        onClick = {
                            torchEnabled = !torchEnabled
                            previewController.enableTorch(torchEnabled)
                        },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        enabled = !recording,
                    )
                }
                StyledButton(
                    onClick = {
                        if (mode == EchoCameraMode.PHOTO) capturePhoto()
                        else if (audioGranted || allowSilentVideo) toggleRecording()
                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    maxScale = 0.06f,
                ) {
                    Text(
                        text = stringResource(
                            when {
                                mode == EchoCameraMode.PHOTO -> R.string.camera_take_photo
                                recording -> R.string.camera_stop_recording
                                else -> R.string.camera_start_recording
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                StyledList {
                    StyledListItem(
                        name = stringResource(R.string.camera_remote_press_hint),
                        description = stringResource(R.string.camera_remote_press_hint_description),
                        onClick = null,
                        enabled = false,
                        orientation = ListItemOrientation.Vertical,
                    )
                }
            } else {
                Spacer(Modifier.height(24.dp))
                StyledList {
                    StyledListItem(
                        name = stringResource(R.string.camera_permission_title),
                        description = stringResource(R.string.camera_permission_description),
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        orientation = ListItemOrientation.Vertical,
                    )
                }
            }
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    StyledButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        surfaceColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        maxScale = 0.04f,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
