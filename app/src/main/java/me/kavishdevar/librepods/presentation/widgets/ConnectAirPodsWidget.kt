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
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.material3.ExperimentalMaterial3Api
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.AirPodsService

@OptIn(ExperimentalMaterial3Api::class)
class ConnectAirPodsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ConnectAirPodsWidget::class.java))
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val selectedAddress = preferences.getString("preferred_airpods_address", null)
                ?.takeIf(String::isNotBlank)
                ?: preferences.getString("mac_address", "").orEmpty()
            val hasSelection = selectedAddress.isNotBlank()
            val selectedName = preferences.getString("preferred_airpods_name", null)
                ?.takeIf(String::isNotBlank)
                ?: preferences.getString("name", "AirPods").orEmpty()
            val views = RemoteViews(context.packageName, R.layout.connect_airpods_widget).apply {
                setTextViewText(R.id.connect_widget_title, context.getString(R.string.connect_widget_title))
                setTextViewText(
                    R.id.connect_widget_subtitle,
                    if (hasSelection) selectedName else context.getString(R.string.connect_widget_choose_device),
                )

                val clickIntent = if (hasSelection) {
                    val serviceIntent = Intent(context, AirPodsService::class.java).apply {
                        action = AirPodsService.ACTION_RECONNECT_SELECTED_AIRPODS
                    }
                    val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        PendingIntent.getForegroundService(
                            context,
                            721,
                            serviceIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    } else {
                        PendingIntent.getService(
                            context,
                            721,
                            serviceIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }
                    pendingIntent
                } else {
                    PendingIntent.getActivity(
                        context,
                        722,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
                setOnClickPendingIntent(R.id.connect_widget_root, clickIntent)
                setContentDescription(
                    R.id.connect_widget_root,
                    context.getString(R.string.connect_widget_description),
                )
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
