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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.IslandPopupSize

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
        width = IslandPopupSize.SMALL.widthPx(metrics.widthPixels, metrics.density)
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
        view.setOnApplyWindowInsetsListener { _, insets ->
            updateTopInset(insets.getInsets(WindowInsets.Type.statusBars()).top)
            insets
        }
    }

    fun show(
        deviceName: String,
        artworkRes: Int,
        level: Int,
        componentName: String,
        popupSize: IslandPopupSize = IslandPopupSize.SMALL,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { show(deviceName, artworkRes, level, componentName, popupSize) }
            return true
        }
        bind(deviceName, artworkRes, level, componentName, popupSize)
        val metrics = context.resources.displayMetrics
        params.width = popupSize.widthPx(metrics.widthPixels, metrics.density)
        if (isVisible) {
            // A new threshold can arrive while the previous alert is fading out.
            // Remove the close listener before cancelling, or its onAnimationEnd
            // can remove the window after this refresh has already started.
            view.animate().setListener(null)
            view.animate().cancel()
            isClosing = false
            runCatching { windowManager.updateViewLayout(view, params) }
            view.alpha = 0.86f
            view.translationY = -dp(5).toFloat()
            view.scaleX = 0.985f
            view.scaleY = 0.985f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()
            scheduleDismiss()
            return true
        }
        if (isClosing) return false
        try {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = -dp(20).toFloat()
            view.scaleX = 0.96f
            view.scaleY = 0.96f
            dismissalReported = false
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            windowManager.addView(view, params)
            view.requestApplyInsets()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
            scheduleDismiss()
            Log.i(TAG, "Low-battery alert overlay shown at ${level}%")
            return true
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to display low-battery overlay", exception)
            cleanup()
            return false
        }
    }

    private fun bind(
        deviceName: String,
        artworkRes: Int,
        level: Int,
        componentName: String,
        popupSize: IslandPopupSize,
    ) {
        val safeLevel = level.coerceIn(0, 100)
        val compact = popupSize == IslandPopupSize.SMALL
        val root = view.findViewById<LinearLayout>(R.id.low_battery_alert_root)
        root.setPadding(
            dp(if (compact) 10 else 16),
            dp(if (compact) 10 else 14),
            dp(if (compact) 10 else 18),
            dp(if (compact) 10 else 14),
        )
        val artwork = view.findViewById<ImageView>(R.id.low_battery_product_artwork)
        artwork.layoutParams = artwork.layoutParams.apply {
            width = dp(if (compact) 36 else 46)
            height = dp(if (compact) 36 else 46)
        }
        val details = root.getChildAt(1) as LinearLayout
        details.layoutParams = (details.layoutParams as LinearLayout.LayoutParams).apply {
            marginStart = dp(if (compact) 8 else 14)
        }
        val deviceNameView = view.findViewById<TextView>(R.id.low_battery_device_name)
        deviceNameView.text = deviceName
        deviceNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 14f else 16f)
        view.findViewById<TextView>(R.id.low_battery_component_name).apply {
            text = componentName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 10.5f else 12f)
        }
        view.findViewById<ImageView>(R.id.low_battery_product_artwork).setImageResource(artworkRes)
        view.findViewById<TextView>(R.id.low_battery_percentage).apply {
            text = context.getString(R.string.popup_battery_value, safeLevel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 17f else 19f)
        }
        val glyph = view.findViewById<FrameLayout>(R.id.low_battery_glyph)
        val glyphWidthDp = if (compact) 38 else 48
        glyph.layoutParams = glyph.layoutParams.apply {
            width = dp(glyphWidthDp)
            height = dp(if (compact) 21 else 24)
            if (this is LinearLayout.LayoutParams) marginStart = dp(if (compact) 6 else 8)
        }
        val fillWidth = if (safeLevel == 0) 0 else (dp(glyphWidthDp - 14) * safeLevel / 100).coerceAtLeast(dp(1))
        val fill = view.findViewById<View>(R.id.low_battery_fill)
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
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(280L)
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
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        if (shouldReportDismissal) onDismissed()
    }

    private fun statusBarInset(): Int = runCatching {
        windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars())
            .top
    }.getOrDefault(0)

    private fun updateTopInset(topInset: Int) {
        val newY = topInset + dp(10)
        if (newY == params.y) return
        params.y = newY
        if (view.parent != null) {
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { Log.w(TAG, "Unable to update low-battery alert insets", it) }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "LowBatteryAlert"
        const val DISPLAY_DURATION_MS = 4_500L
    }
}
