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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.services.ServiceManager
import kotlin.io.encoding.ExperimentalEncodingApi

class NoiseControlWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetManager.updateAppWidget(appWidgetIds, responsiveViews(context))
        ServiceManager.getService()?.updateNoiseControlWidget()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ACTION_SET_ANC_MODE") {
            val mode = intent.getIntExtra("ANC_MODE", 1)
            Log.d("NoiseControlWidget", "Setting ANC mode to $mode")
            val service = ServiceManager.getService()

            if (service == null) {
                Log.w("NoiseControlWidget", "Service unavailable")
                return
            }

             service.aacpManager
                .sendControlCommand(
                    AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value,
                    mode.toByte()
                )
        }
    }

    companion object {
        private data class ModeButton(
            val viewId: Int,
            val selectorViewId: Int,
            val mode: Int,
            val requestCode: Int,
        )

        private val modeButtons = listOf(
            ModeButton(R.id.widget_off_button, R.id.widget_off_selector, 1, 0),
            ModeButton(R.id.widget_transparency_button, R.id.widget_transparency_selector, 3, 1),
            ModeButton(R.id.widget_adaptive_button, R.id.widget_adaptive_selector, 4, 2),
            ModeButton(R.id.widget_anc_button, R.id.widget_anc_selector, 2, 3),
        )

        private val widgetLayouts = linkedMapOf(
            SizeF(280f, 88f) to R.layout.noise_control_widget,
            SizeF(180f, 180f) to R.layout.noise_control_widget_grid,
            SizeF(140f, 72f) to R.layout.noise_control_widget,
            SizeF(88f, 280f) to R.layout.noise_control_widget_tall,
        )

        fun responsiveViews(
            context: Context,
            selectedMode: Int = 2,
            allowOffMode: Boolean = true,
        ): RemoteViews {
            val variants = widgetLayouts.mapValues { (size, layoutId) ->
                RemoteViews(context.packageName, layoutId).apply {
                    setImageViewBitmap(
                        R.id.widget_glass_background,
                        WidgetGlassBackground.create(context, size.width / size.height),
                    )
                    modeButtons.forEach { button ->
                        val buttonIntent = Intent(context, NoiseControlWidget::class.java).apply {
                            action = ACTION_SET_ANC_MODE
                            putExtra(EXTRA_ANC_MODE, button.mode)
                        }
                        setOnClickPendingIntent(
                            button.viewId,
                            PendingIntent.getBroadcast(
                                context,
                                button.requestCode,
                                buttonIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                        setDisplayedChild(
                            button.selectorViewId,
                            if (button.mode == selectedMode) 1 else 0,
                        )
                    }
                    setViewVisibility(
                        R.id.widget_off_button,
                        if (allowOffMode) View.VISIBLE else View.GONE,
                    )
                }
            }
            return RemoteViews(variants)
        }

        private const val ACTION_SET_ANC_MODE = "ACTION_SET_ANC_MODE"
        private const val EXTRA_ANC_MODE = "ANC_MODE"
    }
}
