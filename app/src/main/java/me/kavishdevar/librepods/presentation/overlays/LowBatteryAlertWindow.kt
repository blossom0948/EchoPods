package me.kavishdevar.librepods.presentation.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import me.kavishdevar.librepods.R

/** Compact, app-independent overlay for a newly observed low AirPods component. */
class LowBatteryAlertWindow(
    private val context: Context,
    private val onDismissed: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view = LayoutInflater.from(context).inflate(R.layout.low_battery_alert, null)
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var isClosing = false
    private var dismissalReported = true

    private val params = WindowManager.LayoutParams().apply {
        height = WindowManager.LayoutParams.WRAP_CONTENT
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        width = (minOf(widthDp - 24f, 480f).coerceAtLeast(260f) * metrics.density).toInt()
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = statusBarInset() + dp(10)
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }

    val isVisible: Boolean get() = view.parent != null && view.visibility == View.VISIBLE

    init {
        view.setOnClickListener { close() }
    }

    fun show(deviceName: String, artworkRes: Int, level: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { show(deviceName, artworkRes, level) }
            return
        }
        bind(deviceName, artworkRes, level)
        if (isVisible) {
            view.animate().cancel()
            view.alpha = 0.86f
            view.translationY = -dp(5).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()
            scheduleDismiss()
            return
        }
        if (isClosing) return
        try {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = -dp(28).toFloat()
            dismissalReported = false
            windowManager.addView(view, params)
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
            scheduleDismiss()
            Log.i(TAG, "Low-battery alert overlay shown at ${level}%")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to display low-battery overlay", exception)
            cleanup()
        }
    }

    private fun bind(deviceName: String, artworkRes: Int, level: Int) {
        val safeLevel = level.coerceIn(0, 100)
        view.findViewById<TextView>(R.id.low_battery_device_name).text = deviceName
        view.findViewById<ImageView>(R.id.low_battery_product_artwork).setImageResource(artworkRes)
        view.findViewById<TextView>(R.id.low_battery_percentage).text =
            context.getString(R.string.popup_battery_value, safeLevel)
        val fill = view.findViewById<View>(R.id.low_battery_fill)
        val fillWidth = if (safeLevel == 0) 0 else (dp(34) * safeLevel / 100).coerceAtLeast(dp(1))
        fill.layoutParams = fill.layoutParams.apply { width = fillWidth }
    }

    private fun scheduleDismiss() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = Runnable { close() }
        handler.postDelayed(dismissRunnable!!, DISPLAY_DURATION_MS)
    }

    fun close() {
        if (!isVisible || isClosing) return
        isClosing = true
        dismissRunnable?.let(handler::removeCallbacks)
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(-dp(20).toFloat())
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = cleanup()
            })
            .start()
    }

    fun dismissImmediately() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        view.animate().cancel()
        isClosing = false
        cleanup()
    }

    private fun cleanup() {
        val shouldReportDismissal = !dismissalReported
        dismissalReported = true
        runCatching { view.animate().setListener(null) }
        runCatching { if (view.parent != null) windowManager.removeViewImmediate(view) }
        view.visibility = View.GONE
        isClosing = false
        if (shouldReportDismissal) onDismissed()
    }

    private fun statusBarInset(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) runCatching { context.resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "LowBatteryAlert"
        const val DISPLAY_DURATION_MS = 4_500L
    }
}
