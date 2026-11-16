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

package me.kavishdevar.librepods.presentation.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log.e
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.net.toUri
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.IslandPopupSize
import me.kavishdevar.librepods.services.ServiceManager
import me.kavishdevar.librepods.utils.BatterySummaryPolicy
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.roundToInt

enum class IslandType {
    CONNECTED,
    CASE_OPENED,
    EARBUD_WORN,
    EARBUD_REMOVED,
    TAKING_OVER,
    MOVED_TO_REMOTE,
    MOVED_TO_OTHER_DEVICE,
}

class IslandWindow(private val context: Context) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @SuppressLint("InflateParams")
    private val islandView: View = LayoutInflater.from(context).inflate(R.layout.island_window, null)
    private var isClosing = false
    private var params: WindowManager.LayoutParams? = null

    private var initialY = 0f
    private var initialTouchY = 0f
    private var lastTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isBeingDragged = false
    private var autoCloseHandler: Handler? = null
    private var autoCloseRunnable: Runnable? = null
    private var initialHeight = 0
    private var screenHeight = 0
    private var isDraggingDown = false
    private var lastMoveTime = 0L
    private var yMovement = 0f
    private var dragDistance = 0f

    private var initialConnectedTextY = 0f
    private var initialDeviceTextY = 0f
    private var initialBatteryViewY = 0f
    private var initialVideoViewY = 0f
    private var initialTextSeparation = 0f

    private val containerView = FrameLayout(context)

    private lateinit var springAnimation: SpringAnimation
    private val flingAnimator = ValueAnimator()
    private var islandVideoPrepared = false
    private var islandVideoSetupStarted = false
    private var islandEntranceComplete = false
    private var islandVideoFrameRevealed = false
    private var islandVideoPlayer: MediaPlayer? = null
    private var islandVideoSurface: Surface? = null
    private var islandVideoTextureView: TextureView? = null
    private var islandVideoFallbackView: ImageView? = null
    private var islandRequestedVideoUri: Uri? = null
    private var islandModel: AirPodsBase? = null
    private var popupSize: IslandPopupSize = IslandPopupSize.SMALL
    var isPreview: Boolean = false
        private set

    private val pro2ModelNumbers = setOf("A2931", "A2699", "A2698", "A3047", "A3048", "A3049")

    private val islandSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            islandVideoSurface?.release()
            islandVideoSurface = Surface(surfaceTexture)
            prepareIslandVideoIfReady()
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            releaseIslandVideoPlayer()
            islandVideoSurface?.release()
            islandVideoSurface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
            revealIslandVideoFrame()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AirPodsNotifications.BATTERY_DATA) {
                val batteryList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra("data", Battery::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra("data")
                }
                updateBatteryDisplay(batteryList)
            } else if (intent?.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val isVisible: Boolean
        get() = containerView.parent != null && containerView.visibility == View.VISIBLE

    @SuppressLint("SetTextI18n")
    private fun updateBatteryDisplay(batteryList: ArrayList<Battery>?) {
        if (batteryList == null || batteryList.isEmpty()) return

        val leftBattery = batteryList.find { it.component == BatteryComponent.LEFT }
        val rightBattery = batteryList.find { it.component == BatteryComponent.RIGHT }

        val batteryText = islandView.findViewById<TextView>(R.id.island_battery_text)
        val batteryProgressBar = islandView.findViewById<ProgressBar>(R.id.island_battery_progress)

        val knownBatteries = listOfNotNull(leftBattery, rightBattery).filter { it.isLevelKnown }
        val displayBatteryLevel = knownBatteries.minOfOrNull { it.level }

        if (displayBatteryLevel != null) {
            val isStale = knownBatteries.any { it.status == BatteryStatus.DISCONNECTED }
            batteryText.text = "${if (isStale) "~" else ""}$displayBatteryLevel%"
            batteryProgressBar.progress = displayBatteryLevel
            batteryProgressBar.isIndeterminate = false
        } else {
            batteryText.text = "?"
            batteryProgressBar.progress = 0
            batteryProgressBar.isIndeterminate = false
        }
        updateCompactBatteryDisplay(batteryList)
    }

    @SuppressLint("SetTextI18s", "ClickableViewAccessibility", "UnspecifiedRegisterReceiverFlag",
        "SetTextI18n"
    )
    fun show(
        name: String,
        batteryPercentage: Int,
        context: Context,
        type: IslandType = IslandType.CONNECTED,
        reversed: Boolean = false,
        otherDeviceName: String? = null,
        model: AirPodsBase? = null,
        popupSize: IslandPopupSize = IslandPopupSize.SMALL,
        isPreview: Boolean = false,
    ) {
        if (ServiceManager.getService()?.islandOpen == true) return
        else ServiceManager.getService()?.islandOpen = true

        val displayMetrics = Resources.getSystem().displayMetrics
        this.popupSize = popupSize
        this.isPreview = isPreview
        val width = popupSize.widthPx(displayMetrics.widthPixels, displayMetrics.density)
        screenHeight = displayMetrics.heightPixels

        val batteryList = ServiceManager.getService()?.getBattery()
        val batteryText = islandView.findViewById<TextView>(R.id.island_battery_text)
        val batteryProgressBar = islandView.findViewById<ProgressBar>(R.id.island_battery_progress)

        val batterySummary = BatterySummaryPolicy.primaryEarbudBattery(batteryList.orEmpty())
        val displayBatteryLevel = batterySummary?.level ?: batteryPercentage.takeIf { it in 1..100 }

        if (displayBatteryLevel != null) {
            batteryText.text = "${if (batterySummary?.isStale == true) "~" else ""}$displayBatteryLevel%"
            batteryProgressBar.progress = displayBatteryLevel
        } else {
            batteryText.text = "?"
            batteryProgressBar.progress = 0
        }

        batteryProgressBar.isIndeterminate = false
        islandView.findViewById<TextView>(R.id.island_device_name).text = name
        islandView.findViewById<TextView>(R.id.island_compact_name).text = name
        islandView.findViewById<TextView>(R.id.island_compact_status).text =
            context.getString(if (isPreview) R.string.island_preview_status else R.string.island_connected_text)

        val actionButton = islandView.findViewById<ImageButton>(R.id.island_action_button)
        val batteryBg = islandView.findViewById<ProgressBar>(R.id.island_battery_bg)
        if (type == IslandType.MOVED_TO_OTHER_DEVICE && !reversed) {
            actionButton.visibility = View.VISIBLE
            actionButton.setOnClickListener {
                ServiceManager.getService()?.takeOver("reverse")
                close()
            }
            batteryText.visibility = View.GONE
            batteryProgressBar.visibility = View.GONE
            batteryBg.visibility = View.GONE
        } else {
            actionButton.visibility = View.GONE
            batteryText.visibility = View.VISIBLE
            batteryProgressBar.visibility = View.VISIBLE
            batteryBg.visibility = View.VISIBLE
        }

        containerView.removeAllViews()
        val containerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        containerView.addView(islandView, containerParams)

        params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        islandView.visibility = View.VISIBLE
        containerView.visibility = View.VISIBLE

        containerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return@setOnTouchListener false)
                    flingAnimator.cancel()

                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)

                    initialY = containerView.translationY
                    initialTouchY = event.rawY
                    lastTouchY = event.rawY
                    initialHeight = islandView.height
                    isBeingDragged = false
                    isDraggingDown = false
                    lastMoveTime = System.currentTimeMillis()
                    dragDistance = 0f

                    captureInitialPositions()

                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.rawY - initialTouchY
                    val moveDelta = event.rawY - lastTouchY
                    dragDistance += abs(moveDelta)

                    isDraggingDown = moveDelta > 0

                    val currentTime = System.currentTimeMillis()
                    val timeDelta = currentTime - lastMoveTime
                    if (timeDelta > 0) {
                        yMovement = moveDelta / timeDelta * 10
                    }
                    lastMoveTime = currentTime

                    if (abs(deltaY) > 5 || isBeingDragged) {
                        isBeingDragged = true

                        // Cancel auto close timer when dragging starts
                        autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return@setOnTouchListener false)

                        val dampedDeltaY = if (deltaY > 0) {
                            initialY + (deltaY * 0.6f)
                        } else {
                            initialY + (deltaY * 0.9f)
                        }
                        containerView.translationY = dampedDeltaY

                        if (isDraggingDown && deltaY > 0) {
                            val stretchAmount = (deltaY * 0.5f).coerceAtMost(200f)
                            applyCustomStretchEffect(stretchAmount)
                        }
                    }

                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val yVelocity = velocityTracker?.yVelocity ?: 0f

                    if (isBeingDragged) {
                        val currentTranslationY = containerView.translationY
                        abs(yVelocity) > 800
                        val significantDrag = abs(dragDistance) > 80

                        when {
                            yVelocity < -1200 || (currentTranslationY < -80 && !isDraggingDown) -> {
                                animateDismissWithInertia(yVelocity)
                            }
                            yVelocity > 1200 || (isDraggingDown && significantDrag) -> {
                                animateExpandWithStretch(yVelocity)
                            }
                            else -> {
                                springBackWithInertia(yVelocity)
                            }
                        }
                    } else if (dragDistance < 10) {
                        resetAutoCloseTimer()
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    isBeingDragged = false
                    true
                }
                else -> false
            }
        }

        when (type) {
            IslandType.CONNECTED -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_connected_text)
            }
            IslandType.CASE_OPENED -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_case_opened_text)
            }
            IslandType.EARBUD_WORN -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_earbud_worn_text)
            }
            IslandType.EARBUD_REMOVED -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_earbud_removed_text)
            }
            IslandType.TAKING_OVER -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_taking_over_text)
            }
            IslandType.MOVED_TO_REMOTE -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_moved_to_remote_text)
            }
            IslandType.MOVED_TO_OTHER_DEVICE -> {
                if (otherDeviceName == null || otherDeviceName.isEmpty()) {
                    e("IslandWindow", "Other device name is null or empty for MOVED_TO_OTHER_DEVICE type")
                }
                if (reversed) {
                    islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_moved_to_other_device_reversed_text)
                } else {
                    islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_moved_to_other_device_text, otherDeviceName)
                }
            }
        }

        val videoView = islandView.findViewById<TextureView>(R.id.island_video_view)
        val videoFallbackView = islandView.findViewById<ImageView>(R.id.island_video_fallback)
        islandVideoPrepared = false
        islandVideoSetupStarted = false
        islandEntranceComplete = false
        islandVideoFrameRevealed = false
        islandRequestedVideoUri = null
        islandVideoTextureView = videoView
        islandVideoFallbackView = videoFallbackView
        videoView.isOpaque = false
        videoView.alpha = 0f
        videoView.surfaceTextureListener = islandSurfaceTextureListener
        applyModelArtwork(model, animate = false)
        configurePopupLayout(popupSize)
        updateCompactBatteryDisplay(batteryList)
        try {
            windowManager.addView(containerView, params)
        } catch (e: Exception) {
            e("IslandWindow", "Unable to attach island overlay", e)
            ServiceManager.getService()?.islandOpen = false
            return
        }

        val batteryIntentFilter = IntentFilter(AirPodsNotifications.BATTERY_DATA).apply {
            addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(batteryReceiver, batteryIntentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(batteryReceiver, batteryIntentFilter)
            }
            ServiceManager.getService()?.sendBatteryBroadcast()
        } catch (e: Exception) {
            e("IslandWindow", "Unable to register battery updates", e)
        }

        islandView.post {
            initialHeight = islandView.height
            captureInitialPositions()
        }

        springAnimation = SpringAnimation(containerView, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
        }

        containerView.alpha = 0f
        containerView.scaleX = 0.96f
        containerView.scaleY = 0.96f
        containerView.translationY = -20f
        containerView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(360L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    islandEntranceComplete = true
                    containerView.animate().setListener(null)
                    if (usesPro2IslandVideo(islandModel)) {
                        startPro2VideoIfReady()
                    } else {
                        animateModelArtworkEntrance()
                    }
                }
            })
            .start()

        resetAutoCloseTimer()
    }

    /** Refreshes the art when AACP identifies the model after the overlay has appeared. */
    fun updateModel(model: AirPodsBase?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { updateModel(model) }
            return
        }
        if (!isVisible || isClosing) return
        applyModelArtwork(model, animate = true)
        updateCompactBatteryDisplay(ServiceManager.getService()?.getBattery())
    }

    fun updatePopupSize(size: IslandPopupSize) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { updatePopupSize(size) }
            return
        }
        if (!isVisible || isClosing) return
        configurePopupLayout(size)
        params?.let { layoutParams ->
            layoutParams.width = size.widthPx(
                Resources.getSystem().displayMetrics.widthPixels,
                Resources.getSystem().displayMetrics.density,
            )
            runCatching { windowManager.updateViewLayout(containerView, layoutParams) }
                .onFailure { e("IslandWindow", "Unable to resize island overlay", it) }
        }
        if (isPreview) resetAutoCloseTimer()
    }

    private fun configurePopupLayout(size: IslandPopupSize) {
        popupSize = size
        val compact = size != IslandPopupSize.LARGE
        val root = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
        val compactContainer = islandView.findViewById<LinearLayout>(R.id.island_compact_container)
        val artContainer = islandView.findViewById<View>(R.id.island_video_container)
        val textContainer = islandView.findViewById<View>(R.id.island_text_container)
        val batteryContainer = islandView.findViewById<View>(R.id.island_battery_container)

        compactContainer.visibility = if (compact) View.VISIBLE else View.GONE
        artContainer.visibility = if (compact) View.GONE else View.VISIBLE
        textContainer.visibility = if (compact) View.GONE else View.VISIBLE
        batteryContainer.visibility = if (compact) View.GONE else View.VISIBLE

        root.minimumHeight = context.resources.getDimensionPixelSize(
            when (size) {
                IslandPopupSize.SMALL -> R.dimen.island_compact_small_height
                IslandPopupSize.MEDIUM -> R.dimen.island_compact_medium_height
                IslandPopupSize.LARGE -> R.dimen.island_large_height
            },
        )
        val density = context.resources.displayMetrics.density
        val margin = when (size) {
            IslandPopupSize.SMALL -> 0
            IslandPopupSize.MEDIUM -> (4 * density).roundToInt()
            IslandPopupSize.LARGE -> (16 * density).roundToInt()
        }
        (root.layoutParams as? FrameLayout.LayoutParams)?.let { layout ->
            layout.setMargins(margin, margin, margin, margin)
            root.layoutParams = layout
        }
        val rootPadding = when (size) {
            IslandPopupSize.SMALL -> (6 * density).roundToInt()
            IslandPopupSize.MEDIUM -> (8 * density).roundToInt()
            IslandPopupSize.LARGE -> (8 * density).roundToInt()
        }
        root.setPadding(rootPadding, rootPadding, rootPadding, rootPadding)

        val artSizeDp = if (size == IslandPopupSize.SMALL) 28 else 40
        val artLp = compactContainer.findViewById<ImageView>(R.id.island_compact_artwork).layoutParams
        artLp.width = (artSizeDp * density).roundToInt()
        artLp.height = (artSizeDp * density).roundToInt()
        compactContainer.findViewById<ImageView>(R.id.island_compact_artwork).layoutParams = artLp
        compactContainer.findViewById<TextView>(R.id.island_compact_name).textSize =
            if (size == IslandPopupSize.SMALL) 11f else 13f
        compactContainer.findViewById<TextView>(R.id.island_compact_status).textSize =
            if (size == IslandPopupSize.SMALL) 9f else 10f

        val largeArtSize = (if (size == IslandPopupSize.LARGE) 80 else 40) * density
        artContainer.layoutParams = artContainer.layoutParams.apply {
            width = largeArtSize.roundToInt()
            height = largeArtSize.roundToInt()
        }
        islandView.findViewById<ProgressBar>(R.id.island_battery_bg).layoutParams =
            islandView.findViewById<ProgressBar>(R.id.island_battery_bg).layoutParams.apply {
                width = largeArtSize.roundToInt()
                height = largeArtSize.roundToInt()
            }
        islandView.findViewById<ProgressBar>(R.id.island_battery_progress).layoutParams =
            islandView.findViewById<ProgressBar>(R.id.island_battery_progress).layoutParams.apply {
                width = largeArtSize.roundToInt()
                height = largeArtSize.roundToInt()
            }

        renderCompactArtworkAndRows(ServiceManager.getService()?.latestEarWearState())
    }

    private fun renderCompactArtworkAndRows(wearState: Pair<Boolean, Boolean>?) {
        val image = islandView.findViewById<ImageView>(R.id.island_compact_artwork)
        val leftRow = islandView.findViewById<View>(R.id.island_compact_left_row)
        val rightRow = islandView.findViewById<View>(R.id.island_compact_right_row)
        val (leftWorn, rightWorn) = wearState ?: (false to false)
        val resource = when {
            wearState != null && leftWorn && !rightWorn -> islandModel?.leftBudsRes
            wearState != null && rightWorn && !leftWorn -> islandModel?.rightBudsRes
            else -> islandModel?.budsRes
        } ?: R.drawable.airpods
        image.setImageResource(resource)

        when {
            wearState?.first == true && wearState.second -> {
                leftRow.visibility = View.VISIBLE
                rightRow.visibility = View.VISIBLE
            }
            wearState?.first == true -> {
                leftRow.visibility = View.VISIBLE
                rightRow.visibility = View.GONE
            }
            wearState?.second == true -> {
                leftRow.visibility = View.GONE
                rightRow.visibility = View.VISIBLE
            }
            else -> {
                leftRow.visibility = View.VISIBLE
                rightRow.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCompactBatteryDisplay(batteryList: List<Battery>?) {
        renderCompactArtworkAndRows(ServiceManager.getService()?.latestEarWearState())
        val byComponent = batteryList.orEmpty().associateBy { it.component }
        listOf(
            R.id.island_compact_left_battery to BatteryComponent.LEFT,
            R.id.island_compact_right_battery to BatteryComponent.RIGHT,
        ).forEach { (viewId, component) ->
            val bar = islandView.findViewById<ProgressBar>(viewId)
            val battery = byComponent[component]
            val freshKnownLevel = battery?.takeIf {
                it.isLevelKnown && it.level in 0..100 && it.status != BatteryStatus.DISCONNECTED
            }?.level
            bar.progress = freshKnownLevel ?: 0
            bar.progressTintList = ColorStateList.valueOf(
                if (freshKnownLevel != null && freshKnownLevel < 20) 0xFFFF453A.toInt()
                else 0xFF30D158.toInt(),
            )
        }
    }

    private fun usesPro2IslandVideo(model: AirPodsBase?): Boolean =
        model?.modelNumber?.any(pro2ModelNumbers::contains) == true

    private fun applyModelArtwork(model: AirPodsBase?, animate: Boolean) {
        islandModel = model
        val videoView = islandVideoTextureView ?: return
        val fallbackView = islandVideoFallbackView ?: return
        fallbackView.animate().cancel()
        videoView.animate().cancel()
        fallbackView.setImageResource(model?.budsRes ?: R.drawable.airpods)
        fallbackView.rotationY = 0f
        fallbackView.visibility = View.VISIBLE

        if (usesPro2IslandVideo(model)) {
            videoView.visibility = View.VISIBLE
            if (islandEntranceComplete) startPro2VideoIfReady()
            return
        }

        releaseIslandVideoPlayer()
        islandRequestedVideoUri = null
        islandVideoSetupStarted = false
        islandVideoPrepared = false
        islandVideoFrameRevealed = false
        videoView.alpha = 0f
        videoView.visibility = View.GONE
        fallbackView.alpha = if (animate && islandEntranceComplete) 0f else 1f
        fallbackView.translationY = if (animate && islandEntranceComplete) 8f else 0f
        fallbackView.scaleX = if (animate && islandEntranceComplete) 0.92f else 1f
        fallbackView.scaleY = if (animate && islandEntranceComplete) 0.92f else 1f
        if (animate && islandEntranceComplete) animateModelArtworkEntrance()
    }

    private fun animateModelArtworkEntrance() {
        val artwork = islandVideoFallbackView ?: return
        if (isClosing || usesPro2IslandVideo(islandModel)) return
        artwork.visibility = View.VISIBLE
        artwork.animate().cancel()
        artwork.rotationY = 0f
        artwork.cameraDistance = context.resources.displayMetrics.density * 2400f
        artwork.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .rotationY(360f)
            .setDuration(720L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction { artwork.rotationY = 0f }
            .start()
    }

    private fun startPro2VideoIfReady() {
        if (!islandEntranceComplete || islandVideoSetupStarted || isClosing) return
        val videoView = islandVideoTextureView ?: return
        videoView.visibility = View.VISIBLE
        islandVideoSetupStarted = true
        islandRequestedVideoUri = "android.resource://${context.packageName}/${R.raw.island}".toUri()
        prepareIslandVideoIfReady()
    }

    private fun prepareIslandVideoIfReady() {
        if (!islandVideoSetupStarted || isClosing || islandVideoPlayer != null) return
        val surface = islandVideoSurface ?: return
        val uri = islandRequestedVideoUri ?: return
        val player = MediaPlayer()
        islandVideoPlayer = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            player.setVolume(0f, 0f)
            player.setSurface(surface)
            player.setOnPreparedListener { preparedPlayer ->
                if (isClosing) {
                    releaseIslandVideoPlayer()
                    return@setOnPreparedListener
                }
                preparedPlayer.isLooping = false
                islandVideoPrepared = true
                runCatching { preparedPlayer.start() }
                    .onFailure { error ->
                        e("IslandWindow", "Unable to start decorative island video", error)
                        showIslandVideoFallback()
                        releaseIslandVideoPlayer()
                    }
            }
            player.setOnErrorListener { _, what, extra ->
                e("IslandWindow", "Decorative video unavailable (what=$what, extra=$extra)")
                showIslandVideoFallback()
                releaseIslandVideoPlayer()
                true
            }
            player.setDataSource(context, uri)
            player.prepareAsync()
        } catch (error: Exception) {
            e("IslandWindow", "Unable to prepare decorative island video", error)
            showIslandVideoFallback()
            releaseIslandVideoPlayer()
        }
    }

    private fun revealIslandVideoFrame() {
        if (!islandVideoPrepared || islandVideoFrameRevealed || isClosing ||
            islandVideoPlayer?.isPlaying != true
        ) return
        val videoView = islandVideoTextureView ?: return
        val fallbackView = islandVideoFallbackView ?: return
        islandVideoFrameRevealed = true
        videoView.animate().cancel()
        fallbackView.animate().cancel()
        videoView.animate()
            .alpha(1f)
            .setDuration(100L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        fallbackView.animate()
            .alpha(0f)
            .setDuration(110L)
            .withEndAction {
                if (islandVideoFrameRevealed) fallbackView.visibility = View.GONE
            }
            .start()
    }

    private fun showIslandVideoFallback() {
        islandVideoTextureView?.apply {
            animate().cancel()
            alpha = 0f
            visibility = View.GONE
        }
        islandVideoFallbackView?.apply {
            animate().cancel()
            alpha = 1f
            visibility = View.VISIBLE
        }
    }

    private fun releaseIslandVideoPlayer() {
        val player = islandVideoPlayer
        islandVideoPlayer = null
        islandVideoPrepared = false
        runCatching { player?.setOnPreparedListener(null) }
        runCatching { player?.setOnErrorListener(null) }
        runCatching { player?.reset() }
        runCatching { player?.release() }
    }

    private fun captureInitialPositions() {
        val connectedText = islandView.findViewById<TextView>(R.id.island_connected_text)
        val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)
        val batteryView = islandView.findViewById<FrameLayout>(R.id.island_battery_container)
        val videoView = islandView.findViewById<TextureView>(R.id.island_video_view)

        connectedText.post {
            initialConnectedTextY = connectedText.y
            initialDeviceTextY = deviceText.y
            initialTextSeparation = deviceText.y - (connectedText.y + connectedText.height)

            if (batteryView != null) initialBatteryViewY = batteryView.y
            initialVideoViewY = videoView.y
        }
    }

    private fun applyCustomStretchEffect(stretchAmount: Float) {
        try {
            val mainLayout = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
            islandView.findViewById<TextView>(R.id.island_connected_text)
            val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)
            islandView.findViewById<FrameLayout>(R.id.island_battery_container)

            val stretchFactor = 1f + (stretchAmount / 300f).coerceAtMost(4.0f)
            val newMinHeight = (initialHeight * stretchFactor).toInt()
            mainLayout.minimumHeight = newMinHeight

            val textMarginIncrease = (stretchAmount * 0.8f).toInt()

            val deviceTextParams = deviceText.layoutParams as LinearLayout.LayoutParams
            deviceTextParams.topMargin = textMarginIncrease
            deviceText.layoutParams = deviceTextParams

            val background = mainLayout.background
            if (background is GradientDrawable) {
                val cornerRadius = 56f
                background.cornerRadius = cornerRadius
            }

            if (params != null) {
                params!!.height = screenHeight

                val containerParams = containerView.layoutParams
                containerParams.height = screenHeight
                containerView.layoutParams = containerParams

                try {
                    windowManager.updateViewLayout(containerView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetAutoCloseTimer() {
        autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return)
        autoCloseHandler = Handler(Looper.getMainLooper())
        autoCloseRunnable = Runnable { close() }
        autoCloseHandler?.postDelayed(autoCloseRunnable!!, 4500)
    }

    private fun springBackWithInertia(velocity: Float) {
        springAnimation.cancel()
        flingAnimator.cancel()

        springAnimation.setStartVelocity(velocity)

        val baseStiffness = SpringForce.STIFFNESS_MEDIUM
        val dynamicStiffness = baseStiffness * (1f + (abs(velocity) / 3000f))
        springAnimation.spring = SpringForce(0f)
            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
            .setStiffness(dynamicStiffness)

        resetStretchEffects()

        if (params != null) {
            params!!.height = WindowManager.LayoutParams.WRAP_CONTENT
            try {
                windowManager.updateViewLayout(containerView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        springAnimation.start()
    }

    private fun resetStretchEffects() {
        try {
            val mainLayout = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
            val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)

            val heightAnimator = ValueAnimator.ofInt(mainLayout.minimumHeight, initialHeight)
            heightAnimator.duration = 300
            heightAnimator.interpolator = OvershootInterpolator(1.5f)
            heightAnimator.addUpdateListener { animation ->
                mainLayout.minimumHeight = animation.animatedValue as Int
            }

            val deviceTextParams = deviceText.layoutParams as LinearLayout.LayoutParams
            val textMarginAnimator = ValueAnimator.ofInt(deviceTextParams.topMargin, 0)
            textMarginAnimator.duration = 300
            textMarginAnimator.interpolator = OvershootInterpolator(1.5f)
            textMarginAnimator.addUpdateListener { animation ->
                deviceTextParams.topMargin = animation.animatedValue as Int
                deviceText.layoutParams = deviceTextParams
            }

            heightAnimator.start()
            textMarginAnimator.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateDismissWithInertia(velocity: Float) {
        springAnimation.cancel()
        flingAnimator.cancel()

        val baseDistance = -screenHeight
        val velocityFactor = (abs(velocity) / 2000f).coerceIn(0.5f, 2.0f)
        val targetDistance = baseDistance * velocityFactor

        val baseDuration = 400L
        val velocityDurationFactor = (1500f / (abs(velocity) + 1500f))
        val duration = (baseDuration * velocityDurationFactor).toLong().coerceIn(200L, 500L)

        flingAnimator.setFloatValues(containerView.translationY, targetDistance)
        flingAnimator.duration = duration
        flingAnimator.addUpdateListener { animation ->
            containerView.translationY = animation.animatedValue as Float

            val progress = animation.animatedFraction
            containerView.scaleX = 1f - (progress * 0.5f)
            containerView.scaleY = 1f - (progress * 0.5f)

            containerView.alpha = 1f - progress
        }
        flingAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                forceClose()
            }
        })

        flingAnimator.interpolator = DecelerateInterpolator(1.2f)
        flingAnimator.start()
    }

    private fun animateExpandWithStretch(velocity: Float) {
        springAnimation.cancel()
        flingAnimator.cancel()

        val baseDuration = 600L
        val velocityFactor = (1800f / (abs(velocity) + 1800f)).coerceIn(0.5f, 1.5f)
        val expandDuration = (baseDuration * velocityFactor).toLong().coerceIn(300L, 700L)

        if (params != null) {
            params!!.height = screenHeight
            try {
                windowManager.updateViewLayout(containerView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val containerAnimator = ValueAnimator.ofFloat(containerView.translationY, screenHeight * 0.6f)
        containerAnimator.duration = expandDuration
        containerAnimator.interpolator = DecelerateInterpolator(0.8f)
        containerAnimator.addUpdateListener { animation ->
            containerView.translationY = animation.animatedValue as Float
        }

        val stretchAnimator = ValueAnimator.ofFloat(0f, 1f)
        stretchAnimator.duration = expandDuration
        stretchAnimator.interpolator = OvershootInterpolator(0.5f)
        stretchAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            animateCustomStretch(progress)
        }

        val normalizeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f)
        normalizeAnimator.duration = 300
        normalizeAnimator.startDelay = expandDuration - 150
        normalizeAnimator.interpolator = AccelerateInterpolator(1.2f)
        normalizeAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            containerView.alpha = progress

            if (progress < 0.7f) {
                islandView.findViewById<View>(R.id.island_video_container).visibility = View.GONE
            }
        }
        normalizeAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ServiceManager.getService()?.startMainActivity()
                forceClose()
            }
        })

        containerAnimator.start()
        stretchAnimator.start()
        normalizeAnimator.start()
    }

    private fun animateCustomStretch(progress: Float) {
        try {
            val mainLayout = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
            val connectedText = islandView.findViewById<TextView>(R.id.island_connected_text)
            val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)

            val targetHeight = (screenHeight * 0.7f).toInt()
            val currentHeight = initialHeight + ((targetHeight - initialHeight) * progress)
            mainLayout.minimumHeight = currentHeight.toInt()

            val mainLayoutParams = mainLayout.layoutParams
            mainLayoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            mainLayout.layoutParams = mainLayoutParams

            val targetMargin = (400 * progress).toInt()
            val deviceTextParams = deviceText.layoutParams as LinearLayout.LayoutParams
            deviceTextParams.topMargin = targetMargin
            deviceText.layoutParams = deviceTextParams

            val baseTextSize = 24f
            deviceText.textSize = baseTextSize + (progress * 8f)

            val baseSubTextSize = 16f
            connectedText.textSize = baseSubTextSize + (progress * 4f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { close() }
            return
        }
        try {
            if (isClosing) return
            isClosing = true

            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
//                e.printStackTrace()
            }

            ServiceManager.getService()?.islandOpen = false
            autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return)

            resetStretchEffects()

            containerView.animate().setListener(null).cancel()
            containerView.animate()
                .alpha(0f)
                .scaleX(0.97f)
                .scaleY(0.97f)
                .translationY(-16f)
                .setDuration(190L)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        cleanupAndRemoveView()
                    }
                })
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
            // Even if animation fails, ensure we cleanup
            cleanupAndRemoveView()
        }
    }

    private fun cleanupAndRemoveView() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { cleanupAndRemoveView() }
            return
        }
        try {
            containerView.visibility = View.GONE
        } catch (e: Exception) {
            e("IslandWindow", "Error setting visibility: $e")
        }
        try {
            if (containerView.parent != null) {
                windowManager.removeView(containerView)
            }
        } catch (e: Exception) {
            e("IslandWindow", "Error removing view: $e")
        }
        releaseIslandVideoPlayer()
        runCatching { islandVideoSurface?.release() }
        islandVideoSurface = null
        islandVideoTextureView = null
        islandVideoFallbackView = null
        islandRequestedVideoUri = null
        isClosing = false
        // Make sure all animations are canceled
        try {
            springAnimation.cancel()
        } catch (e: Exception) {
            e("IslandWindow", "Error cancelling spring animation $e")
        }
        try {
            flingAnimator.cancel()
        } catch (e: Exception) {
            e("IslandWindow", "Error cancelling fling animation $e")
        }
    }

    fun forceClose() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { forceClose() }
            return
        }
        try {
            if (isClosing) return
            isClosing = true

            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            ServiceManager.getService()?.islandOpen = false
            autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return)

            // Cancel all ongoing animations
            containerView.animate().setListener(null).cancel()
            springAnimation.cancel()
            flingAnimator.cancel()
            // Immediately remove the view without animations
            cleanupAndRemoveView()
        } catch (e: Exception) {
            e.printStackTrace()
            isClosing = false
        }
    }
}
