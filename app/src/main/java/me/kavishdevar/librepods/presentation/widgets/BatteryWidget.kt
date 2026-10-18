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

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.presentation.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ExperimentalMaterial3Api
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.AirPodsFormFactor
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.ServiceManager
import kotlin.io.encoding.ExperimentalEncodingApi

internal data class BatteryWidgetIcons(val left: Int, val right: Int)

internal object BatteryWidgetIconPolicy {
    fun iconsForModel(model: AirPodsBase?): BatteryWidgetIcons = when {
        model?.formFactor == AirPodsFormFactor.OVER_EAR_HEADPHONES ->
            BatteryWidgetIcons(R.drawable.ic_headphones_widget, R.drawable.ic_headphones_widget)
        model?.name?.contains("Pro", ignoreCase = true) == true -> BatteryWidgetIcons(
            R.drawable.ic_airpod_pro_left_widget,
            R.drawable.ic_airpod_pro_right_widget,
        )
        else -> BatteryWidgetIcons(
            R.drawable.ic_airpod_regular_widget,
            R.drawable.ic_airpod_regular_right_widget,
        )
    }
}

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val modelNumber = preferences
            .getString("airpods_model_number", "") ?: ""
        val model = AirPodsModels.getModelByModelNumber(modelNumber)
        val cached = readCachedLevels(context)
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            responsiveViews(context, model) { views, layoutId ->
                val overEar = model?.formFactor == AirPodsFormFactor.OVER_EAR_HEADPHONES

                fun applyLevel(
                    progressId: Int,
                    iconId: Int,
                    level: Int?,
                    showIconWhenUnknown: Boolean = true,
                ) {
                    val known = level != null && level in 0..100
                    views.setProgressBar(progressId, 100, level?.takeIf { known } ?: 0, false)
                    views.setViewVisibility(progressId, if (known) View.VISIBLE else View.GONE)
                    views.setViewVisibility(iconId, if (known || showIconWhenUnknown) View.VISIBLE else View.GONE)
                }

                applyLevel(R.id.left_battery_progress, R.id.left_battery_icon, cached.left)
                applyLevel(
                    R.id.right_battery_progress,
                    R.id.right_battery_icon,
                    cached.right.takeUnless { overEar },
                    showIconWhenUnknown = !overEar,
                )
                applyLevel(
                    R.id.case_battery_progress,
                    R.id.case_battery_icon,
                    cached.case.takeUnless { overEar },
                    showIconWhenUnknown = !overEar,
                )
            },
        )
        ServiceManager.getService()?.updateBattery()
    }

    companion object {
        private val widgetLayouts = linkedMapOf(
            SizeF(180f, 180f) to R.layout.battery_widget,
            SizeF(180f, 90f) to R.layout.battery_widget_wide,
            SizeF(300f, 90f) to R.layout.battery_widget_wide,
            SizeF(140f, 90f) to R.layout.battery_widget_wide,
            SizeF(90f, 180f) to R.layout.battery_widget_tall,
            SizeF(90f, 300f) to R.layout.battery_widget_tall,
        )

        data class CachedLevels(val left: Int?, val right: Int?, val case: Int?)

        fun readCachedLevels(context: Context): CachedLevels {
            val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val currentModel = preferences.getString("airpods_model_number", null)
            val cachedModel = preferences.getString("last_known_battery_model_number", null)
            val belongsToCurrentModel = cachedModel == null || currentModel == null || cachedModel == currentModel
            if (!belongsToCurrentModel) return CachedLevels(null, null, null)

            fun read(key: String) = preferences.getInt(key, -1).takeIf { it in 0..100 }
            return CachedLevels(
                left = read("last_known_battery_left"),
                right = read("last_known_battery_right"),
                case = read("last_known_battery_case"),
            )
        }

        @OptIn(ExperimentalMaterial3Api::class)
        fun responsiveViews(
            context: Context,
            model: AirPodsBase? = null,
            configure: (RemoteViews, Int) -> Unit = { _, _ -> },
        ): RemoteViews {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val variants = widgetLayouts.mapValues { (size, layoutId) ->
                RemoteViews(context.packageName, layoutId).apply {
                    setOnClickPendingIntent(R.id.battery_widget, openApp)
                    setImageViewBitmap(
                        R.id.widget_glass_background,
                        WidgetGlassBackground.create(context, size.width / size.height),
                    )
                    val overEar = model?.formFactor == AirPodsFormFactor.OVER_EAR_HEADPHONES
                    val earbudIcons = BatteryWidgetIconPolicy.iconsForModel(model)
                    setViewVisibility(
                        R.id.right_battery_widget_container,
                        if (overEar) View.GONE else View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.case_battery_widget_container,
                        if (overEar) View.GONE else View.VISIBLE,
                    )
                    setImageViewResource(
                        R.id.left_battery_icon,
                        earbudIcons.left,
                    )
                    setImageViewResource(
                        R.id.right_battery_icon,
                        earbudIcons.right,
                    )
                    setImageViewResource(R.id.case_battery_icon, R.drawable.ic_airpods_case_widget)
                    configure(this, layoutId)
                }
            }
            return RemoteViews(variants)
        }
    }
}
