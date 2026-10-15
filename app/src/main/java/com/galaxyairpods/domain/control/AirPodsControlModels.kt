package com.galaxyairpods.domain.control

/**
 * AACP control identifiers observed in public AirPods interoperability
 * implementations.  They are intentionally kept separate from the battery
 * domain: a connected Android audio route is required before any command is
 * sent, and no control value is restored as live state after a process restart.
 */
enum class AapControlId(
    val wireValue: Int,
    val title: String,
) {
    MIC_MODE(0x01, "마이크 위치"),
    BUTTON_SEND_MODE(0x05, "버튼 전송 모드"),
    VOICE_TRIGGER(0x12, "음성 호출"),
    SINGLE_CLICK_MODE(0x14, "한 번 누르기"),
    DOUBLE_CLICK_MODE(0x15, "두 번 누르기"),
    CLICK_HOLD_MODE(0x16, "길게 누르기"),
    DOUBLE_CLICK_INTERVAL(0x17, "누르기 속도"),
    CLICK_HOLD_INTERVAL(0x18, "길게 누르기 시간"),
    LISTENING_MODE_CONFIGS(0x1A, "소음 제어 모드 구성"),
    ONE_BUD_ANC_MODE(0x1B, "한쪽 ANC"),
    LISTENING_MODE(0x0D, "현재 소음 제어 모드"),
    AUTO_ANSWER_MODE(0x1E, "자동 통화 응답"),
    CHIME_VOLUME(0x1F, "알림음 크기"),
    VOLUME_SWIPE_INTERVAL(0x23, "볼륨 스와이프 속도"),
    CALL_MANAGEMENT_CONFIG(0x24, "통화 제어"),
    VOLUME_SWIPE_MODE(0x25, "스와이프로 볼륨 조절"),
    ADAPTIVE_VOLUME_CONFIG(0x26, "적응형 음량"),
    SOFTWARE_MUTE_CONFIG(0x27, "소프트웨어 음소거"),
    CONVERSATION_DETECT_CONFIG(0x28, "대화 인식"),
    HEARING_AID(0x2C, "보청기"),
    AUTO_ANC_STRENGTH(0x2E, "적응형 ANC 강도"),
    HPS_GAIN_SWIPE(0x2F, "청력 보호 스와이프"),
    HRM_STATE(0x30, "심박수 센서"),
    IN_CASE_TONE_CONFIG(0x31, "케이스 음향"),
    SIRI_MULTITONE_CONFIG(0x32, "음성 호출 멀티톤"),
    HEARING_ASSIST_CONFIG(0x33, "청각 보조"),
    ALLOW_OFF_OPTION(0x34, "끔 모드 허용"),
    SLEEP_DETECTION_CONFIG(0x35, "수면 감지"),
    ALLOW_AUTO_CONNECT(0x36, "자동 연결 허용"),
    PPE_TOGGLE_CONFIG(0x37, "큰 소리 줄이기"),
    PPE_CAP_LEVEL_CONFIG(0x38, "큰 소리 제한 단계"),
    STEM_CONFIG(0x39, "스템 동작"),
    DYNAMIC_END_OF_CHARGE(0x3B, "최적화 충전"),
    EAR_DETECTION_CONFIG(0x0A, "자동 착용 감지"),
    AUTOMATIC_CONNECTION_CONFIG(0x20, "자동 연결"),
    OWNS_CONNECTION(0x06, "연결 소유권"),
    ;

    companion object {
        fun fromWireValue(value: Int): AapControlId? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ListeningMode(val wireValue: Int, val label: String) {
    OFF(1, "끔"),
    NOISE_CANCELLATION(2, "소음 제거"),
    TRANSPARENCY(3, "주변음 허용"),
    ADAPTIVE(4, "적응형"),
    ;

    companion object {
        fun fromWireValue(value: Int): ListeningMode? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class MicrophoneMode(val wireValue: Int, val label: String) {
    AUTOMATIC(0, "자동"),
    ALWAYS_RIGHT(1, "항상 오른쪽"),
    ALWAYS_LEFT(2, "항상 왼쪽"),
    ;

    companion object {
        fun fromWireValue(value: Int): MicrophoneMode? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * UI vocabulary for a future stem-event adapter. These values are not sent
 * directly as AACP bytes until a device Gate confirms the firmware mapping.
 */
enum class StemAction(val wireValue: Int, val label: String) {
    PLAY_PAUSE(0x01, "재생/일시정지"),
    NEXT_TRACK(0x02, "다음 트랙"),
    PREVIOUS_TRACK(0x03, "이전 트랙"),
    DIGITAL_ASSISTANT(0x04, "음성 비서"),
    CYCLE_NOISE_CONTROL_MODES(0x05, "소음 제어 모드 순환"),
    ;
}

enum class ControlCapabilityTier(val label: String) {
    AAP_L2CAP_EXACT("AAP 제어 연결됨"),
    AAP_PENDING("AAP 제어 연결 대기"),
    BLE_ONLY("BLE 감지 전용"),
    ROOT_OR_VENDOR_HOOK_REQUIRED("루트/제조사 연동 필요"),
    EXPERIMENTAL("실험적·기기별 확인 필요"),
    UNSUPPORTED("현재 지원 안 함"),
}

/** Transient control state. It is deliberately not persisted as live state. */
data class AirPodsControlState(
    val tier: ControlCapabilityTier = ControlCapabilityTier.BLE_ONLY,
    val aapReady: Boolean = false,
    val values: Map<AapControlId, ByteArray> = emptyMap(),
    val lastUpdatedAt: Long? = null,
    val lastError: String? = null,
) {
    fun value(id: AapControlId): ByteArray? = values[id]

    fun byte(id: AapControlId): Int? = values[id]?.firstOrNull()?.toInt()?.and(0xFF)

    fun boolean(id: AapControlId): Boolean? = when (byte(id)) {
        0x01 -> true
        0x02 -> false
        else -> null
    }

    fun listeningMode(): ListeningMode? = byte(AapControlId.LISTENING_MODE)
        ?.let(ListeningMode::fromWireValue)

    fun microphoneMode(): MicrophoneMode? = byte(AapControlId.MIC_MODE)
        ?.let(MicrophoneMode::fromWireValue)

    fun withValue(id: AapControlId, value: ByteArray, now: Long = System.currentTimeMillis()): AirPodsControlState =
        copy(values = values + (id to value.copyOf()), lastUpdatedAt = now, lastError = null)
}

fun Boolean.toAapValue(): ByteArray = byteArrayOf(if (this) 0x01 else 0x02)
