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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.R
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
    StyledSlider(
        label = stringResource(R.string.media_volume),
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
    Spacer(Modifier.height(8.dp))
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
