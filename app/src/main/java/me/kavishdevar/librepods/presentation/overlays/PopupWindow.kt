/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.
*/

package me.kavishdevar.librepods.presentation.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus

@SuppressLint("InflateParams")
class PopupWindow(
    private val context: Context,
    private val onCloseCallback: () -> Unit = {},
) {
    private val view: View = LayoutInflater.from(context).inflate(R.layout.popup_window, null)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null
    private var batteryUpdateReceiver: BroadcastReceiver? = null
    private var isClosing = false

    private val params = WindowManager.LayoutParams().apply {
        height = WindowManager.LayoutParams.WRAP_CONTENT
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        width = (minOf(widthDp - 32f, 480f).coerceAtLeast(280f) * metrics.density).toInt()
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = (16 * metrics.density).toInt()
        dimAmount = 0.22f
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }

    val isVisible: Boolean get() = view.parent != null && view.visibility == View.VISIBLE

    init {
        view.findViewById<ImageButton>(R.id.close_button).setOnClickListener { close() }
        installOutsideTouchHandler()
    }

    /** This listener handles only system outside-touch events, not in-view clicks. */
    @SuppressLint("ClickableViewAccessibility")
    private fun installOutsideTouchHandler() {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                close()
                true
            } else {
                false
            }
        }
    }

    fun open(
        name: String,
        batteryNotification: AirPodsNotifications.BatteryNotification,
        artworkRes: Int,
    ): Boolean {
        if (isVisible) return replay(name, batteryNotification, artworkRes)
        if (isClosing) return false

        return try {
            bindContent(name, batteryNotification.getBattery(), artworkRes)
            view.visibility = View.VISIBLE
            windowManager.addView(view, params)
            view.alpha = 0f
            view.scaleX = 0.97f
            view.scaleY = 0.97f
            view.translationY = dp(18).toFloat()
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
            registerBatteryUpdateReceiver()
            scheduleAutoClose()
            Log.i(TAG, "Battery popup shown with model artwork")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to show battery popup", exception)
            cleanup()
            onCloseCallback()
            false
        }
    }

    /** Replays a wear-change event even when the same popup is already on screen. */
    fun replay(
        name: String,
        batteryNotification: AirPodsNotifications.BatteryNotification,
        artworkRes: Int,
    ): Boolean {
        if (!isVisible || isClosing) return false
        bindContent(name, batteryNotification.getBattery(), artworkRes)
        view.animate().cancel()
        view.alpha = 0.88f
        view.scaleX = 0.985f
        view.scaleY = 0.985f
        view.translationY = dp(8).toFloat()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator(1.3f))
            .start()
        scheduleAutoClose()
        Log.i(TAG, "Battery popup replayed for wear change")
        return true
    }

    private fun bindContent(name: String, batteries: List<Battery>, artworkRes: Int) {
        view.findViewById<TextView>(R.id.name).text = name
        view.findViewById<ImageView>(R.id.product_artwork).setImageResource(artworkRes)
        updateBatteryStatusFromList(batteries)
    }

    private fun updateBatteryStatusFromList(batteries: List<Battery>) {
        updateBatteryCell(
            containerId = R.id.left_battery_item,
            labelRes = R.string.left,
            battery = batteries.firstOrNull { it.component == BatteryComponent.LEFT },
        )
        updateBatteryCell(
            containerId = R.id.right_battery_item,
            labelRes = R.string.right,
            battery = batteries.firstOrNull { it.component == BatteryComponent.RIGHT },
        )
        updateBatteryCell(
            containerId = R.id.case_battery_item,
            labelRes = R.string.case_alt,
            battery = batteries.firstOrNull { it.component == BatteryComponent.CASE },
        )
    }

    private fun updateBatteryCell(containerId: Int, labelRes: Int, battery: Battery?) {
        val cell = view.findViewById<View>(containerId)
        cell.findViewById<TextView>(R.id.popup_battery_label).setText(labelRes)
        val known = battery?.isLevelKnown == true && battery.level in 0..100
        val value = if (known) {
            val stalePrefix = if (battery.status == BatteryStatus.DISCONNECTED) "~" else ""
            context.getString(R.string.popup_battery_value, battery.level).let { "$stalePrefix$it" }
        } else {
            "—"
        }
        cell.findViewById<TextView>(R.id.popup_battery_value).text = value
        val isCharging = known && battery.status in setOf(
            BatteryStatus.CHARGING,
            BatteryStatus.OPTIMIZED_CHARGING,
        )
        cell.findViewById<ImageView>(R.id.popup_battery_charging).visibility =
            if (isCharging) View.VISIBLE else View.GONE
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBatteryUpdateReceiver() {
        if (batteryUpdateReceiver != null) return
        batteryUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != AirPodsNotifications.BATTERY_DATA) return
                val batteryList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra("data", Battery::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra("data")
                }
                batteryList?.let(::updateBatteryStatusFromList)
            }
        }
        val filter = IntentFilter(AirPodsNotifications.BATTERY_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batteryUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(batteryUpdateReceiver, filter)
        }
    }

    private fun scheduleAutoClose() {
        autoCloseRunnable?.let(handler::removeCallbacks)
        autoCloseRunnable = Runnable { close() }
        handler.postDelayed(autoCloseRunnable!!, AUTO_CLOSE_MS)
    }

    fun close() {
        if (!isVisible || isClosing) return
        isClosing = true
        autoCloseRunnable?.let(handler::removeCallbacks)
        unregisterBatteryUpdateReceiver()
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .translationY(dp(12).toFloat())
            .setDuration(190L)
            .setInterpolator(AccelerateInterpolator(1.2f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cleanup()
                    onCloseCallback()
                }
            })
            .start()
    }

    private fun cleanup() {
        runCatching { view.animate().setListener(null) }
        runCatching { if (view.parent != null) windowManager.removeViewImmediate(view) }
        view.visibility = View.GONE
        unregisterBatteryUpdateReceiver()
        isClosing = false
    }

    private fun unregisterBatteryUpdateReceiver() {
        val receiver = batteryUpdateReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        batteryUpdateReceiver = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "PopupWindow"
        const val AUTO_CLOSE_MS = 12_000L
    }
}
