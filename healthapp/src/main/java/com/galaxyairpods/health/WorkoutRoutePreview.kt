package com.galaxyairpods.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkoutRoutePreview(
    route: List<WorkoutLocationSample>,
    modifier: Modifier = Modifier,
) {
    if (route.size < 2) return
    val shape = RoundedCornerShape(20.dp)
    val line = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val surface = MaterialTheme.colorScheme.surface
    val start = ColorRouteStart
    Box(
        modifier.fillMaxWidth().height(152.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
    ) {
        Canvas(Modifier.fillMaxWidth().height(152.dp)) {
            val usable = route.takeLast(1_000)
            val minLat = usable.minOf { it.latitude }
            val maxLat = usable.maxOf { it.latitude }
            val minLon = usable.minOf { it.longitude }
            val maxLon = usable.maxOf { it.longitude }
            val latSpan = (maxLat - minLat).coerceAtLeast(0.00002)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.00002)
            val inset = 20.dp.toPx()
            val mapWidth = size.width - inset * 2
            val mapHeight = size.height - inset * 2
            val scale = minOf(mapWidth / lonSpan.toFloat(), mapHeight / latSpan.toFloat())
            val drawnWidth = lonSpan.toFloat() * scale
            val drawnHeight = latSpan.toFloat() * scale
            val offsetX = (size.width - drawnWidth) / 2f
            val offsetY = (size.height - drawnHeight) / 2f
            val points = usable.map { sample ->
                Offset(
                    x = offsetX + ((sample.longitude - minLon).toFloat() * scale),
                    y = size.height - offsetY - ((sample.latitude - minLat).toFloat() * scale),
                )
            }
            // Subtle map-like guide lines keep the route readable without remote map tiles.
            for (index in 1..3) {
                val x = size.width * index / 4f
                val y = size.height * index / 4f
                drawLine(track, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawLine(track, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val path = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(path, line.copy(alpha = 0.20f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, line, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(surface, radius = 7.dp.toPx(), center = points.first())
            drawCircle(start, radius = 5.dp.toPx(), center = points.first())
            drawCircle(surface, radius = 7.dp.toPx(), center = points.last())
            drawCircle(line, radius = 5.dp.toPx(), center = points.last())
        }
    }
}

private val ColorRouteStart = androidx.compose.ui.graphics.Color(0xFF35C759)
