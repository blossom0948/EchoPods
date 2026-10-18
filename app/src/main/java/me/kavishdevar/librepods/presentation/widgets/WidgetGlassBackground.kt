package me.kavishdevar.librepods.presentation.widgets

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.os.Build

/**
 * A compact RemoteViews-safe glass surface. Android widgets cannot sample/blur the
 * wallpaper behind each pixel, so this uses the wallpaper's system accent color as
 * a restrained translucent tint and keeps the image itself transparent outside its
 * rounded silhouette.
 */
internal object WidgetGlassBackground {
    fun create(context: Context, aspectRatio: Float): Bitmap {
        val width = 256
        val height = (width / aspectRatio.coerceIn(0.45f, 3.5f)).toInt().coerceIn(96, 512)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val wallpaperColor = readWallpaperAccent(context) ?: 0xFF55515F.toInt()
        val tinted = mix(0xFF202024.toInt(), wallpaperColor, 0.28f)
        val upper = withAlpha(mix(tinted, 0xFFFFFFFF.toInt(), 0.10f), 218)
        val lower = withAlpha(tinted, 194)
        val bounds = RectF(1f, 1f, width - 1f, height - 1f)
        val radius = minOf(width, height) * 0.16f

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

    @Suppress("DEPRECATION")
    private fun readWallpaperAccent(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
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
