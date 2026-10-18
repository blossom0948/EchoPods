package me.kavishdevar.librepods.presentation.widgets

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.content.res.Configuration
import kotlin.math.roundToInt

/**
 * A compact RemoteViews-safe glass surface. Android widgets cannot sample/blur the
 * wallpaper behind each pixel, so this uses the wallpaper's system accent color as
 * a restrained translucent tint and keeps the image itself transparent outside its
 * rounded silhouette.
 */
internal object WidgetGlassBackground {
    fun create(context: Context, aspectRatio: Float): Bitmap {
        return create(
            context = context,
            aspectRatio = aspectRatio,
            wallpaperMix = 0.28f,
            upperAlpha = 218,
            lowerAlpha = 194,
            cornerFraction = 0.16f,
        )
    }

    fun createBattery(context: Context, aspectRatio: Float): Bitmap {
        return create(
            context = context,
            aspectRatio = aspectRatio,
            wallpaperMix = 0.40f,
            upperAlpha = 215,
            lowerAlpha = 190,
            cornerFraction = 0.20f,
        )
    }

    private fun create(
        context: Context,
        aspectRatio: Float,
        wallpaperMix: Float,
        upperAlpha: Int,
        lowerAlpha: Int,
        cornerFraction: Float,
    ): Bitmap {
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.25f, 4f) ?: 1f
        val width: Int
        val height: Int
        if (safeAspect >= 1f) {
            width = 256
            height = (width / safeAspect).roundToInt().coerceAtLeast(64)
        } else {
            height = 256
            width = (height * safeAspect).roundToInt().coerceAtLeast(64)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val wallpaperColor = readWallpaperAccent(context) ?: 0xFF55515F.toInt()
        val themeBase = if ((context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ) 0xFF17171D.toInt() else 0xFF24242B.toInt()
        val tinted = mix(themeBase, wallpaperColor, wallpaperMix)
        val upper = withAlpha(mix(tinted, 0xFFFFFFFF.toInt(), 0.08f), upperAlpha)
        val lower = withAlpha(tinted, lowerAlpha)
        val bounds = RectF(1f, 1f, width - 1f, height - 1f)
        val radius = minOf(width, height) * cornerFraction

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(), upper, lower, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bounds, radius, radius, fill)

        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(bounds, radius, radius, highlight)
        return bitmap
    }

    private fun readWallpaperAccent(context: Context): Int? {
        return runCatching {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
            val primary = colors.primaryColor.toArgb()
            val secondary = colors.secondaryColor?.toArgb()
            val tertiary = colors.tertiaryColor?.toArgb()
            when {
                secondary != null && tertiary != null -> mix(mix(primary, secondary, 0.5f), tertiary, 0.25f)
                secondary != null -> mix(primary, secondary, 0.35f)
                else -> primary
            }
        }.getOrNull()
    }

    private fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = from shr shift and 0xFF
            val b = to shr shift and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
}
