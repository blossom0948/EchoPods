package me.kavishdevar.librepods.presentation.overlays

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.utils.LowBatteryAlertPolicy
import java.util.ArrayDeque

/** Debug-only controls that exercise the same overlay window used by live battery samples. */
class LowBatteryAlertDesignPreviewActivity : Activity() {
    private data class AlertSample(val component: Int, val level: Int)

    private val pendingSamples = ArrayDeque<AlertSample>()
    private var alertWindow: LowBatteryAlertWindow? = null
    private var connectionIslandWindow: IslandWindow? = null
    private lateinit var componentPicker: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(Color.rgb(18, 18, 23))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.preview_low_battery_title)
            setTextColor(Color.WHITE)
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.preview_low_battery_description)
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(16))
        })

        componentPicker = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val componentRadioIds = mutableListOf<Int>()
        listOf(
            BatteryComponent.LEFT to R.string.left,
            BatteryComponent.RIGHT to R.string.right,
            BatteryComponent.CASE to R.string.case_alt,
        ).forEach { (component, labelRes) ->
            val radioId = android.view.View.generateViewId()
            componentRadioIds += radioId
            componentPicker.addView(RadioButton(this).apply {
                id = radioId
                tag = component
                text = getString(labelRes)
                setTextColor(Color.WHITE)
            })
        }
        componentPicker.check(componentRadioIds.first())
        content.addView(componentPicker)

        val thresholds = listOf(20, 10, 5, 4, 3, 2, 1, 0)
        thresholds.chunked(2).forEach { rowValues ->
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                rowValues.forEach { level ->
                    addView(Button(this@LowBatteryAlertDesignPreviewActivity).apply {
                        text = getString(R.string.preview_low_battery_warning_button, level)
                        setOnClickListener { showOrRequestPermission(AlertSample(selectedComponent(), level)) }
                    }, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                        marginEnd = dp(8)
                    })
                }
            })
        }

        content.addView(Button(this).apply {
            text = getString(R.string.preview_all_components_warning_button)
            setOnClickListener {
                pendingSamples.clear()
                pendingSamples.add(AlertSample(BatteryComponent.LEFT, 5))
                pendingSamples.add(AlertSample(BatteryComponent.RIGHT, 10))
                pendingSamples.add(AlertSample(BatteryComponent.CASE, 20))
                showNextPendingOrRequestPermission()
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.preview_duplicate_warning_button)
            setOnClickListener {
                val component = selectedComponent()
                val sample = Battery(
                    component = component,
                    level = 10,
                    status = BatteryStatus.NOT_CHARGING,
                    levelCapturedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
                )
                val first = LowBatteryAlertPolicy.nextThresholdToAlert(sample, null)
                val second = LowBatteryAlertPolicy.nextThresholdToAlert(sample, first)
                if (first != null && second == null) {
                    showOrRequestPermission(AlertSample(component, sample.level))
                } else {
                    Toast.makeText(this@LowBatteryAlertDesignPreviewActivity, R.string.preview_duplicate_warning_failed, Toast.LENGTH_LONG).show()
                }
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.preview_background_display_button)
            setOnClickListener {
                if (requestOverlayPermissionIfNeeded()) return@setOnClickListener
                showSample(AlertSample(selectedComponent(), 10))
                Handler(Looper.getMainLooper()).postDelayed({ moveTaskToBack(true) }, 500L)
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.preview_connection_popup_button)
            setOnClickListener {
                if (requestOverlayPermissionIfNeeded()) return@setOnClickListener
                connectionIslandWindow = IslandWindow(this@LowBatteryAlertDesignPreviewActivity).also {
                    it.show(
                        name = getString(R.string.preview_airpods_name),
                        batteryPercentage = 50,
                        context = this@LowBatteryAlertDesignPreviewActivity,
                    )
                }
                Handler(Looper.getMainLooper()).postDelayed({ moveTaskToBack(true) }, 500L)
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.preview_missing_battery_button)
            setOnClickListener {
                Toast.makeText(this@LowBatteryAlertDesignPreviewActivity, R.string.preview_missing_battery_message, Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun selectedComponent(): Int =
        componentPicker.findViewById<RadioButton>(componentPicker.checkedRadioButtonId)?.tag as? Int
            ?: BatteryComponent.LEFT

    private fun showNextPendingOrRequestPermission() {
        if (requestOverlayPermissionIfNeeded()) return
        showNextPending()
    }

    private fun showNextPending() {
        val sample = pendingSamples.pollFirst() ?: return
        showSample(sample)
    }

    private fun showOrRequestPermission(sample: AlertSample) {
        if (requestOverlayPermissionIfNeeded()) return
        showSample(sample)
    }

    private fun requestOverlayPermissionIfNeeded(): Boolean {
        if (Settings.canDrawOverlays(this)) return false
        Toast.makeText(this, R.string.preview_overlay_permission_required, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
        return true
    }

    private fun showSample(sample: AlertSample) {
        val artwork = when (sample.component) {
            BatteryComponent.LEFT -> R.drawable.airpods_pro_left_notification
            BatteryComponent.RIGHT -> R.drawable.airpods_pro_right_notification
            else -> R.drawable.airpods_pro_case_notification
        }
        val label = getString(
            when (sample.component) {
                BatteryComponent.LEFT -> R.string.left
                BatteryComponent.RIGHT -> R.string.right
                else -> R.string.case_alt
            },
        )
        val window = alertWindow ?: LowBatteryAlertWindow(this) {
            showNextPending()
        }.also { alertWindow = it }
        window.show(getString(R.string.preview_airpods_name), artwork, sample.level, label)
    }

    override fun onDestroy() {
        pendingSamples.clear()
        alertWindow?.dismissImmediately()
        alertWindow = null
        connectionIslandWindow?.forceClose()
        connectionIslandWindow = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
