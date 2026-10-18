package me.kavishdevar.librepods.presentation.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import kotlin.math.min
import kotlin.math.roundToInt

internal data class BatteryWidgetRingStyle(
    val sweepDegrees: Float,
    val progressColor: Int?,
)

internal object BatteryWidgetRingPolicy {
    const val NORMAL_GREEN: Int = 0xFF30D158.toInt()
    const val LOW_RED: Int = 0xFFFF453A.toInt()

    fun style(level: Int?): BatteryWidgetRingStyle {
        val knownLevel = level?.takeIf { it in 0..100 }
            ?: return BatteryWidgetRingStyle(sweepDegrees = 0f, progressColor = null)
        return BatteryWidgetRingStyle(
            sweepDegrees = knownLevel * 3.6f,
            progressColor = if (knownLevel <= 20) LOW_RED else NORMAL_GREEN,
        )
    }
}

/**
 * Renders each widget size into one proportionally scaled bitmap. Using a single Canvas image
 * avoids RemoteViews layout rounding changing the ring/icon alignment on different launchers.
 */
internal object BatteryWidgetRenderer {
    private const val MAX_EDGE = 256
    private const val SQUARE_REFERENCE_WIDTH = 354f
    private const val SQUARE_REFERENCE_HEIGHT = 346f
    private const val TRACK_COLOR = 0xFF3A3948.toInt()

    private enum class Arrangement { GRID, ROW, COLUMN }

    private data class Cell(
        val centerX: Float,
        val centerY: Float,
        val outerRadius: Float,
        val strokeWidth: Float,
        val component: WidgetBatteryComponent,
        @param:DrawableRes val icon: Int,
        val charging: Boolean,
    )

