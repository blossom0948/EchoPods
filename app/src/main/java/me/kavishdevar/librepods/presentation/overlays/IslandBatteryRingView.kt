package me.kavishdevar.librepods.presentation.overlays

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import me.kavishdevar.librepods.presentation.widgets.BatteryWidgetRingPolicy
import kotlin.math.min

/** Compact island battery indicator, drawn with the same ring colors as the home-screen widget. */
class IslandBatteryRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var level: Int? = null
    private var stale = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }

    fun setBatteryLevel(level: Int?, isStale: Boolean) {
        this.level = level?.takeIf { it in 0..100 }
        stale = isStale
        contentDescription = this.level?.let { "AirPods battery $it percent${if (stale) ", approximate" else ""}" }
            ?: "AirPods battery unavailable"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        if (side <= 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        val strokeWidth = side * 0.105f
        val radius = (side - strokeWidth) / 2f
        ringPaint.strokeWidth = strokeWidth
        ringPaint.color = TRACK_COLOR
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        val currentLevel = level
        if (currentLevel != null && currentLevel > 0) {
            val style = BatteryWidgetRingPolicy.style(currentLevel)
            val baseColor = style.progressColor ?: BatteryWidgetRingPolicy.NORMAL_GREEN
            ringPaint.color = if (stale) (baseColor and 0x00FFFFFF) or 0x99000000.toInt() else baseColor
            if (currentLevel == 100) {
                canvas.drawCircle(centerX, centerY, radius, ringPaint)
            } else {
                val inset = strokeWidth / 2f
                canvas.drawArc(
                    RectF(inset, inset, width - inset, height - inset),
                    -90f,
                    style.sweepDegrees,
                    false,
                    ringPaint,
                )
            }
        }

        val text = currentLevel?.let { "$it%" } ?: "—"
        valuePaint.color = if (currentLevel == null) UNKNOWN_TEXT else android.graphics.Color.WHITE
        valuePaint.alpha = if (stale && currentLevel != null) 205 else 255
        valuePaint.textSize = side * if (text.length >= 4) 0.245f else 0.30f
        val baseline = centerY - (valuePaint.ascent() + valuePaint.descent()) / 2f
        canvas.drawText(text, centerX, baseline, valuePaint)
    }

    private companion object {
        const val TRACK_COLOR = 0xFF3A3948.toInt()
        const val UNKNOWN_TEXT = 0xFFB8B8BC.toInt()
    }
}
