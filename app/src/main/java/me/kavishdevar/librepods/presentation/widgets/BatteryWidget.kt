/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.presentation.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.SizeF
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.AirPodsFormFactor
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.services.ServiceManager
import android.widget.RemoteViews
import androidx.compose.material3.ExperimentalMaterial3Api

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

internal data class WidgetBatteryComponent(
    val level: Int? = null,
    val charging: Boolean = false,
    val visible: Boolean = true,
)

internal data class BatteryWidgetState(
    val phone: WidgetBatteryComponent = WidgetBatteryComponent(visible = false),
    val left: WidgetBatteryComponent = WidgetBatteryComponent(),
    val right: WidgetBatteryComponent = WidgetBatteryComponent(),
    val case: WidgetBatteryComponent = WidgetBatteryComponent(),
)

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val model = readCurrentModel(context)
        val cached = readCachedLevels(context)
        val phoneEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("show_phone_battery_in_widget", true)
        val phoneManager = context.getSystemService(BatteryManager::class.java)
        val state = BatteryWidgetState(
            phone = WidgetBatteryComponent(
                level = if (phoneEnabled) {
                    phoneManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        .takeIf { it in 0..100 }
                } else null,
                charging = phoneEnabled && phoneManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_STATUS,
                ) == BatteryManager.BATTERY_STATUS_CHARGING,
                visible = phoneEnabled,
            ),
            left = WidgetBatteryComponent(cached.left),
            right = WidgetBatteryComponent(cached.right, visible = model?.formFactor != AirPodsFormFactor.OVER_EAR_HEADPHONES),
            case = WidgetBatteryComponent(cached.case, visible = model?.formFactor != AirPodsFormFactor.OVER_EAR_HEADPHONES),
        )
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            responsiveViews(context, model, state),
        )
        ServiceManager.getService()?.updateBattery()
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, BatteryWidget::class.java),
            )
            if (ids.isNotEmpty()) {
                // Prefer live service data so a wallpaper refresh does not revive cached charging state.
                val service = ServiceManager.getService()
                if (service != null) {
                    service.updateBattery()
                } else {
                    onUpdate(context, AppWidgetManager.getInstance(context), ids)
                }
            }
            return
        }
        super.onReceive(context, intent)
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
        internal fun responsiveViews(
            context: Context,
            model: AirPodsBase? = null,
            state: BatteryWidgetState = BatteryWidgetState(),
        ): RemoteViews {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val variants = widgetLayouts.map { (size, layoutId) ->
                val view = RemoteViews(context.packageName, layoutId).apply {
                    setOnClickPendingIntent(R.id.battery_widget, openApp)
                    setImageViewBitmap(
                        R.id.battery_widget_canvas,
                        BatteryWidgetRenderer.render(
                            context = context,
                            model = model,
                            state = state,
                            aspectRatio = size.width / size.height,
                        ),
                    )
                    setContentDescription(
                        R.id.battery_widget_canvas,
                        context.getString(R.string.battery_widget_accessibility_description),
                    )
                }
                size to view
            }.toMap()
            return RemoteViews(variants)
        }

        private fun readCurrentModel(context: Context): AirPodsBase? {
            val modelNumber = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("airpods_model_number", "") ?: ""
            return AirPodsModels.getModelByModelNumber(modelNumber)
        }
    }
}
