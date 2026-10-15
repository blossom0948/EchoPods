package com.galaxyairpods.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.AirPodsControlState
import com.galaxyairpods.domain.control.ListeningMode
import com.galaxyairpods.domain.control.MicrophoneMode
import kotlin.math.roundToInt

@Composable
fun AirPodsAdvancedFeaturesCard(
    state: AirPodsControlState,
    onListeningModeChange: (ListeningMode) -> Unit,
    onAapBooleanChange: (AapControlId, Boolean) -> Unit,
    onMicrophoneModeChange: (MicrophoneMode) -> Unit,
    onAapByteChange: (AapControlId, Int) -> Unit,
    onChimeVolumeChange: (Int) -> Unit,
    onRename: (String) -> Unit,
) {
    val aapEnabled = state.aapReady
    var rename by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("AirPods 고급 기능", style = MaterialTheme.typography.titleMedium)
            Text(
                state.tier.label,
                color = if (aapEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (aapEnabled) {
                    "현재 AAP 제어 세션이 확인되었습니다. 변경값은 AirPods 응답을 받은 뒤 상태에 반영됩니다."
                } else {
                    "주변 감지나 BLE 배터리만으로는 AirPods 설정을 바꿀 수 없습니다. Galaxy 오디오 연결과 AAP READY가 필요합니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.lastError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Text("소음 제어", style = MaterialTheme.typography.titleSmall)
            ListeningModeRow(
                selected = state.listeningMode(),
                enabled = aapEnabled,
                onSelected = onListeningModeChange,
            )

            AdvancedToggle(
                title = "대화 인식",
                description = "말하기 시작하면 AirPods가 음량을 낮추는 기기 기능입니다.",
                checked = state.boolean(AapControlId.CONVERSATION_DETECT_CONFIG) == true,
                known = state.boolean(AapControlId.CONVERSATION_DETECT_CONFIG) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.CONVERSATION_DETECT_CONFIG, it) },
            )
            AdvancedToggle(
                title = "자동 연결",
                description = "AirPods의 자동 연결 설정을 변경합니다. 최종 연결은 Android가 결정합니다.",
                checked = state.boolean(AapControlId.AUTOMATIC_CONNECTION_CONFIG) == true,
                known = state.boolean(AapControlId.AUTOMATIC_CONNECTION_CONFIG) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.AUTOMATIC_CONNECTION_CONFIG, it) },
            )
            AdvancedToggle(
                title = "AirPods 자동 착용 감지",
                description = "AirPods 내부의 착용 감지 설정입니다. 앱의 감지/미디어 제어 스위치와 별개입니다.",
                checked = state.boolean(AapControlId.EAR_DETECTION_CONFIG) == true,
                known = state.boolean(AapControlId.EAR_DETECTION_CONFIG) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.EAR_DETECTION_CONFIG, it) },
            )
            AdvancedToggle(
                title = "한쪽 ANC",
                description = "한쪽 이어버드만 사용 중에도 ANC를 허용합니다.",
                checked = state.boolean(AapControlId.ONE_BUD_ANC_MODE) == true,
                known = state.boolean(AapControlId.ONE_BUD_ANC_MODE) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.ONE_BUD_ANC_MODE, it) },
            )
            AdvancedToggle(
                title = "끔 모드 허용",
                description = "소음 제어 모드에서 ‘끔’을 선택할 수 있게 합니다.",
                checked = state.boolean(AapControlId.ALLOW_OFF_OPTION) == true,
                known = state.boolean(AapControlId.ALLOW_OFF_OPTION) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.ALLOW_OFF_OPTION, it) },
            )
            AdvancedToggle(
                title = "최적화 충전",
                description = "AirPods 내부의 충전 최적화 설정입니다. 배터리 퍼센트와는 별개입니다.",
                checked = state.boolean(AapControlId.DYNAMIC_END_OF_CHARGE) == true,
                known = state.boolean(AapControlId.DYNAMIC_END_OF_CHARGE) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.DYNAMIC_END_OF_CHARGE, it) },
            )
            AdvancedToggle(
                title = "수면 감지",
                description = "지원 firmware에서 잠든 상태를 감지합니다.",
                checked = state.boolean(AapControlId.SLEEP_DETECTION_CONFIG) == true,
                known = state.boolean(AapControlId.SLEEP_DETECTION_CONFIG) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.SLEEP_DETECTION_CONFIG, it) },
            )
            AdvancedToggle(
                title = "적응형 음량",
                description = "주변 환경에 맞춰 음량을 조절하는 AirPods 기능입니다.",
                checked = state.boolean(AapControlId.ADAPTIVE_VOLUME_CONFIG) == true,
                known = state.boolean(AapControlId.ADAPTIVE_VOLUME_CONFIG) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.ADAPTIVE_VOLUME_CONFIG, it) },
            )
            AdvancedToggle(
                title = "스와이프로 볼륨 조절",
                description = "지원 모델에서 스템을 위·아래로 쓸어 볼륨을 조절합니다.",
                checked = state.boolean(AapControlId.VOLUME_SWIPE_MODE) == true,
                known = state.boolean(AapControlId.VOLUME_SWIPE_MODE) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.VOLUME_SWIPE_MODE, it) },
            )
            AdvancedToggle(
                title = "자동 통화 응답",
                description = "지원 모델에서 착용 상태에 따른 자동 통화 응답을 설정합니다.",
                checked = state.boolean(AapControlId.AUTO_ANSWER_MODE) == true,
                known = state.boolean(AapControlId.AUTO_ANSWER_MODE) != null,
                enabled = aapEnabled,
                onCheckedChange = { onAapBooleanChange(AapControlId.AUTO_ANSWER_MODE, it) },
            )

            HorizontalDivider()
            Text("마이크", style = MaterialTheme.typography.titleSmall)
            MicrophoneSelector(
                selected = state.microphoneMode(),
                enabled = aapEnabled,
                onSelected = onMicrophoneModeChange,
            )

            Text("접근성", style = MaterialTheme.typography.titleSmall)
            AapByteSlider(
                title = "누르기 속도",
                description = "더블 클릭 간격",
                value = state.byte(AapControlId.DOUBLE_CLICK_INTERVAL) ?: 0,
                range = 0..2,
                enabled = aapEnabled,
                onValueChange = { onAapByteChange(AapControlId.DOUBLE_CLICK_INTERVAL, it) },
            )
            AapByteSlider(
                title = "길게 누르기 시간",
                description = "스템 길게 누르기 인식 시간",
                value = state.byte(AapControlId.CLICK_HOLD_INTERVAL) ?: 0,
                range = 0..2,
                enabled = aapEnabled,
                onValueChange = { onAapByteChange(AapControlId.CLICK_HOLD_INTERVAL, it) },
            )
            AapByteSlider(
                title = "볼륨 스와이프 속도",
                description = "스템 볼륨 스와이프 감도",
                value = state.byte(AapControlId.VOLUME_SWIPE_INTERVAL) ?: 0,
                range = 0..2,
                enabled = aapEnabled,
                onValueChange = { onAapByteChange(AapControlId.VOLUME_SWIPE_INTERVAL, it) },
            )
            AapByteSlider(
                title = "알림음 크기",
                description = "케이스·AirPods 알림음",
                value = state.byte(AapControlId.CHIME_VOLUME) ?: 75,
                range = 0..100,
                enabled = aapEnabled,
                onValueChange = onChimeVolumeChange,
            )

            HorizontalDivider()
            Text("AirPods 이름", style = MaterialTheme.typography.titleSmall)
            Text(
                "AAP 연결이 확인된 동안에만 기기에 이름 변경을 요청합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = rename,
                    onValueChange = { rename = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = aapEnabled,
                    label = { Text("새 이름") },
                )
                Button(
                    onClick = { onRename(rename) },
                    enabled = aapEnabled && rename.isNotBlank(),
                ) { Text("변경") }
            }

            HorizontalDivider()
            Text("조건부 기능", style = MaterialTheme.typography.titleSmall)
            Text(
                "Head Gestures·큰 소리 줄이기·투명 모드 사용자 조정·보청기·심박수·Spatial Audio·Find My·멀티 디바이스는 루트/ATT hook 또는 특정 firmware가 필요합니다. 실제 Gate 전에는 전송하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
@Composable
private fun ListeningModeRow(
    selected: ListeningMode?,
    enabled: Boolean,
    onSelected: (ListeningMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ListeningMode.entries.forEach { mode ->
            OutlinedButton(
                onClick = { onSelected(mode) },
                enabled = enabled,
            ) {
                Text(
                    text = if (selected == mode) "✓ ${mode.label}" else mode.label,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MicrophoneSelector(
    selected: MicrophoneMode?,
    enabled: Boolean,
    onSelected: (MicrophoneMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(selected?.label ?: "확인 중")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MicrophoneMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun AdvancedToggle(
    title: String,
    description: String,
    checked: Boolean,
    known: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!known) {
                Text("상태 확인 전", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AapByteSlider(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.coerceIn(range).toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sliderValue.roundToInt().toString(), color = MaterialTheme.colorScheme.primary)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

