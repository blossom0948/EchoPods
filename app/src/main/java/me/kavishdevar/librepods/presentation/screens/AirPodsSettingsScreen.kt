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

package me.kavishdevar.librepods.presentation.screens

// import me.kavishdevar.librepods.utils.RadareOffsetFinder
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.graphics.shapes.Morph
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.HeartRateStreamStatus
import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.data.AirPodsFormFactor
import me.kavishdevar.librepods.presentation.MaterialIcons
import me.kavishdevar.librepods.presentation.components.AboutCard
import me.kavishdevar.librepods.presentation.components.AudioSettings
import me.kavishdevar.librepods.presentation.components.BatteryView
import me.kavishdevar.librepods.presentation.components.CallControlSettings
import me.kavishdevar.librepods.presentation.components.ConnectionSettings
import me.kavishdevar.librepods.presentation.components.HearingHealthSettings
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.MediaVolumeSlider
import me.kavishdevar.librepods.presentation.components.MaterialButtonStyle
import me.kavishdevar.librepods.presentation.components.NoiseControlSettings
import me.kavishdevar.librepods.presentation.components.PressAndHoldSettings
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem as StyledStandaloneListItem
import me.kavishdevar.librepods.presentation.components.StyledListItem as StyledListItemInGroup
import me.kavishdevar.librepods.presentation.components.StyledToggle as StyledToggleInGroup
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsUiState
import me.kavishdevar.librepods.utils.XposedState
import me.kavishdevar.librepods.utils.canAttemptAacpControlSession
import me.kavishdevar.librepods.utils.hasRootlessBluetoothSupport
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.demoState
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

