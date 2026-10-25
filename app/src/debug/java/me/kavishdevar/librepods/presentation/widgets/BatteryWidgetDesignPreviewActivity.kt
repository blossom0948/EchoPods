package me.kavishdevar.librepods.presentation.widgets

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import me.kavishdevar.librepods.data.AirPodsPro2USBC
import kotlin.math.roundToInt

/** Debug-only visual fixture. Never included in a release APK or used by the live widget. */
class BatteryWidgetDesignPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.rgb(9, 9, 12))
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        root.addView(
            TextView(this).apply {
                text = "디자인 미리보기 · 실제 배터리 아님"
                setTextColor(Color.LTGRAY)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                contentDescription = "Debug-only widget preview with isolated test values"
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)),
        )
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "95%, 90%, 15% 충전 중, 케이스 85% 디자인 테스트 값"
            val aspectRatio = intent.getFloatExtra("aspect", 1f).takeIf { it > 0f } ?: 1f
            setImageBitmap(
                BatteryWidgetRenderer.render(
                    context = this@BatteryWidgetDesignPreviewActivity,
                    model = AirPodsPro2USBC(),
                    state = BatteryWidgetState(
                        phone = WidgetBatteryComponent(level = 95),
                        left = WidgetBatteryComponent(level = 15, charging = true),
                        right = WidgetBatteryComponent(level = 90),
                        case = WidgetBatteryComponent(level = 85),
                    ),
                    aspectRatio = aspectRatio,
                ),
            )
        }
        root.addView(
            image,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(FrameLayout(this).apply { addView(root) })
    }
}
