package com.galaxyairpods.health

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, horizontalPadding, horizontalPadding, horizontalPadding)
            gravity = Gravity.CENTER_VERTICAL
        }
        layout.addView(TextView(this).apply {
            text = if (resources.configuration.locales[0]?.language == "ko") "에코 헬스 개인정보 안내" else "Echo Health privacy"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = if (resources.configuration.locales[0]?.language == "ko") {
                "에코 헬스는 허용한 경우 Health Connect에서 활동 에너지, 걸음 수, 최근 24시간 수면 합계를 읽어 오늘 요약에 표시합니다. 운동 기록과 에코팟이 받은 심박 샘플은 사용자가 운동 저장을 켠 경우에만 Health Connect에 씁니다. 삼성 헬스의 기록은 Samsung Health 설정에서 Health Connect 공유를 켜야 전달됩니다."
            } else {
                "With your permission, Echo Health reads active energy, step totals, and the past 24-hour sleep total from Health Connect for its daily summary. Workout records and heart-rate samples received through EchoPods are written only when you enable workout saving. To include Samsung Health records, enable Health Connect sharing in Samsung Health settings."
            }
            textSize = 16f
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        setContentView(layout, ViewGroup.LayoutParams(-1, -1))
    }
}
