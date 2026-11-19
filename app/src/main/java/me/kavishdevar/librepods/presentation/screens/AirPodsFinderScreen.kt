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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.BLEManager
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel

private const val SIGNAL_STALE_MS = 3_500L

@Composable
fun AirPodsFinderScreen(viewModel: AirPodsViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val isMaterial = LocalDesignSystem.current == DesignSystem.Material
    val backdrop = rememberLayerBackdrop()
    val topPadding = if (isMaterial) 16.dp else
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 78.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp

    var signal by remember { mutableStateOf<BLEManager.SignalReading?>(null) }
    var diagnostics by remember { mutableStateOf<BLEManager.FinderScanDiagnostics?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastRecordedSample by remember { mutableStateOf<Long?>(null) }
    var smoothedRssi by remember { mutableFloatStateOf(Float.NaN) }
    var signalTrend by remember { mutableIntStateOf(0) }
    var hasFinderPermissions by remember {
        mutableStateOf(
            hasFinderPermissions(context),
        )
    }
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasFinderPermissions = grants[Manifest.permission.BLUETOOTH_SCAN] == true &&
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true &&
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    DisposableEffect(hasFinderPermissions, viewModel) {
        if (hasFinderPermissions) {
            viewModel.setNearbyAirPodsFinderActive(true)
        }
        onDispose { viewModel.setNearbyAirPodsFinderActive(false) }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val observed = viewModel.getNearbyAirPodsSignal()
            signal = observed
            diagnostics = viewModel.getAirPodsFinderDiagnostics()
            now = System.currentTimeMillis()
            if (observed == null || now - observed.lastSeen > SIGNAL_STALE_MS) {
                lastRecordedSample = null
                smoothedRssi = Float.NaN
                signalTrend = 0
            } else if (observed.lastSeen != lastRecordedSample) {
                val prior = smoothedRssi
                val next = if (prior.isNaN()) observed.rssi.toFloat()
                    else prior * 0.72f + observed.rssi * 0.28f
                if (!prior.isNaN()) {
                    val delta = observed.rssi - prior
                    if (delta >= 4f) signalTrend = 1
                    else if (delta <= -4f) signalTrend = -1
                }
                smoothedRssi = next
                lastRecordedSample = observed.lastSeen
            }
            delay(350)
        }
    }

    val freshSignal = signal?.takeIf { now - it.lastSeen <= SIGNAL_STALE_MS }
    val noSignalMessage = when {
        !hasFinderPermissions -> R.string.airpods_finder_scan_permission
        diagnostics?.bluetoothEnabled == false -> R.string.airpods_finder_bluetooth_off
        diagnostics?.locationServicesEnabled == false -> R.string.airpods_finder_location_disabled
        diagnostics?.scanFailureCode == -1003 -> R.string.airpods_finder_scanner_unavailable
        diagnostics?.scanFailureCode != null -> R.string.airpods_finder_scan_failed
        diagnostics?.scannerRunning == false -> R.string.airpods_finder_scanner_unavailable
        diagnostics?.selectedIdentityConfigured == false -> R.string.airpods_finder_select_identity
        diagnostics?.nearbyAirPodsAdvertisementSeen == true &&
            diagnostics?.identityMismatchSeen == true &&
            diagnostics?.identityResolutionKeyAvailable == false -> R.string.airpods_finder_identity_key_missing
        diagnostics?.nearbyAirPodsAdvertisementSeen == true &&
            diagnostics?.identityMismatchSeen == true -> R.string.airpods_finder_identity_not_verified
        else -> R.string.airpods_finder_waiting_body
    }
    val signalStrength = freshSignal?.let { ((it.rssi + 100f) / 55f).coerceIn(0f, 1f) } ?: 0f
    val animatedSignalStrength by animateFloatAsState(
        targetValue = signalStrength,
        animationSpec = tween(280),
        label = "airpodsFinderSignal",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .layerBackdrop(backdrop)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(topPadding))
            if (freshSignal != null) {
                Text(
                    text = freshSignal.model,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = if (freshSignal == null) {
                    stringResource(R.string.airpods_finder_intro)
                } else if (state.isLocallyConnected) {
                    stringResource(R.string.airpods_finder_connected_body)
                } else {
                    stringResource(R.string.airpods_finder_direction)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                maxLines = 2,
            )

            Spacer(Modifier.height(12.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                FinderSignalGraphic(
                    strength = animatedSignalStrength,
                    showSignalPoint = freshSignal != null,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (freshSignal == null) {
                        Text(
                            text = stringResource(R.string.airpods_finder_waiting_title),
                            style = if (isMaterial) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.airpods_finder_signal_value,
                                freshSignal.rssi,
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.airpods_finder_signal),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (freshSignal != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                        Text(
                            text = stringResource(R.string.airpods_finder_distance),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                signalTrend > 0 -> stringResource(R.string.airpods_finder_signal_stronger)
                                signalTrend < 0 -> stringResource(R.string.airpods_finder_signal_weaker)
                                else -> stringResource(R.string.airpods_finder_signal_stable)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = when {
                            noSignalMessage == R.string.airpods_finder_waiting_body ->
                                stringResource(R.string.airpods_finder_no_signal)
                            noSignalMessage == R.string.airpods_finder_scan_failed ->
                                stringResource(noSignalMessage, diagnostics?.scanFailureCode ?: 0)
                            else -> stringResource(noSignalMessage)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        maxLines = 3,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (freshSignal != null) {
                Text(
                    text = stringResource(R.string.airpods_finder_signal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(12.dp))
            StyledButton(
                onClick = {
                    lastRecordedSample = null
                    smoothedRssi = Float.NaN
                    signalTrend = 0
                    if (hasFinderPermissions) {
                        viewModel.refreshNearbyAirPodsScan()
                    } else {
                        scanPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                maxScale = 0.05f,
                surfaceColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(
                        if (hasFinderPermissions) R.string.airpods_finder_search_again
                        else R.string.airpods_finder_scan_permission_action,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            if (diagnostics?.locationServicesEnabled == false && hasFinderPermissions) {
                Spacer(Modifier.height(10.dp))
                StyledButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    maxScale = 0.05f,
                    surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = stringResource(R.string.airpods_finder_open_location_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(bottomPadding))
        }
    }
}

private fun hasFinderPermissions(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun FinderSignalGraphic(
    strength: Float,
    showSignalPoint: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val ringStrength = strength.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.44f
        drawCircle(color = accent.copy(alpha = 0.045f + ringStrength * 0.08f), radius = radius, center = center)
        drawCircle(color = track, radius = radius * 0.94f, center = center, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = track.copy(alpha = 0.75f), radius = radius * 0.70f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = track.copy(alpha = 0.55f), radius = radius * 0.46f, center = center, style = Stroke(width = 1.dp.toPx()))
        if (ringStrength > 0f) {
            drawArc(
                color = accent.copy(alpha = 0.35f + ringStrength * 0.65f),
                startAngle = -90f,
                sweepAngle = 360f * ringStrength,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.94f, center.y - radius * 0.94f),
                size = androidx.compose.ui.geometry.Size(radius * 1.88f, radius * 1.88f),
                style = Stroke(width = 5.dp.toPx()),
            )
        }
        if (showSignalPoint) {
            drawCircle(
                color = accent.copy(alpha = 0.92f),
                radius = 8.dp.toPx(),
                center = center,
            )
        }
    }
}
