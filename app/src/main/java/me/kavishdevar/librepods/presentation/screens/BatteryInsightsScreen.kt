package me.kavishdevar.librepods.presentation.screens

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBatterySample
import me.kavishdevar.librepods.data.AirPodsBatteryHistoryStore
import me.kavishdevar.librepods.data.AirPodsRuntimeEstimate
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.components.StyledBottomSheet
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.BatteryIndicator
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem as StyledListItemInGroup
import me.kavishdevar.librepods.presentation.components.StyledListScope
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import kotlin.math.roundToInt

private const val HISTORY_RANGE_MS = 7L * 24 * 60 * 60 * 1000

@Composable
fun BatteryInsightsScreen(viewModel: AirPodsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val preferences = remember(context) { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val deviceAddress = remember(preferences) {
        preferences.getString("airpods_model_address", null)
            ?: preferences.getString("mac_address", null)
    }
    val modelNumber = remember(preferences) { preferences.getString("airpods_model_number", null) }
    val backdrop = rememberLayerBackdrop()
    val isMaterial = LocalDesignSystem.current == DesignSystem.Material
    var samples by remember(context, deviceAddress, modelNumber) {
        mutableStateOf(AirPodsBatteryHistoryStore.load(context, deviceAddress, modelNumber))
    }
    var showClearSheet by remember { mutableStateOf(false) }
    var nowElapsedMillis by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    fun refreshHistory() {
        samples = AirPodsBatteryHistoryStore.load(context, deviceAddress, modelNumber)
        nowElapsedMillis = SystemClock.elapsedRealtime()
        nowMillis = System.currentTimeMillis()
    }

    LaunchedEffect(state.battery) { refreshHistory() }
    DisposableEffect(lifecycleOwner, deviceAddress, modelNumber) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshHistory()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val topPadding = if (isMaterial) 16.dp else
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    val currentBatteries = state.battery.associateBy { it.component }
    val leftBattery = currentBatteries[BatteryComponent.LEFT]
    val rightBattery = currentBatteries[BatteryComponent.RIGHT]
    val caseBattery = currentBatteries[BatteryComponent.CASE]
    val leftEstimate = estimateForCurrentBattery(
        leftBattery, BatteryComponent.LEFT, samples, state.isLocallyConnected, nowElapsedMillis, nowMillis,
    )
    val rightEstimate = estimateForCurrentBattery(
        rightBattery, BatteryComponent.RIGHT, samples, state.isLocallyConnected, nowElapsedMillis, nowMillis,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .layerBackdrop(backdrop),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            Text(
                text = stringResource(R.string.battery_insights_intro),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StyledList(title = stringResource(R.string.battery_insights_current)) {
                BatterySummaryRow(
                    label = stringResource(R.string.battery_insights_left),
                    battery = leftBattery,
                    estimate = leftEstimate,
                    connected = state.isLocallyConnected,
                    nowElapsedMillis = nowElapsedMillis,
                )
                BatterySummaryRow(
                    label = stringResource(R.string.battery_insights_right),
                    battery = rightBattery,
                    estimate = rightEstimate,
                    connected = state.isLocallyConnected,
                    nowElapsedMillis = nowElapsedMillis,
                )
                BatterySummaryRow(
                    label = stringResource(R.string.battery_insights_case),
                    battery = caseBattery,
                    estimate = null,
                    connected = state.isLocallyConnected,
                    nowElapsedMillis = nowElapsedMillis,
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.battery_insights_history),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMaterial) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            BatteryHistoryChart(
                samples = samples,
                startTimeMillis = nowMillis - HISTORY_RANGE_MS,
                endTimeMillis = nowMillis,
                isMaterial = isMaterial,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.battery_insights_health_note_short),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (samples.isNotEmpty()) {
                StyledList(
                    title = stringResource(R.string.battery_insights_manage),
                    description = stringResource(R.string.battery_insights_local_note),
                ) {
                    StyledListItemInGroup(
                        name = stringResource(R.string.battery_insights_clear),
                        onClick = { showClearSheet = true },
                    )
                }
            }
            Spacer(Modifier.height(bottomPadding))
        }
    }

    StyledBottomSheet(
        visible = showClearSheet,
        onDismiss = { showClearSheet = false },
        backdrop = backdrop,
    ) { innerBackdrop, _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.battery_insights_clear_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.battery_insights_clear_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StyledButton(
                    onClick = { showClearSheet = false },
                    backdrop = innerBackdrop,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.battery_insights_cancel))
                }
                StyledButton(
                    onClick = {
                        AirPodsBatteryHistoryStore.clear(context, deviceAddress, modelNumber)
                        samples = emptyList()
                        showClearSheet = false
                    },
                    backdrop = innerBackdrop,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.battery_insights_clear))
                }
            }
        }
    }
}

