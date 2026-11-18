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

package me.kavishdevar.librepods.presentation.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.data.parseTransparencySettingsResponse
import me.kavishdevar.librepods.data.sendTransparencySettings
import me.kavishdevar.librepods.presentation.components.ConfirmationDialog
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "AccessibilitySettings"

@SuppressLint("DefaultLocale")
@ExperimentalHazeMaterialsApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalEncodingApi::class)
@Composable
fun HearingAidScreen(viewModel: AirPodsViewModel, onNavigateHearingAidAdjustments: () -> Unit, onNavigateHearingTest: () -> Unit) {
    val verticalScrollState  = rememberScrollState()
    val backdrop = rememberLayerBackdrop()

    val showDialog = remember { mutableStateOf(false) }
    val initialLoad = remember { mutableStateOf(true) }

    val state by viewModel.uiState.collectAsState()

    val hearingAidEnabled = remember {
        val aidStatus = state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID]
        val assistStatus = state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG]
        mutableStateOf((aidStatus?.getOrNull(1) == 0x01.toByte()) && (assistStatus?.getOrNull(0) == 0x01.toByte()))
    }


    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (m3eEnabled) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (m3eEnabled) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    Column(
        modifier = Modifier
            .layerBackdrop(backdrop)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(verticalScrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        LaunchedEffect(hearingAidEnabled.value) {
            if (hearingAidEnabled.value && !initialLoad.value) {
                showDialog.value = true
            } else if (!hearingAidEnabled.value && !initialLoad.value) {
                viewModel.setControlCommandValue(AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID, byteArrayOf(0x01, 0x02))
                viewModel.setControlCommandByte(AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG, 0x02.toByte())
                hearingAidEnabled.value = false
            }
            initialLoad.value = false
        }

        StyledList (title = stringResource(R.string.hearing_aid)) {
            StyledToggle(
                label = stringResource(R.string.hearing_aid),
                checked = hearingAidEnabled.value,
                onCheckedChange = { hearingAidEnabled.value = it },
            )
            StyledListItem(
                name = stringResource(R.string.adjustments),
                onClick = onNavigateHearingAidAdjustments,
            )
        }

        Text(
            text = stringResource(R.string.hearing_aid_description),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = (if (isSystemInDarkTheme()) Color.White else Color.Black).copy(alpha = 0.6f),
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        StyledListItem(
            name = stringResource(R.string.update_hearing_test),
            onClick = onNavigateHearingTest,
        )

        Spacer(modifier = Modifier.height(8.dp))

        StyledList(title = stringResource(R.string.media_assist)) {
            StyledToggle(
                label = stringResource(R.string.media_assist),
                description = stringResource(R.string.media_assist_description),
                checked = state.eqOnPhone || state.eqOnMedia,
                enabled = state.eqProfileAvailable,
                onCheckedChange = { enabled ->
                    viewModel.setPhoneMediaEq(phoneEnabled = enabled, mediaEnabled = enabled)
                },
            )
            if (state.eqProfileAvailable) {
                StyledToggle(
                    label = stringResource(R.string.adjust_media),
                    checked = state.eqOnMedia,
                    onCheckedChange = { enabled ->
                        viewModel.setPhoneMediaEq(phoneEnabled = state.eqOnPhone, mediaEnabled = enabled)
                    },
                )
                StyledToggle(
                    label = stringResource(R.string.adjust_calls),
                    checked = state.eqOnPhone,
                    onCheckedChange = { enabled ->
                        viewModel.setPhoneMediaEq(phoneEnabled = enabled, mediaEnabled = state.eqOnMedia)
                    },
                )
            } else {
                StyledListItem(
                    name = stringResource(R.string.hearing_eq_waiting),
                    description = stringResource(R.string.hearing_eq_waiting_description),
                    onClick = null,
                    enabled = false,
                )
            }
        }
        Spacer(modifier = Modifier.height(bottomPadding))
    }

    ConfirmationDialog(
        showDialog = showDialog,
        title = stringResource(R.string.enable_hearing_aid_title),
        message = stringResource(R.string.enable_hearing_aid_message),
        confirmText = stringResource(R.string.enable_label),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDialog.value = false
            val enrolled = state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID]?.getOrNull(0) == 0x01.toByte()
            if (!enrolled) {
                viewModel.setControlCommandValue(AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID, byteArrayOf(0x01, 0x01))
            } else {
                viewModel.setControlCommandValue(AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID, byteArrayOf(0x01, 0x01))
            }
            viewModel.setControlCommandByte(AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG, 0x01.toByte())
            hearingAidEnabled.value = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (state.hearingAidData.isEmpty()) {
                        Log.w(TAG, "read failed")
                        return@launch
                    }
                    val parsed = parseTransparencySettingsResponse(state.hearingAidData)
                    if (parsed == null) {
                        Log.w(TAG, "transparency parse failed")
                        return@launch
                    }
                    val disabledSettings = parsed.copy(enabled = false)
                    sendTransparencySettings(viewModel::setATTCharacteristicValue, disabledSettings)
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling transparency: ${e.message}")
                }
            }
        },
        onDismiss = {
            hearingAidEnabled.value = false
            showDialog.value = false
        },
         backdrop = backdrop
    )
}
