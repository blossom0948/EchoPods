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
import android.os.Handler
import android.os.Looper
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_CONNECT_SELECTED) return

        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val selectedAddress = preferences.getString("preferred_airpods_address", null)
            ?.takeIf(String::isNotBlank)
            ?: preferences.getString("mac_address", null)?.takeIf(String::isNotBlank)
        if (selectedAddress == null) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            return
        }

        preferences.edit()
            .putLong(CONNECT_REQUESTED_AT_KEY, System.currentTimeMillis())
            .apply()
        refresh(context)

        val serviceIntent = Intent(context, AirPodsService::class.java).apply {
            action = AirPodsService.ACTION_RECONNECT_SELECTED_AIRPODS
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }.onFailure {
            preferences.edit().remove(CONNECT_REQUESTED_AT_KEY).apply()
            refresh(context)
        }
        Handler(Looper.getMainLooper()).postDelayed({ refresh(context) }, CONNECTING_TIMEOUT_MS)
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
            val connected = preferences.getBoolean("connection_successful", false)
            val connecting = !connected &&
                System.currentTimeMillis() - preferences.getLong(CONNECT_REQUESTED_AT_KEY, 0L) <
                CONNECTING_TIMEOUT_MS
            val selectedName = preferences.getString("preferred_airpods_name", null)
                ?.takeIf(String::isNotBlank)
                ?: preferences.getString("name", "AirPods").orEmpty()
            val views = RemoteViews(context.packageName, R.layout.connect_airpods_widget).apply {
                setTextViewText(R.id.connect_widget_title, context.getString(R.string.connect_widget_title))
                setTextViewText(
                    R.id.connect_widget_subtitle,
                    when {
                        !hasSelection -> context.getString(R.string.connect_widget_choose_device)
                        connected -> selectedName
                        connecting -> context.getString(R.string.connect_widget_connecting)
                        else -> context.getString(R.string.connect_widget_tap_to_connect, selectedName)
                    },
                )
                setImageViewBitmap(
                    R.id.connect_widget_glass_background,
                    WidgetGlassBackground.create(context, 2.35f),
                )
                setTextViewText(
                    R.id.connect_widget_state,
                    context.getString(
                        when {
                            connected -> R.string.connect_widget_connected
                            connecting -> R.string.connect_widget_connecting_short
                            hasSelection -> R.string.connect_widget_connect
                            else -> R.string.connect_widget_choose
                        },
                    ),
                )
                setInt(
                    R.id.connect_widget_state,
                    "setBackgroundResource",
                    when {
                        connected -> R.drawable.connect_widget_state_connected
                        connecting -> R.drawable.connect_widget_state_connecting
                        else -> R.drawable.connect_widget_state_disconnected
                    },
                )
                setTextColor(
                    R.id.connect_widget_state,
                    context.getColor(
                        when {
                            connected -> R.color.connect_widget_connected_text
                            connecting -> R.color.connect_widget_connecting_text
                            else -> R.color.connect_widget_disconnected_text
                        },
                    ),
                )

                val clickIntent = PendingIntent.getBroadcast(
                    context,
                    721,
                    Intent(context, ConnectAirPodsWidget::class.java).setAction(ACTION_CONNECT_SELECTED),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.connect_widget_root, clickIntent)
                setContentDescription(
                    R.id.connect_widget_root,
                    context.getString(R.string.connect_widget_description),
                )
            }
            manager.updateAppWidget(ids, views)
        }

        private const val ACTION_CONNECT_SELECTED = "com.galaxyairpods.widget.CONNECT_SELECTED"
        private const val CONNECT_REQUESTED_AT_KEY = "connect_widget_requested_at"
        private const val CONNECTING_TIMEOUT_MS = 15_000L
    }
}