@Composable
private fun StyledListScope.BatterySummaryRow(
    label: String,
    battery: Battery?,
    estimate: AirPodsRuntimeEstimate?,
    connected: Boolean,
    nowElapsedMillis: Long,
) {
    val freshLevel = battery != null && battery.isLevelKnown && battery.level in 0..100 &&
        battery.levelCapturedAtElapsedMs > 0L &&
        nowElapsedMillis - battery.levelCapturedAtElapsedMs in 0L..Battery.LEVEL_FRESHNESS_TTL_MS
    val freshStatus = battery != null && battery.chargingCapturedAtElapsedMs > 0L &&
        nowElapsedMillis - battery.chargingCapturedAtElapsedMs in 0L..Battery.CHARGING_FRESHNESS_TTL_MS
    val statusLabel = when {
        battery == null -> R.string.battery_insights_unknown
        !freshLevel -> R.string.battery_insights_stale
        !freshStatus -> R.string.battery_insights_status_unknown
        battery.status == BatteryStatus.CHARGING -> R.string.battery_insights_charging
        battery.status == BatteryStatus.OPTIMIZED_CHARGING -> R.string.battery_insights_optimized_charging
        battery.status == BatteryStatus.NOT_CHARGING -> R.string.battery_insights_not_charging
        else -> R.string.battery_insights_status_unknown
    }
    val description = when {
        !freshLevel -> stringResource(statusLabel)
        !connected -> stringResource(R.string.battery_insights_last_seen)
        !freshStatus -> stringResource(statusLabel)
        battery.status == BatteryStatus.CHARGING || battery.status == BatteryStatus.OPTIMIZED_CHARGING ->
            stringResource(statusLabel)
        battery.status == BatteryStatus.NOT_CHARGING && estimate != null ->
            stringResource(
                R.string.battery_insights_inline_runtime,
                formatRuntime(estimate.minutesRemaining),
            )
        battery.status == BatteryStatus.NOT_CHARGING && battery.component != BatteryComponent.CASE ->
            stringResource(R.string.battery_insights_runtime_collecting)
        else -> stringResource(statusLabel)
    }
    StyledListItemInGroup(
        name = label,
        description = description,
        onClick = null,
        enabled = false,
        leadingContent = {
            BatteryIndicator(
                batteryPercentage = battery?.level?.takeIf { freshLevel },
                status = battery?.status?.takeIf { freshStatus } ?: BatteryStatus.DISCONNECTED,
                backgroundColor = MaterialTheme.colorScheme.surface,
            )
        },
    )
}

private fun estimateForCurrentBattery(
    battery: Battery?,
    component: Int,
    samples: List<AirPodsBatterySample>,
    connected: Boolean,
    nowElapsedMillis: Long,
    nowMillis: Long,
): AirPodsRuntimeEstimate? {
    if (!connected || battery?.status != BatteryStatus.NOT_CHARGING ||
        !battery.isLevelKnown || battery.level !in 1..100 ||
        battery.chargingCapturedAtElapsedMs <= 0L ||
        nowElapsedMillis - battery.chargingCapturedAtElapsedMs !in 0L..Battery.CHARGING_FRESHNESS_TTL_MS ||
        nowElapsedMillis - battery.levelCapturedAtElapsedMs !in 0L..Battery.LEVEL_FRESHNESS_TTL_MS
    ) return null
    return AirPodsBatteryHistoryStore.estimateRemainingMinutes(samples, component, battery.level, nowMillis)
}

@Composable
private fun formatRuntime(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.battery_insights_hours_minutes, hours, minutes)
    else stringResource(R.string.battery_insights_minutes, minutes)
}

@Composable
private fun BatteryHistoryChart(
    samples: List<AirPodsBatterySample>,
    startTimeMillis: Long,
    endTimeMillis: Long,
    isMaterial: Boolean,
) {
    val leftColor = MaterialTheme.colorScheme.primary
    val rightColor = MaterialTheme.colorScheme.tertiary
    val caseColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val visibleSamples = samples.filter {
        it.recordedAtMillis in startTimeMillis..endTimeMillis
    }
    val hasChartData = visibleSamples.map { it.component }.distinct().any { component ->
        visibleSamples.count { it.component == component } >= 2
    }

    Surface(
        color = if (isMaterial) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(if (isMaterial) 24.dp else 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(color = leftColor, label = stringResource(R.string.battery_insights_legend_left))
                LegendDot(color = rightColor, label = stringResource(R.string.battery_insights_legend_right))
                LegendDot(color = caseColor, label = stringResource(R.string.battery_insights_legend_case))
            }
            Spacer(Modifier.height(14.dp))
            if (hasChartData) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                        .height(156.dp)
                            .width(38.dp)
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        listOf("100%", "50%", "0%").forEach { value ->
                            Text(
                                text = value,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(156.dp),
                    ) {
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 4.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    val plotWidth = right - left
                    val plotHeight = bottom - top

                    for (step in 0..4) {
                        val y = top + plotHeight * step / 4f
                        drawLine(
                            color = gridColor,
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    listOf(
                        BatteryComponent.LEFT to leftColor,
                        BatteryComponent.RIGHT to rightColor,
                        BatteryComponent.CASE to caseColor,
                    ).forEach { (component, color) ->
                        val series = visibleSamples
                            .filter { it.component == component }
                            .sortedBy { it.recordedAtMillis }
                        if (series.size < 2) return@forEach
                        val path = Path()
                        series.forEachIndexed { index, sample ->
                            val x = left + plotWidth *
                                ((sample.recordedAtMillis - startTimeMillis).toFloat() /
                                    (endTimeMillis - startTimeMillis).coerceAtLeast(1L))
                            val y = top + plotHeight * (1f - sample.level / 100f)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                        series.forEach { sample ->
                            val x = left + plotWidth *
                                ((sample.recordedAtMillis - startTimeMillis).toFloat() /
                                    (endTimeMillis - startTimeMillis).coerceAtLeast(1L))
                            val y = top + plotHeight * (1f - sample.level / 100f)
                            drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.battery_insights_7days_ago),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.battery_insights_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.battery_insights_history_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(color, CircleShape)
                .size(8.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
