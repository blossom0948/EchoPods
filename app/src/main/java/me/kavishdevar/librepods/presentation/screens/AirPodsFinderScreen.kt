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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val state by viewModel.uiState.collectAsState()
    val isMaterial = LocalDesignSystem.current == DesignSystem.Material
    val backdrop = rememberLayerBackdrop()
    val topPadding = if (isMaterial) 16.dp else
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 78.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp

    var signal by remember { mutableStateOf<BLEManager.SignalReading?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastRecordedSample by remember { mutableStateOf<Long?>(null) }
    var smoothedRssi by remember { mutableFloatStateOf(Float.NaN) }
    var signalTrend by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val observed = viewModel.getNearbyAirPodsSignal()
            signal = observed
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
                    stringResource(R.string.airpods_finder_waiting_body)
                } else if (state.isLocallyConnected) {
                    stringResource(R.string.airpods_finder_connected_body)
                } else {
                    stringResource(R.string.airpods_finder_direction)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(264.dp)) {
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
                            modifier = Modifier.padding(horizontal = 24.dp),
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
                        text = stringResource(R.string.airpods_finder_no_signal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = stringResource(R.string.airpods_finder_rotate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.airpods_finder_limitations),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            StyledButton(
                onClick = {
                    lastRecordedSample = null
                    smoothedRssi = Float.NaN
                    signalTrend = 0
                },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                maxScale = 0.05f,
                surfaceColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.airpods_finder_search_again),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.height(bottomPadding))
        }
    }
}

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