@Composable
fun AirPodsSettingsRoute(
    viewModel: AirPodsViewModel,
    navigateToRename: () -> Unit,
    navigateToHearingProtection: () -> Unit,
    navigateToHearingAid: () -> Unit,
    navigateToLeftLongPress: () -> Unit,
    navigateToRightLongPress: () -> Unit,
    navigateToPurchase: () -> Unit,
    navigateToAdaptiveStrength: () -> Unit,
    navigateToEqualizer: () -> Unit,
    navigateToHeadTracking: () -> Unit,
    navigateToAccessibility: () -> Unit,
    navigateToVersion: () -> Unit,
    navigateToTroubleshooting: () -> Unit,
    navigateToCallControlScreen: (action: String) -> Unit,
    navigateToMicrophoneSettings: () -> Unit,
    navigateToConversationSettings: () -> Unit,
    navigateToCameraControl: () -> Unit,
    navigateToLiveListen: () -> Unit,
    navigateToLiveTranslation: () -> Unit,
    navigateToAirPodsFinder: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + if (m3eEnabled) 0.dp else 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    Box (
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AirPodsSettingsScreen(
            state = state,

            topPadding = topPadding,
            bottomPadding = bottomPadding,

            setControlCommandInt = viewModel::setControlCommandInt,
            setControlCommandBoolean = viewModel::setControlCommandBoolean,
//        setControlCommandValue = viewModel::setControlCommandValue,
            setControlCommandByte = viewModel::setControlCommandByte,

            setATTCharacteristicValue = viewModel::setATTCharacteristicValue,

            onAutomaticEarDetectionChanged = viewModel::setAutomaticEarDetectionEnabled,
            onAutomaticConnectionChanged = viewModel::setAutomaticConnectionEnabled,
            setDynamicEndOfCharge = viewModel::setDynamicEndOfCharge,
            setChargingNotificationsEnabled = viewModel::setChargingNotificationsEnabled,
            setOffListeningMode = viewModel::setOffListeningMode,
            disconnect = viewModel::disconnect,

            navigateToRename = navigateToRename,
            navigateToHearingProtection = navigateToHearingProtection,
            navigateToHearingAid = navigateToHearingAid,
            navigateToLeftLongPress = navigateToLeftLongPress,
            navigateToRightLongPress = navigateToRightLongPress,
            navigateToPurchase = navigateToPurchase,
            navigateToAdaptiveStrength = navigateToAdaptiveStrength,
            navigateToEqualizer = navigateToEqualizer,
            navigateToHeadTracking = navigateToHeadTracking,
            navigateToAccessibility = navigateToAccessibility,
            navigateToVersion = navigateToVersion,
            navigateToTroubleshooting = navigateToTroubleshooting,
            navigateToCallControlScreen = navigateToCallControlScreen,
            navigateToMicrophoneSettings = navigateToMicrophoneSettings,
            navigateToConversationSettings = navigateToConversationSettings,
            navigateToCameraControl = navigateToCameraControl,
            navigateToLiveListen = navigateToLiveListen,
            navigateToLiveTranslation = navigateToLiveTranslation,
            navigateToAirPodsFinder = navigateToAirPodsFinder,

            reconnectFromSavedMac = viewModel::reconnectFromSavedMac,
            selectPreferredAirPods = viewModel::selectPreferredAirPods,
        )
    }
}

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
@Composable
fun AirPodsSettingsScreen(
        state: AirPodsUiState,

        topPadding: Dp = 16.dp,
        bottomPadding: Dp = 16.dp,

        setControlCommandInt: (AACPManager.Companion.ControlCommandIdentifiers, Int) -> Unit,
        setControlCommandBoolean: (AACPManager.Companion.ControlCommandIdentifiers, Boolean) -> Unit,
//        setControlCommandValue: (AACPManager.Companion.ControlCommandIdentifiers, ByteArray) -> Unit,
        setControlCommandByte: (AACPManager.Companion.ControlCommandIdentifiers, Byte) -> Unit,
        setATTCharacteristicValue: (ATTHandles, ByteArray) -> Unit,

        onAutomaticEarDetectionChanged: (Boolean) -> Unit,
        onAutomaticConnectionChanged: (Boolean) -> Unit,
        setDynamicEndOfCharge: (Boolean) -> Unit,
        setChargingNotificationsEnabled: (Boolean) -> Unit,
        setOffListeningMode: (Boolean) -> Unit,
        disconnect: () -> Unit,

        navigateToRename: () -> Unit,
        navigateToHearingProtection: () -> Unit,
        navigateToHearingAid: () -> Unit,
        navigateToLeftLongPress: () -> Unit,
        navigateToRightLongPress: () -> Unit,
        navigateToPurchase: () -> Unit,
        navigateToAdaptiveStrength: () -> Unit,
        navigateToEqualizer: () -> Unit,
        navigateToHeadTracking: () -> Unit,
        navigateToAccessibility: () -> Unit,
        navigateToVersion: () -> Unit,
        navigateToTroubleshooting: () -> Unit,
        navigateToCallControlScreen: (action: String) -> Unit,
        navigateToMicrophoneSettings: () -> Unit,

        reconnectFromSavedMac: () -> Unit,
        navigateToConversationSettings: () -> Unit = {},
        navigateToCameraControl: () -> Unit = {},
        navigateToLiveListen: () -> Unit = {},
        navigateToLiveTranslation: () -> Unit = {},
        navigateToAirPodsFinder: () -> Unit = {},
        selectPreferredAirPods: (String, String) -> Boolean = { _, _ -> false },
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", MODE_PRIVATE)
    val useMaterialAppearance = LocalDesignSystem.current == DesignSystem.Material
    val aacpControlSupported = canAttemptAacpControlSession(
        rootlessBluetoothSupported = hasRootlessBluetoothSupport(),
        bluetoothScopeEnabled = XposedState.bluetoothScopeEnabled,
    )
    var deviceName by remember {
        mutableStateOf(
            TextFieldValue(
                sharedPreferences.getString("name", state.deviceName).toString()
            )
        )
    }

    val nameChangeListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "name") {
                deviceName =
                    TextFieldValue(sharedPreferences.getString("name", "AirPods Pro").toString())
            }
        }
    }

    DisposableEffect(Unit) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(nameChangeListener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(nameChangeListener)
        }
    }

    if (state.isLocallyConnected) {
        val capabilities = state.capabilities

        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp)
        ) {
            item(key = "top_padding") { Spacer(modifier = Modifier.height(topPadding)) }
            item(key = "battery") {
                BatteryView(
                    batteryList = state.battery,
                    budsRes = state.instance?.model?.budsRes,
                    caseRes = state.instance?.model?.caseRes,
                    formFactor = state.instance?.model?.formFactor ?: AirPodsFormFactor.IN_EAR,
                )
            }
            item(key = "spacer_battery") {
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (state.instance?.model?.capabilities?.contains(Capability.HRM) == true) {
                item(key = "heart_rate_android_support") {
                    val heartRate = state.heartRateStream
                    val heartRateSampleAge = heartRate.sampledAtEpochMillis?.let {
                        System.currentTimeMillis() - it
                    }
                    val currentBpm = heartRate.bpm?.takeIf { it in 30..220 }
                    val isFresh = heartRate.status == HeartRateStreamStatus.ACTIVE &&
                        currentBpm != null && heartRateSampleAge?.let { it in 0..15_000 } == true
                    val statusDescription = when {
                        isFresh -> stringResource(R.string.heart_rate_current, currentBpm)
                        else -> stringResource(
                            when (heartRate.status) {
                                HeartRateStreamStatus.DISCONNECTED -> R.string.heart_rate_waiting_connection
                                HeartRateStreamStatus.OFF -> R.string.heart_rate_off
                                HeartRateStreamStatus.WAITING -> R.string.heart_rate_measuring
                                HeartRateStreamStatus.ACTIVE -> R.string.heart_rate_stale
                                HeartRateStreamStatus.NO_DATA -> R.string.heart_rate_no_data
                            }
                        )
                    }
                    val workoutAppMissingText = stringResource(R.string.heart_rate_workout_app_missing)
                    StyledList(title = stringResource(R.string.heart_rate_sensor)) {
                        StyledListItemInGroup(
                            name = stringResource(R.string.heart_rate_workout_open),
                            description = stringResource(R.string.heart_rate_workout_open_description),
                            onClick = {
                                val launchIntent = context.packageManager
                                    .getLaunchIntentForPackage("com.galaxyairpods.health")
                                if (launchIntent != null) {
                                    launchIntent.putExtra(
                                        "appearance_material",
                                        useMaterialAppearance,
                                    )
                                    context.startActivity(launchIntent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        workoutAppMissingText,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            enabled = true,
                            orientation = ListItemOrientation.Vertical,
                        )
                        StyledListItemInGroup(
                            name = stringResource(R.string.heart_rate_status),
                            description = statusDescription,
                            onClick = null,
                            enabled = false,
                            orientation = ListItemOrientation.Vertical,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item(key = "name") {
                StyledStandaloneListItem(
                    name = stringResource(R.string.name),
                    description = deviceName.text,
                    onClick = navigateToRename,
                )
            }

            val hasHearingAidCapability =
                state.instance?.model?.capabilities?.contains(Capability.HEARING_AID) == true
            val hasPPECapability =
                state.instance?.model?.capabilities?.contains(Capability.PPE) == true

            if (hasHearingAidCapability || hasPPECapability) {
                if (hasPPECapability || state.vendorIdHook) {
                    item(key = "spacer_hearing_health") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                item(key = "hearing_health") {
                    HearingHealthSettings(
                        hasPPECapability = hasPPECapability,
                        hasHearingAidCapability = hasHearingAidCapability,
                        vendorIdHook = state.vendorIdHook,
                        navigateToHearingProtection = navigateToHearingProtection,
                        navigateToHearingAid = navigateToHearingAid
                    )
                }
            }

            if (capabilities.contains(Capability.LISTENING_MODE)) {
                item(key = "spacer_noise") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item(key = "noise_control") {
                    NoiseControlSettings(
                        showOffListeningMode = state.offListeningMode,
                        noiseControlModeValue = state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE]?.getOrNull(
                            0
                        )?.toInt() ?: 3,
                        adaptiveAudioAvailable = capabilities.contains(Capability.ADAPTIVE_AUDIO),
                        adaptiveAudioStrengthPercent = 100 - (
                            state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.AUTO_ANC_STRENGTH]
                                ?.getOrNull(0)?.toInt()?.and(0xFF) ?: 50
                            ),
                        onNoiseControlModeChanged = {
                            setControlCommandInt(
                                AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE, it
                            )
                        },
                        onAdaptiveAudioStrengthChanged = { strength ->
                            setControlCommandInt(
                                AACPManager.Companion.ControlCommandIdentifiers.AUTO_ANC_STRENGTH,
                                100 - strength.coerceIn(0, 100),
                            )
                        },
                    )
                }
            }

            item(key = "media_volume") {
                MediaVolumeSlider()
            }

            if (capabilities.contains(Capability.STEM_CONFIG)) {
                item(key = "spacer_press_hold") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item(key = "press_hold") {
                    PressAndHoldSettings(
                        leftAction = state.leftAction,
                        rightAction = state.rightAction,
                        navigateToLeftLongPress = navigateToLeftLongPress,
                        navigateToRightLongPress = navigateToRightLongPress
                    )
                }
            }

            item(key = "spacer_call") {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item(key = "call_control") {
                val bytes =
                    state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.CALL_MANAGEMENT_CONFIG]?.take(
                        2
                    )?.toByteArray() ?: byteArrayOf(0x00, 0x00)
                val flipped = try {
                    bytes[1] == 0x02.toByte()
                } catch (_: Exception) {
                    false
                }
                CallControlSettings(
                    flipped = flipped,
                    navigateToCallControlScreen = navigateToCallControlScreen
                )
            }

            item(key = "spacer_device_features") {
                Spacer(modifier = Modifier.height(20.dp))
            }
            item(key = "device_features") {
                StyledList(title = stringResource(R.string.airpods_extras)) {
                    StyledListItemInGroup(
                        name = stringResource(R.string.airpods_finder_title),
                        description = stringResource(R.string.airpods_finder_summary),
                        onClick = navigateToAirPodsFinder,
                        orientation = ListItemOrientation.Vertical,
                    )
                    if (capabilities.contains(Capability.STEM_CONFIG)) {
                        StyledListItemInGroup(
                            name = stringResource(R.string.camera_remote_title),
                            description = stringResource(R.string.camera_remote_description),
                            onClick = navigateToCameraControl,
                            orientation = ListItemOrientation.Vertical,
                        )
                    }
                    StyledListItemInGroup(
                        name = stringResource(R.string.live_listen_title),
                        description = stringResource(R.string.live_listen_description),
                        onClick = navigateToLiveListen,
                        orientation = ListItemOrientation.Vertical,
                    )
                    StyledListItemInGroup(
                        name = stringResource(R.string.live_translation_title),
                        description = stringResource(R.string.live_translation_description),
                        onClick = navigateToLiveTranslation,
                        orientation = ListItemOrientation.Vertical,
                    )
                }
            }

            item(key = "spacer_audio") { Spacer(modifier = Modifier.height(16.dp)) }
            item(key = "audio") {
                val capabilities = state.instance?.model?.capabilities.orEmpty()
                val adaptiveVolumeCapability =
                    capabilities.contains(Capability.ADAPTIVE_VOLUME)
                val conversationalAwarenessCapability =
                    capabilities.contains(Capability.CONVERSATION_AWARENESS)
                val loudSoundReductionCapability =
                    capabilities.contains(Capability.LOUD_SOUND_REDUCTION)
                val adaptiveAudioCapability =
                    capabilities.contains(Capability.ADAPTIVE_AUDIO)

                val adaptiveVolumeChecked =
                    state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG]?.getOrNull(
                        0
                    ) == 0x01.toByte()
                val conversationalAwarenessChecked =
                    state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG]?.getOrNull(
                        0
                    ) == 0x01.toByte()

                AudioSettings(
                    adaptiveVolumeCapability = adaptiveVolumeCapability,
                    conversationalAwarenessCapability = conversationalAwarenessCapability,
                    loudSoundReductionCapability = loudSoundReductionCapability,
                    adaptiveAudioCapability = adaptiveAudioCapability,
                    customEqCapability = true,
                    adaptiveVolumeChecked = adaptiveVolumeChecked,
                    onAdaptiveVolumeCheckedChange = { checked ->
                        setControlCommandBoolean(
                            AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG,
                            checked
                        )
                    },
                    conversationalAwarenessChecked = conversationalAwarenessChecked,
                    onConversationalAwarenessCheckedChange = { checked ->
                        setControlCommandBoolean(
                            AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG,
                            checked
                        )
                    },
                    loudSoundReductionChecked = state.loudSoundReductionEnabled,
                    onLoudSoundReductionCheckedChange = { checked ->
                        setATTCharacteristicValue(
                            ATTHandles.LOUD_SOUND_REDUCTION,
                            byteArrayOf(if (checked) 0x01.toByte() else 0x00.toByte())
                        )
                    },
                    navigateToAdaptiveStrength = navigateToAdaptiveStrength,
                    navigateToEqualizer = navigateToEqualizer,
                    navigateToConversationSettings = navigateToConversationSettings,
                    vendorIdHook = state.vendorIdHook,
                )
            }

            item(key = "spacer_connection") { Spacer(modifier = Modifier.height(16.dp)) }
            item(key = "connection") {
                ConnectionSettings(
                    automaticEarDetectionEnabled = state.automaticEarDetectionEnabled,
                    onAutomaticEarDetectionChanged = onAutomaticEarDetectionChanged,
                    automaticConnectionEnabled = state.automaticConnectionEnabled,
                    onAutomaticConnectionChanged = onAutomaticConnectionChanged
                )
            }

            item(key = "spacer_microphone") { Spacer(modifier = Modifier.height(16.dp)) }
            item(key = "microphone") {
                val id = AACPManager.Companion.ControlCommandIdentifiers.MIC_MODE

                val selectedModeText = when (state.controlStates[id]?.getOrNull(0) ?: 0x00.toByte()) {
                    0x00.toByte() -> stringResource(R.string.microphone_automatic)
                    0x01.toByte() -> stringResource(R.string.microphone_always_right)
                    0x02.toByte() -> stringResource(R.string.microphone_always_left)
                    else -> stringResource(R.string.microphone_automatic)
                }

                StyledStandaloneListItem(
                    name = stringResource(R.string.microphone_mode),
                    description = selectedModeText,
                    onClick = navigateToMicrophoneSettings
                )
            }

            item(key = "spacer_general_controls") {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item(key = "general_controls") {
                StyledList(title = stringResource(R.string.controls_and_gestures)) {
                    if (capabilities.contains(Capability.SLEEP_DETECTION)) {
                        val id = AACPManager.Companion.ControlCommandIdentifiers.SLEEP_DETECTION_CONFIG
                        StyledToggleInGroup(
                            label = stringResource(R.string.sleep_detection),
                            checked = state.controlStates[id]?.getOrNull(0) == 0x01.toByte(),
                            onCheckedChange = { setControlCommandBoolean(id, it) }
                        )
                    }

                    if (capabilities.contains(Capability.HEAD_GESTURES)) {
                        StyledListItemInGroup(
                            name = stringResource(R.string.head_gestures),
                            description = if (sharedPreferences.getBoolean("head_gestures", false)) {
                                stringResource(R.string.on)
                            } else {
                                stringResource(R.string.off)
                            },
                            onClick = navigateToHeadTracking
                        )
                    }

                    StyledListItemInGroup(
                        name = stringResource(R.string.accessibility),
                        onClick = navigateToAccessibility
                    )

                    if (capabilities.contains(Capability.LOUD_SOUND_REDUCTION)) {
                        val id = AACPManager.Companion.ControlCommandIdentifiers.ALLOW_OFF_OPTION
                        StyledToggleInGroup(
                            label = stringResource(R.string.off_listening_mode),
                            description = stringResource(R.string.off_listening_mode_description),
                            checked = state.offListeningMode,
                            onCheckedChange = setOffListeningMode
                        )
                    }
                }
            }

            item(key = "spacer_battery_settings") {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item(key = "battery_settings") {
                StyledList(title = stringResource(R.string.battery)) {
                    StyledToggleInGroup(
                        label = stringResource(R.string.optimized_charging),
                        description = stringResource(R.string.optimized_charging_description),
                        checked = state.dynamicEndOfCharge,
                        onCheckedChange = setDynamicEndOfCharge,
                    )
                    StyledToggleInGroup(
                        label = stringResource(R.string.charging_notifications),
                        description = stringResource(R.string.charging_notifications_description),
                        checked = state.chargingNotificationsEnabled,
                        onCheckedChange = setChargingNotificationsEnabled,
                    )
                }
            }

            item(key = "spacer_about") { Spacer(modifier = Modifier.height(32.dp)) }
            item(key = "about") {
                AboutCard(
                    modelName = state.modelName,
                    actualModel = state.actualModel,
                    serialNumbers = state.serialNumbers,
                    version = state.version3,
                    navigateToVersion = navigateToVersion
                )
            }

            item(key = "spacer_disconnect") { Spacer(modifier = Modifier.height(28.dp)) }
            item(key = "disconnect_button") {
                StyledButton(
                    onClick = disconnect,
                    backdrop = rememberLayerBackdrop(),
                    isInteractive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.disconnect),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

//                item(key = "spacer_debug") { Spacer(modifier = Modifier.height(16.dp)) }
//                item(key = "debug") { StyledListItem("debug", "Debug", navController) }

            item(key = "bottom_padding") { Spacer(modifier = Modifier.height(bottomPadding)) }
        }
    } else {
        val backdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, bottom = bottomPadding),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .layerBackdrop(backdrop)
            )
            var reconnecting by remember { mutableStateOf(false) }
            var showBluetoothFallback by remember { mutableStateOf(false) }

            LaunchedEffect(reconnecting) {
                if (reconnecting) {
                    delay(5.seconds)
                    reconnecting = false
                    if (!state.connectionSuccessful && !state.isAndroidAudioProfileConnected) {
                        showBluetoothFallback = true
                    }
                }
            }
            LaunchedEffect(state.connectionSuccessful, state.isAndroidAudioProfileConnected) {
                if (state.connectionSuccessful || state.isAndroidAudioProfileConnected) {
                    reconnecting = false
                    showBluetoothFallback = false
                }
            }

            if (showBluetoothFallback) {
                BluetoothConnectionFallbackDialog(
                    backdrop = backdrop,
                    onDismiss = { showBluetoothFallback = false },
                    onOpenBluetoothSettings = {
                        showBluetoothFallback = false
                        openBluetoothSettings(context)
                    },
                )
            }

            when (LocalDesignSystem.current) {
                DesignSystem.Material -> {
                    val polygons = remember {
                        listOf(
                            MaterialShapes.Cookie9Sided,
                            MaterialShapes.Clover4Leaf,
                            MaterialShapes.SoftBurst,
                            MaterialShapes.Sunny,
                            MaterialShapes.Pentagon,
                            MaterialShapes.Cookie4Sided,
                            MaterialShapes.Oval,
                        )
                    }

                    val morphs = remember {
                        buildList {
                            for (i in polygons.indices) {
                                add(
                                    Morph(
                                        polygons[i].normalized(),
                                        polygons[(i + 1) % polygons.size].normalized()
                                    )
                                )
                            }
                        }
                    }

                    var currentMorphIndex by remember { mutableIntStateOf(0) }

                    val morphProgress = remember { Animatable(0f) }

                    LaunchedEffect(reconnecting) {
                        if (!reconnecting) {
                            currentMorphIndex = 0
                            morphProgress.snapTo(0f)
                            return@LaunchedEffect
                        }

                        while (reconnecting) {
                            morphProgress.snapTo(0f)

                            morphProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = 650,
                                    easing = FastOutSlowInEasing
                                )
                            )

                            currentMorphIndex = (currentMorphIndex + 1) % morphs.size
                        }
                    }

                    val path = remember { Path() }
                    val scaleMatrix = remember { Matrix() }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val primaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                            val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer

                            val animatedShapeColor by animateColorAsState(if (reconnecting) primaryContainerColor else secondaryContainerColor)

                            if ((state.connectionSuccessful || state.isAndroidAudioProfileConnected) && aacpControlSupported) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceBright,
                                            CircleShape
                                        )
                                        .clickable(
                                            interactionSource = null,
                                            indication = ripple(
                                                bounded = false,
                                                radius = 120.dp
                                            ),
                                            enabled = !reconnecting,
                                            onClick = {}
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    if (!reconnecting) {
                                                        currentMorphIndex = 1
                                                        reconnecting = true
                                                        reconnectFromSavedMac()
                                                    }
                                                },
                                                onPress = {
                                                    if (!reconnecting) {
                                                        morphProgress.animateTo(
                                                            targetValue = 1f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                        tryAwaitRelease()
                                                        morphProgress.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                        .drawWithContent {
                                            val activeMorph = morphs[currentMorphIndex]

                                            val shapePath = activeMorph.toPath(
                                                progress = morphProgress.value,
                                                path = path
                                            )

                                            val bounds = shapePath.getBounds()

                                            val scale = min(
                                                size.width / bounds.width,
                                                size.height / bounds.height
                                            ) * 0.8f

                                            scaleMatrix.reset()

                                            scaleMatrix.scale(x = scale, y = scale)

                                            shapePath.transform(scaleMatrix)

                                            shapePath.translate(size.center - shapePath.getBounds().center)

                                            drawPath(
                                                path = shapePath,
                                                color = animatedShapeColor
                                            )

                                            drawContent()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (reconnecting) MaterialIcons.bluetooth_searching else MaterialIcons.headset_off,
                                        contentDescription = null,
                                        modifier = Modifier.size(84.dp),
                                        tint = if (reconnecting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                Spacer(Modifier.height(40.dp))

                                Text(
                                    text = if (reconnecting) stringResource(R.string.reconnecting) else if (state.isAndroidAudioProfileConnected) stringResource(R.string.retry_airpods_control_connection) else stringResource(R.string.tap_to_reconnect),
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        if (state.isAndroidAudioProfileConnected) R.string.android_audio_connected_control_unavailable
                                        else R.string.airpods_not_connected
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                )
                                if (!hasRootlessBluetoothSupport() && !XposedState.bluetoothScopeEnabled) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.device_bluetooth_support_limited),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.minimum_android_support),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            if (!state.connectionSuccessful && !state.isAndroidAudioProfileConnected) {
                                Spacer(Modifier.height(16.dp))
                                DisconnectedAirPodsConnectControls(
                                    backdrop = backdrop,
                                    sharedPreferences = sharedPreferences,
                                    onSelectPreferredAirPods = selectPreferredAirPods,
                                    onConnect = {
                                        reconnectFromSavedMac()
                                        reconnecting = true
                                        showBluetoothFallback = false
                                    },
                                )
                                Spacer(Modifier.height(24.dp))
                                AirPodsFinderButton(backdrop, navigateToAirPodsFinder)
                            }
                        }

                        if (!BuildConfig.PLAY_BUILD) {
                            OutlinedButton(
                                onClick = navigateToTroubleshooting,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.troubleshooting
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                DesignSystem.Apple -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.isAndroidAudioProfileConnected) R.string.android_audio_connected_control_unavailable
                                    else R.string.airpods_not_connected
                                ),
                                style = MaterialTheme.typography.displaySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(
                                    if (state.isAndroidAudioProfileConnected) R.string.android_audio_connected_control_description
                                    else R.string.airpods_not_connected_description
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!hasRootlessBluetoothSupport() && !XposedState.bluetoothScopeEnabled) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.device_bluetooth_support_limited),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.minimum_android_support),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (!state.connectionSuccessful && !state.isAndroidAudioProfileConnected) {
                            Spacer(Modifier.height(18.dp))
                            DisconnectedAirPodsConnectControls(
                                backdrop = backdrop,
                                sharedPreferences = sharedPreferences,
                                onSelectPreferredAirPods = selectPreferredAirPods,
                                onConnect = {
                                    reconnectFromSavedMac()
                                    reconnecting = true
                                    showBluetoothFallback = false
                                },
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        AirPodsFinderButton(backdrop, navigateToAirPodsFinder)

                        if ((state.connectionSuccessful || state.isAndroidAudioProfileConnected) && aacpControlSupported) {
                            StyledButton(
                                onClick = { reconnectFromSavedMac(); reconnecting = true },
                                backdrop = backdrop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .widthIn(max = 200.dp),
                                enabled = !reconnecting
                            ) {
                                Text(
                                    text = stringResource(
                                        if (state.isAndroidAudioProfileConnected) R.string.retry_airpods_control_connection
                                        else R.string.reconnect_to_last_device
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (!BuildConfig.PLAY_BUILD) {
                        StyledButton(
                            onClick = navigateToTroubleshooting,
                            backdrop = backdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .widthIn(max = 200.dp),
                            materialButtonStyle = MaterialButtonStyle.Outlined,
                        ) {
                            Text(
                                text = stringResource(R.string.troubleshooting),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectedAirPodsConnectControls(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    sharedPreferences: SharedPreferences,
    onSelectPreferredAirPods: (String, String) -> Boolean,
    onConnect: () -> Unit,
) {
    val context = LocalContext.current
    var preferredAddress by remember(sharedPreferences) {
        mutableStateOf(
            sharedPreferences.getString("preferred_airpods_address", null)
                ?: sharedPreferences.getString("mac_address", "").orEmpty(),
        )
    }
    var preferredName by remember(sharedPreferences) {
        mutableStateOf(
            sharedPreferences.getString("preferred_airpods_name", null)
                ?: sharedPreferences.getString("name", "").orEmpty(),
        )
    }
    var pairedDevices by remember { mutableStateOf(emptyList<PairedAudioDeviceOption>()) }
    var showPicker by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val chooseAirPodsLabel = stringResource(R.string.choose_airpods)
    val pairedDeviceLabel = stringResource(R.string.paired_audio_device)

    LaunchedEffect(connecting) {
        if (connecting) {
            delay(5.seconds)
            connecting = false
        }
    }

    StyledList(title = stringResource(R.string.preferred_airpods_section)) {
        StyledListItemInGroup(
            name = preferredName.ifBlank { chooseAirPodsLabel },
            description = stringResource(
                if (preferredAddress.isBlank()) R.string.choose_airpods_description
                else R.string.preferred_airpods_description
            ),
            orientation = ListItemOrientation.Vertical,
            onClick = {
                pairedDevices = readPairedAudioDevices(context)
                showPicker = true
            },
            descriptionMaxLines = 2,
            leadingContent = {
                Icon(
                    imageVector = MaterialIcons.bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    StyledButton(
        onClick = {
            if (preferredAddress.isBlank()) {
                pairedDevices = readPairedAudioDevices(context)
                showPicker = true
            } else {
                connecting = true
                onConnect()
            }
        },
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        enabled = !connecting,
        materialButtonStyle = MaterialButtonStyle.Filled,
    ) {
        Text(
            text = stringResource(if (connecting) R.string.connecting_airpods else R.string.connect_airpods),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.widthIn(max = 440.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.choose_airpods),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.choose_airpods_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_paired_audio_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 340.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            StyledList {
                                pairedDevices.forEach { device ->
                                    val deviceName = device.name.ifBlank { pairedDeviceLabel }
                                    StyledListItemInGroup(
                                        name = deviceName,
                                        description = device.address,
                                        onClick = {
                                            if (onSelectPreferredAirPods(device.address, deviceName)) {
                                                preferredAddress = device.address
                                                preferredName = deviceName
                                                showPicker = false
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    R.string.select_airpods_failed,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        },
                                        selected = device.address.equals(preferredAddress, ignoreCase = true),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StyledButton(
                            onClick = { openBluetoothSettings(context) },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            materialButtonStyle = MaterialButtonStyle.Outlined,
                        ) {
                            Text(stringResource(R.string.open_bluetooth_settings))
                        }
                        StyledButton(
                            onClick = { showPicker = false },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothConnectionFallbackDialog(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onDismiss: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(
                    text = stringResource(R.string.connection_request_pending_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.connection_request_pending_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StyledButton(
                        onClick = onDismiss,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        materialButtonStyle = MaterialButtonStyle.Outlined,
                    ) {
                        Text(stringResource(R.string.close))
                    }
                    StyledButton(
                        onClick = onOpenBluetoothSettings,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        materialButtonStyle = MaterialButtonStyle.Filled,
                    ) {
                        Text(stringResource(R.string.open_bluetooth_settings))
                    }
                }
            }
        }
    }
}

private data class PairedAudioDeviceOption(val address: String, val name: String)

@SuppressLint("MissingPermission")
private fun readPairedAudioDevices(context: android.content.Context): List<PairedAudioDeviceOption> {
    return try {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            ?: return emptyList()
        adapter.bondedDevices.mapNotNull { device ->
            val name = runCatching { device.name }.getOrNull().orEmpty()
            val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
            if (name.contains("AirPods", ignoreCase = true) ||
                majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO
            ) {
                PairedAudioDeviceOption(device.address, name)
            } else null
        }.sortedBy { it.name.lowercase() }
    } catch (_: SecurityException) {
        emptyList()
    }
}

private fun openBluetoothSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }.onFailure {
        Toast.makeText(context, R.string.bluetooth_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun AirPodsFinderButton(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onClick: () -> Unit,
) {
    StyledButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .widthIn(max = 320.dp),
    ) {
        Text(
            text = stringResource(R.string.airpods_finder_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Apple")
@Composable
fun AirPodsSettingsScreenPreviewApple() {
    LibrePodsTheme(
        m3eEnabled = false
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AirPodsSettingsScreen(
                state = demoState,

                setControlCommandInt = { _, _ -> },
                setControlCommandBoolean = { _, _ -> },
                setControlCommandByte = { _, _ -> },
                setATTCharacteristicValue = { _, _ -> },

                onAutomaticEarDetectionChanged = {},
                onAutomaticConnectionChanged = {},
                setDynamicEndOfCharge = {},
                setOffListeningMode = {},
                disconnect = {},

                navigateToRename = {},
                navigateToHearingProtection = {},
                navigateToHearingAid = {},
                navigateToLeftLongPress = {},
                navigateToRightLongPress = {},
                navigateToPurchase = {},
                navigateToAdaptiveStrength = {},
                navigateToEqualizer = {},
                navigateToHeadTracking = {},
                navigateToAccessibility = {},
                navigateToVersion = {},
                navigateToTroubleshooting = {},
                navigateToCallControlScreen = {},
                navigateToMicrophoneSettings = {},

                setChargingNotificationsEnabled = {},
                reconnectFromSavedMac = {}
            )
        }
    }
}


@Preview(name = "Material")
@Composable
fun AirPodsSettingsScreenPreviewMaterial() {
    LibrePodsTheme(
        m3eEnabled = true
    ) {
        Box (
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AirPodsSettingsScreen(
                state = demoState,

                setControlCommandInt = { _, _ -> },
                setControlCommandBoolean = { _, _ -> },
                setControlCommandByte = { _, _ -> },
                setATTCharacteristicValue = { _, _ -> },

                onAutomaticEarDetectionChanged = {},
                onAutomaticConnectionChanged = {},
                setDynamicEndOfCharge = {},
                setOffListeningMode = {},
                disconnect = {},

                navigateToRename = {},
                navigateToHearingProtection = {},
                navigateToHearingAid = {},
                navigateToLeftLongPress = {},
                navigateToRightLongPress = {},
                navigateToPurchase = {},
                navigateToAdaptiveStrength = {},
                navigateToEqualizer = {},
                navigateToHeadTracking = {},
                navigateToAccessibility = {},
                navigateToVersion = {},
                navigateToTroubleshooting = {},
                navigateToCallControlScreen = {},
                navigateToMicrophoneSettings = {},

                setChargingNotificationsEnabled = {},
                reconnectFromSavedMac = {}
            )
        }
    }
}
