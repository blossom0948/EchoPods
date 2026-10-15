package com.galaxyairpods.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxyairpods.domain.model.AirPodsState
import com.galaxyairpods.domain.model.BatterySlot
import com.galaxyairpods.domain.model.DataConfidence
import com.galaxyairpods.domain.model.isMax
import kotlinx.coroutines.delay

@Composable
fun BatteryGrid(
    state: AirPodsState,
    modifier: Modifier = Modifier,
    reveal: Boolean = true,
    staggerMs: Long = 35L,
    reducedMotion: Boolean = false,
    dashboardStyle: Boolean = false,
    inlineStyle: Boolean = false,
    librePodsStyle: Boolean = false,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.model.isMax) {
            BatteryItem(
                slot = BatterySlot.LEFT,
                labelOverride = "헤드폰",
                battery = state.leftBattery,
                charging = state.chargingFor(BatterySlot.LEFT, now),
                chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.LEFT, now),
                confidence = state.confidence,
                reveal = reveal,
                revealDelayMs = 0L,
                reducedMotion = reducedMotion,
                dashboardStyle = dashboardStyle,
                inlineStyle = inlineStyle,
                librePodsStyle = librePodsStyle,
                modifier = Modifier.weight(1f),
            )
        } else if (librePodsStyle) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BatteryItem(
                    slot = BatterySlot.LEFT,
                    battery = state.leftBattery,
                    charging = state.chargingFor(BatterySlot.LEFT, now),
                    chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.LEFT, now),
                    confidence = state.confidence,
                    reveal = reveal,
                    revealDelayMs = 0L,
                    reducedMotion = reducedMotion,
                    librePodsStyle = true,
                    modifier = Modifier.weight(1f),
                )
                BatteryItem(
                    slot = BatterySlot.RIGHT,
                    battery = state.rightBattery,
                    charging = state.chargingFor(BatterySlot.RIGHT, now),
                    chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.RIGHT, now),
                    confidence = state.confidence,
                    reveal = reveal,
                    revealDelayMs = staggerMs,
                    reducedMotion = reducedMotion,
                    librePodsStyle = true,
                    modifier = Modifier.weight(1f),
                )
            }
            BatteryItem(
                slot = BatterySlot.CASE,
                battery = state.caseBattery,
                charging = state.chargingFor(BatterySlot.CASE, now),
                chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.CASE, now),
                confidence = state.confidence,
                reveal = reveal,
                revealDelayMs = staggerMs * 2,
                reducedMotion = reducedMotion,
                librePodsStyle = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            BatteryItem(
                slot = BatterySlot.LEFT,
                battery = state.leftBattery,
                charging = state.chargingFor(BatterySlot.LEFT, now),
                chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.LEFT, now),
                confidence = state.confidence,
                reveal = reveal,
                revealDelayMs = 0L,
                reducedMotion = reducedMotion,
                dashboardStyle = dashboardStyle,
                inlineStyle = inlineStyle,
                librePodsStyle = librePodsStyle,
                modifier = Modifier.weight(1f),
            )
            BatteryItem(
                slot = BatterySlot.RIGHT,
                battery = state.rightBattery,
                charging = state.chargingFor(BatterySlot.RIGHT, now),
                chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.RIGHT, now),
                confidence = state.confidence,
                reveal = reveal,
                revealDelayMs = staggerMs,
                reducedMotion = reducedMotion,
                dashboardStyle = dashboardStyle,
                inlineStyle = inlineStyle,
                librePodsStyle = librePodsStyle,
                modifier = Modifier.weight(1f),
            )
            BatteryItem(
                slot = BatterySlot.CASE,
                battery = state.caseBattery,
                charging = state.chargingFor(BatterySlot.CASE, now),
                chargingUnknown = state.chargingStatusUnknownFor(BatterySlot.CASE, now),
                confidence = state.confidence,
                reveal = reveal,
                revealDelayMs = staggerMs * 2,
                reducedMotion = reducedMotion,
                dashboardStyle = dashboardStyle,
                inlineStyle = inlineStyle,
                librePodsStyle = librePodsStyle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun BatteryItem(
    slot: BatterySlot,
    battery: Int?,
    charging: Boolean?,
    chargingUnknown: Boolean = false,
    confidence: DataConfidence,
    reveal: Boolean,
    revealDelayMs: Long,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    dashboardStyle: Boolean = false,
    inlineStyle: Boolean = false,
    librePodsStyle: Boolean = false,
) {
    var itemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(reveal, revealDelayMs, reducedMotion) {
        if (reveal) {
            if (!reducedMotion) delay(revealDelayMs)
            itemVisible = true
        } else {
            itemVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (itemVisible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(160),
        label = "${slot.name}-alpha",
    )
    val progress by animateFloatAsState(
        targetValue = (battery ?: 0).coerceIn(0, 100) / 100f,
        animationSpec = if (reducedMotion) snap() else tween(520),
        label = "${slot.name}-ring",
    )
    val accent = when {
        battery == null -> MaterialTheme.colorScheme.onSurfaceVariant
        battery <= 20 -> Color(0xFFFFB4AB)
        else -> MaterialTheme.colorScheme.tertiary
    }

    Surface(
        modifier = modifier
            .then(if (librePodsStyle) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer { this.alpha = alpha }
            .semantics {
                contentDescription = (labelOverride ?: slot.label) + " 배터리 " +
                    (battery?.let { it.toString() + " 퍼센트" } ?: "확인 불가")
            },
        shape = MaterialTheme.shapes.large,
        color = if (dashboardStyle || inlineStyle || librePodsStyle) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (librePodsStyle) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 1.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BatterySlotBadge(slot = slot)
                Spacer(Modifier.size(5.dp))
                Crossfade(
                    targetState = battery?.let { "$it%" } ?: "--",
                    animationSpec = if (reducedMotion || battery == null) snap() else tween(210),
                    label = "${slot.name}-value-libre",
                ) { valueLabel ->
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                }
                if (charging == true) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "충전 중",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 2.dp).size(16.dp),
                    )
                }
            }
        } else Column(
            modifier = Modifier.padding(
                vertical = if (dashboardStyle || inlineStyle) 2.dp else 12.dp,
                horizontal = if (dashboardStyle || inlineStyle) 2.dp else 8.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (inlineStyle) {
                Text(
                    labelOverride ?: slot.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (charging == true) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "충전 중",
                            tint = accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Crossfade(
                        targetState = battery?.let { "$it%" } ?: "--",
                        animationSpec = if (reducedMotion || battery == null) snap() else tween(210),
                        label = "${slot.name}-value-inline",
                    ) { valueLabel ->
                        Text(
                            text = valueLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        )
                    }
                }
                if (chargingUnknown && battery != null) {
                    Text("충전 확인 중", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    BatteryRing(
                        progress = battery?.let { progress },
                        accent = accent,
                        modifier = Modifier.size(if (dashboardStyle) 38.dp else 40.dp),
                        strokeWidth = if (dashboardStyle) 3.dp else 5.dp,
                    )
                    if (charging == true && dashboardStyle) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "충전 중",
                            tint = accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(if (dashboardStyle) 3.dp else 5.dp))
                if (dashboardStyle) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            labelOverride ?: slot.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            maxLines = 1,
                        )
                        Crossfade(
                            targetState = battery?.let { "$it%" } ?: "--",
                            animationSpec = if (reducedMotion || battery == null) snap() else tween(210),
                            label = "${slot.name}-value",
                        ) { valueLabel ->
                            Text(
                                text = valueLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            )
                        }
                    }
                } else {
                    Text(labelOverride ?: slot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                    Crossfade(
                        targetState = battery?.let { "$it%" } ?: "--",
                        animationSpec = if (reducedMotion || battery == null) snap() else tween(210),
                        label = "${slot.name}-value",
                    ) { valueLabel ->
                        Text(
                            text = valueLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        )
                    }
                    if (charging == true) {
                        Text("충전 중", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    } else if (chargingUnknown && battery != null) {
                        Text("충전 상태 확인 중", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                    } else if (battery == null) {
                        Text("데이터 없음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatterySlotBadge(slot: BatterySlot) {
    val foreground = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    when (slot) {
        BatterySlot.LEFT, BatterySlot.RIGHT -> Surface(
            modifier = Modifier.size(17.dp),
            shape = CircleShape,
            color = background,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (slot == BatterySlot.LEFT) "L" else "R",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
        BatterySlot.CASE -> Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = foreground,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(stroke),
            )
            drawLine(
                color = foreground,
                start = Offset(size.width * 0.12f, size.height * 0.39f),
                end = Offset(size.width * 0.88f, size.height * 0.39f),
                strokeWidth = stroke,
            )
            drawRoundRect(
                color = foreground,
                topLeft = Offset(size.width * 0.42f, size.height * 0.53f),
                size = Size(size.width * 0.16f, stroke),
                cornerRadius = CornerRadius(stroke, stroke),
            )
        }
    }
}

@Composable
fun BatteryRing(
    progress: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 5.dp,
) {
    Canvas(modifier) {
        drawArc(
            color = accent.copy(alpha = 0.18f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx()),
        )
        // Unknown is intentionally not drawn as an empty 0% ring. A short
        // neutral dash keeps the state visibly different from a real 0%.
        if (progress == null) {
            drawArc(
                color = accent.copy(alpha = 0.55f),
                startAngle = -90f,
                sweepAngle = 72f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx()),
            )
        } else {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx()),
            )
        }
    }
}
