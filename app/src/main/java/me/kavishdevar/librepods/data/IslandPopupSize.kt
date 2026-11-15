package me.kavishdevar.librepods.data

import java.util.Locale

/** Width presets shared by connection islands and low-battery alerts. */
enum class IslandPopupSize(
    val preferenceValue: String,
    val sliderPosition: Float,
    private val screenWidthFraction: Float,
    private val maxWidthDp: Float,
) {
    SMALL("SMALL", 0f, 0.60f, 208f),
    MEDIUM("MEDIUM", 1f, 0.82f, 296f),
    LARGE("LARGE", 2f, 0.95f, 440f);

    fun widthPx(screenWidthPx: Int, density: Float): Int {
        val safeDensity = density.coerceAtLeast(0.1f)
        val widthDp = screenWidthPx / safeDensity
        return (minOf(widthDp * screenWidthFraction, maxWidthDp) * safeDensity)
            .toInt()
            .coerceAtLeast(1)
    }

    companion object {
        fun fromPreference(value: String?): IslandPopupSize = when (value?.uppercase(Locale.ROOT)) {
            MEDIUM.preferenceValue -> MEDIUM
            LARGE.preferenceValue -> LARGE
            else -> SMALL
        }

        fun fromSliderPosition(position: Float): IslandPopupSize = entries.minBy { size ->
            kotlin.math.abs(size.sliderPosition - position)
        }
    }
}