    fun render(
        context: Context,
        model: AirPodsBase?,
        state: BatteryWidgetState,
        aspectRatio: Float,
    ): Bitmap {
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        val arrangement = when {
            safeAspect > 1.35f -> Arrangement.ROW
            safeAspect < 0.74f -> Arrangement.COLUMN
            else -> Arrangement.GRID
        }
        val width = when (arrangement) {
            Arrangement.COLUMN -> (MAX_EDGE * safeAspect).roundToInt().coerceAtLeast(72)
            else -> MAX_EDGE
        }
        val height = when (arrangement) {
            Arrangement.ROW -> (MAX_EDGE / safeAspect).roundToInt().coerceAtLeast(72)
            else -> MAX_EDGE
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scaleX = width / SQUARE_REFERENCE_WIDTH
        val scaleY = height / SQUARE_REFERENCE_HEIGHT

        if (arrangement == Arrangement.GRID) {
            drawGlassSurface(
                context = context,
                canvas = canvas,
                bounds = RectF(14f * scaleX, 19f * scaleY, 343f * scaleX, 345f * scaleY),
            )
        } else {
            val inset = min(width, height) * 0.035f
            drawGlassSurface(
                context = context,
                canvas = canvas,
                bounds = RectF(inset, inset, width - inset, height - inset),
            )
        }

        val icons = BatteryWidgetIconPolicy.iconsForModel(model)
        val cells = when (arrangement) {
            Arrangement.GRID -> gridCells(state, icons, scaleX, scaleY)
            Arrangement.ROW -> rowCells(state, icons, width, height)
            Arrangement.COLUMN -> columnCells(state, icons, width, height)
        }
        cells.forEach { cell -> drawCell(context, canvas, cell) }
        return bitmap
    }

    private fun drawGlassSurface(context: Context, canvas: Canvas, bounds: RectF) {
        val surface = WidgetGlassBackground.createBattery(
            context,
            aspectRatio = bounds.width() / bounds.height(),
        )
        canvas.drawBitmap(
            surface,
            null,
            bounds,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        surface.recycle()
    }

    private fun gridCells(
        state: BatteryWidgetState,
        icons: BatteryWidgetIcons,
        scaleX: Float,
        scaleY: Float,
    ): List<Cell> {
        val radius = 65f * min(scaleX, scaleY)
        val stroke = 12f * min(scaleX, scaleY)
        return listOf(
            Cell(102f * scaleX, 107f * scaleY, radius, stroke, state.phone, R.drawable.smartphone, state.phone.charging),
            // Match the reference's reading order: phone, right bud, left bud, case.
            Cell(254f * scaleX, 107f * scaleY, radius, stroke, state.right, icons.right, state.right.charging),
            Cell(102f * scaleX, 257f * scaleY, radius, stroke, state.left, icons.left, state.left.charging),
            Cell(254f * scaleX, 257f * scaleY, radius, stroke, state.case, R.drawable.ic_airpods_case_widget, state.case.charging),
        )
    }

    private fun rowCells(
        state: BatteryWidgetState,
        icons: BatteryWidgetIcons,
        width: Int,
        height: Int,
    ): List<Cell> {
        val cellWidth = width / 4f
        val radius = min(height * 0.29f, cellWidth * 0.43f)
        val stroke = radius * (12f / 65f)
        val centerY = height / 2f
        val components = listOf(
            Triple(state.phone, R.drawable.smartphone, state.phone.charging),
            Triple(state.right, icons.right, state.right.charging),
            Triple(state.left, icons.left, state.left.charging),
            Triple(state.case, R.drawable.ic_airpods_case_widget, state.case.charging),
        )
        return components.mapIndexed { index, (component, icon, charging) ->
            Cell((index + 0.5f) * cellWidth, centerY, radius, stroke, component, icon, charging)
        }
    }

    private fun columnCells(
        state: BatteryWidgetState,
        icons: BatteryWidgetIcons,
        width: Int,
        height: Int,
    ): List<Cell> {
        val cellHeight = height / 4f
        val radius = min(width * 0.29f, cellHeight * 0.43f)
        val stroke = radius * (12f / 65f)
        val centerX = width / 2f
        val components = listOf(
            Triple(state.phone, R.drawable.smartphone, state.phone.charging),
            Triple(state.right, icons.right, state.right.charging),
            Triple(state.left, icons.left, state.left.charging),
            Triple(state.case, R.drawable.ic_airpods_case_widget, state.case.charging),
        )
        return components.mapIndexed { index, (component, icon, charging) ->
            Cell(centerX, (index + 0.5f) * cellHeight, radius, stroke, component, icon, charging)
        }
    }

    private fun drawCell(context: Context, canvas: Canvas, cell: Cell) {
        if (!cell.component.visible) return
        drawRing(canvas, cell)
        val iconBounds = iconBounds(cell)
        drawDrawable(context, cell.icon, canvas, iconBounds)
        if (cell.charging) drawChargingBolt(context, canvas, cell)
    }

    private fun drawRing(canvas: Canvas, cell: Cell) {
        val centerlineRadius = cell.outerRadius - cell.strokeWidth / 2f
        val ringBounds = RectF(
            cell.centerX - centerlineRadius,
            cell.centerY - centerlineRadius,
            cell.centerX + centerlineRadius,
            cell.centerY + centerlineRadius,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = cell.strokeWidth
            strokeCap = Paint.Cap.ROUND
            color = TRACK_COLOR
        }
        canvas.drawCircle(cell.centerX, cell.centerY, centerlineRadius, paint)

        val style = BatteryWidgetRingPolicy.style(cell.component.level)
        val color = style.progressColor ?: return
        if (style.sweepDegrees <= 0f) return
        paint.color = color
        if (style.sweepDegrees >= 360f) {
            canvas.drawCircle(cell.centerX, cell.centerY, centerlineRadius, paint)
        } else {
            canvas.drawArc(ringBounds, -90f, style.sweepDegrees, false, paint)
        }
    }

    private fun iconBounds(cell: Cell): RectF {
        val radius = cell.outerRadius
        val (widthFactor, heightFactor) = when (cell.icon) {
            R.drawable.smartphone -> 0.46f to 0.66f
            R.drawable.ic_airpods_case_widget -> 0.42f to 0.34f
            R.drawable.ic_headphones_widget -> 0.72f to 0.56f
            else -> 0.22f to 0.36f
        }
        val width = radius * widthFactor * 2f
        val height = radius * heightFactor * 2f
        return RectF(
            cell.centerX - width / 2f,
            cell.centerY - height / 2f,
            cell.centerX + width / 2f,
            cell.centerY + height / 2f,
        )
    }

    private fun drawChargingBolt(context: Context, canvas: Canvas, cell: Cell) {
        val radius = cell.outerRadius
        val width = radius * 0.34f
        val height = radius * 0.50f
        val centerY = cell.centerY - radius * 0.96f
        drawDrawable(
            context,
            R.drawable.ic_charging_bolt_widget,
            canvas,
            RectF(
                cell.centerX - width / 2f,
                centerY - height / 2f,
                cell.centerX + width / 2f,
                centerY + height / 2f,
            ),
        )
    }

    private fun drawDrawable(context: Context, @DrawableRes resourceId: Int, canvas: Canvas, bounds: RectF) {
        val drawable = ContextCompat.getDrawable(context, resourceId)?.mutate() ?: return
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: bounds.width()
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: bounds.height()
        val fitScale = min(bounds.width() / intrinsicWidth, bounds.height() / intrinsicHeight)
        val fittedWidth = intrinsicWidth * fitScale
        val fittedHeight = intrinsicHeight * fitScale
        val left = bounds.centerX() - fittedWidth / 2f
        val top = bounds.centerY() - fittedHeight / 2f
        drawable.setBounds(
            left.roundToInt(),
            top.roundToInt(),
            (left + fittedWidth).roundToInt(),
            (top + fittedHeight).roundToInt(),
        )
        drawable.draw(canvas)
    }

}
