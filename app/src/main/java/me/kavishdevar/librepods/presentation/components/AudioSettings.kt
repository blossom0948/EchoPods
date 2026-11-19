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

package me.kavishdevar.librepods.presentation.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import kotlin.math.roundToInt
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun MediaVolumeSlider() {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember(audioManager) {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    LaunchedEffect(audioManager) {
        while (true) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            if (volume != currentVolume) volume = currentVolume
            delay(400L)
        }
    }

    val percent = (volume / maxVolume * 100f).roundToInt().coerceIn(0, 100)
    val isAppleAppearance = LocalDesignSystem.current == DesignSystem.Apple
    val label = stringResource(
        if (isAppleAppearance) R.string.media_volume_ios_label else R.string.media_volume,
    )
    if (isAppleAppearance) {
        var dragFraction by remember(audioManager) {
            mutableFloatStateOf(volume / maxVolume.toFloat())
        }
        var isDragging by remember { mutableStateOf(false) }
        val fraction = (volume / maxVolume.toFloat()).coerceIn(0f, 1f)
        val visibleFraction by animateFloatAsState(
            targetValue = if (isDragging) dragFraction else fraction,
            animationSpec = tween(durationMillis = 90),
            label = "media-volume-fill",
        )
        val latestChange by rememberUpdatedState<(Float) -> Unit> { requestedFraction ->
            val exactVolume = requestedFraction.coerceIn(0f, 1f) * maxVolume
            dragFraction = requestedFraction.coerceIn(0f, 1f)
            volume = exactVolume
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                exactVolume.roundToInt().coerceIn(0, maxVolume),
                0,
            )
        }
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        val fillColor = MaterialTheme.colorScheme.onSurface
        val iconColor = if (visibleFraction >= 0.10f) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        }
        val valueDescription = stringResource(R.string.media_volume_percent, percent)

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .pointerInput(maxVolume) {
                        detectTapGestures { offset ->
                            val fractionAtTap = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            latestChange(fractionAtTap)
                        }
                    }
                    .pointerInput(maxVolume) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val fractionAtStart =
                                    (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                latestChange(fractionAtStart)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            val deltaFraction = dragAmount.x / size.width.toFloat()
                            latestChange((dragFraction + deltaFraction).coerceIn(0f, 1f))
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        stateDescription = valueDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            fraction,
                            0f..1f,
                        )
                        setProgress { requested ->
                            latestChange(requested)
                            true
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(visibleFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(fillColor),
                )
                VolumeSpeakerGlyph(
                    tint = iconColor,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(24.dp),
                )
            }
        }
    } else {
        StyledSlider(
            label = label,
            description = stringResource(R.string.media_volume_description),
            value = volume,
            onValueChange = { newVolume ->
                val streamVolume = newVolume.roundToInt().coerceIn(0, maxVolume)
                volume = streamVolume.toFloat()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, streamVolume, 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            startLabel = stringResource(R.string.media_volume_min),
            endLabel = stringResource(R.string.media_volume_percent, percent),
            independent = true,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun VolumeSpeakerGlyph(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val speaker = Path().apply {
            moveTo(3f * scaleX, 9f * scaleY)
            lineTo(7f * scaleX, 9f * scaleY)
            lineTo(12f * scaleX, 5f * scaleY)
            lineTo(12f * scaleX, 19f * scaleY)
            lineTo(7f * scaleX, 15f * scaleY)
            lineTo(3f * scaleX, 15f * scaleY)
            close()
        }
        drawPath(speaker, tint)
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(9f * scaleX, 7f * scaleY),
            size = Size(9f * scaleX, 10f * scaleY),
            style = Stroke(width = 2f * scaleX),
        )
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(7f * scaleX, 3f * scaleY),
            size = Size(16f * scaleX, 18f * scaleY),
            style = Stroke(width = 2f * scaleX),
        )
    }
}

@Composable
fun AudioSettings(
    adaptiveVolumeCapability: Boolean,
    conversationalAwarenessCapability: Boolean,
    loudSoundReductionCapability: Boolean,
    adaptiveAudioCapability: Boolean,
    customEqCapability: Boolean,

    adaptiveVolumeChecked: Boolean,
    onAdaptiveVolumeCheckedChange: (Boolean) -> Unit,

    conversationalAwarenessChecked: Boolean,
    onConversationalAwarenessCheckedChange: (Boolean) -> Unit,

    loudSoundReductionChecked: Boolean,
    onLoudSoundReductionCheckedChange: (Boolean) -> Unit,

    navigateToAdaptiveStrength: () -> Unit,
    navigateToEqualizer: () -> Unit,
    navigateToConversationSettings: () -> Unit,

    vendorIdHook: Boolean,
) {
    var conversationEnabled by remember(conversationalAwarenessChecked) {
        mutableStateOf(conversationalAwarenessChecked)
    }

    if (adaptiveVolumeCapability || conversationalAwarenessCapability || loudSoundReductionCapability || adaptiveAudioCapability) {
        StyledList(title = stringResource(R.string.audio_routing)) {
            if (adaptiveVolumeCapability) {
                StyledToggle(
                    label = stringResource(R.string.personalized_volume),
                    description = stringResource(R.string.personalized_volume_description),
                    checked = adaptiveVolumeChecked,
                    onCheckedChange = onAdaptiveVolumeCheckedChange,
                )
            }

            if (conversationalAwarenessCapability) {
                StyledToggle(
                    label = stringResource(R.string.conversational_awareness),
                    description = stringResource(R.string.conversational_awareness_description),
                    checked = conversationEnabled,
                    onCheckedChange = { enabled ->
                        // Reflect the user's tap in this composition immediately; device
                        // control notifications may arrive later over Bluetooth.
                        conversationEnabled = enabled
                        onConversationalAwarenessCheckedChange(enabled)
                    },
                )
                AnimatedVisibility(
                    visible = conversationEnabled,
                    enter = expandVertically(animationSpec = tween(150)) + fadeIn(tween(120)),
                    exit = shrinkVertically(animationSpec = tween(120)) + fadeOut(tween(90)),
                ) {
                    StyledListItem(
                        name = stringResource(R.string.conversation_mode_return_open),
                        description = stringResource(R.string.conversation_mode_return_open_description),
                        orientation = ListItemOrientation.Vertical,
                        onClick = navigateToConversationSettings,
                    )
                }
            }

            if (loudSoundReductionCapability && vendorIdHook) {
                StyledToggle(
                    label = stringResource(R.string.loud_sound_reduction),
                    description = stringResource(R.string.loud_sound_reduction_description),
                    checked = loudSoundReductionChecked,
                    onCheckedChange = onLoudSoundReductionCheckedChange,
                )
            }

            if (adaptiveAudioCapability) {
                StyledListItem(
                    name = stringResource(R.string.adaptive_audio),
                    onClick = navigateToAdaptiveStrength,
                )
            }

            if (customEqCapability) {
                StyledListItem(
                    name = stringResource(R.string.equalizer),
                    onClick = navigateToEqualizer,
                )
            }
        }
    }
}
