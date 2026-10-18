# LibrePods 기능 감사 및 EchoPods 이식 범위

감사 기준일: 2026-10-16

## 조사한 공식 자료

- [LibrePods 공식 저장소](https://github.com/librepods-org/librepods)
- [공식 Android README](https://github.com/librepods-org/librepods/blob/main/android/README.md)
- [Google Play 등록 정보](https://play.google.com/store/apps/details?id=me.kavishdevar.librepods)

LibrePods는 Android에서 BLE 광고만 읽는 배터리 앱이 아니라, AirPods의
비공개 AAP/ATT 경로를 이용해 기기 설정까지 변경하는 프로젝트다. 따라서 이
앱의 기본 배터리·착용 감지 경로와 AAP 제어 경로를 분리한다. AAP 제어는
Android audio route와 실제 AAP L2CAP READY 상태가 확인된 경우에만 전송한다.

LibrePods는 GPL-3.0 라이선스이므로 소스 코드와 Apple/LibrePods asset을 이
프로젝트에 복사하지 않았다. 아래 AACP identifier와 packet shape는 공개된
상호운용성 관찰 결과를 바탕으로 독립적으로 구현했으며, 실제 기기 Gate가
통과하기 전에는 모든 AirPods firmware에서 동작한다고 표시하지 않는다.

## 기능 전체 목록과 현재 처리

| LibrePods 기능 | EchoPods 상태 | 구현/제한 |
|---|---|---|
| AirPods 5 모델 식별/제품 모양 fallback | 이번 변경 | LibrePods 최신 Play listing의 AirPods 5 지원을 반영; 공개 모델 코드가 확인되기 전에는 수동/이름 기반 식별과 4세대 계열 모양 fallback |
| 정확한 L/R/Case 배터리 | 기존 구현 + AAP 0x0004 | AAP exact, BLE coarse fallback; freshness와 last-known 유지 |
| 케이스/착용 감지 | 기존 구현 | AAP 0x0006 우선, BLE fallback, stale/unknown 분리 |
| 미디어 자동 일시정지/재생 | 기존 구현 | Wear state와 MediaSession을 분리하고 한쪽 착용도 정책에 포함 |
| 자동 팝업/케이스 오픈 팝업 | 기존 구현 | overlay 권한이 없으면 Activity/notification fallback |
| 모델별 제품 artwork | 기존 구현 | 현재 모델 catalog와 layered motion renderer 사용 |
| 소음 제어 모드 | 이번 변경 | AAP 0x0009 + identifier 0x0D: 끔/ANC/주변음/적응형 |
| 소음 제어 모드 구성 | 이번 변경 | identifier 0x1A bit mask |
| 대화 인식 | 이번 변경 | identifier 0x28; 기기/firmware 응답 확인 필요 |
| 자동 연결 | 이번 변경 | identifier 0x20; Android Bluetooth 연결 자체는 OS 정책의 영향도 받음 |
| 한쪽 ANC | 이번 변경 | identifier 0x1B; AirPods model/firmware가 지원해야 함 |
| 끔 모드 허용 | 이번 변경 | identifier 0x34 |
| 최적화 충전 | 이번 변경 | identifier 0x3B; 배터리 수치가 아니라 AirPods 내부 설정 |
| 수면 감지 | 이번 변경 | identifier 0x35; firmware 지원 필요 |
| 적응형 음량 | 이번 변경 | identifier 0x26; firmware 지원 필요 |
| 자동 착용 감지 설정 | 이번 변경 | identifier 0x0A; 앱의 media-control toggle과 별도 |
| 마이크 자동/항상 L/항상 R | 이번 변경 | identifier 0x01 |
| 누르기 속도/길게 누르기 시간 | 이번 변경 | identifier 0x17/0x18 |
| 스와이프로 볼륨/속도 | 이번 변경 | identifier 0x25/0x23 |
| 케이스·AirPods 알림음 크기 | 이번 변경 | identifier 0x1F |
| 통화 자동 응답/통화 제어 | 이번 변경 | identifier 0x1E/0x24; 통화 앱별 검증 필요 |
| 길게 누르기 동작 사용자 지정 | 코드 구현·실기기 미검증 | 0x0019 press event를 엄격히 파싱하고 0x39 bit 3으로 host intercept; 좌/우 별 media next/previous/play-pause, assistant, noise-mode 순환. Android AirPods route 없으면 firmware 기본 동작으로 되돌림 |
| AirPods 이름 변경 | 이번 변경 | AAP 0x1A session command; READY session에서만 전송 |
| Head Gestures | 실험 구현·실기기 미검증 | 선택 설정 + 전화 권한; 수신 전화 중에만 AAP 0x17 orientation을 수집하고 fresh frame 안정화 후 끄덕임=응답, 좌우 흔들기=거절. 같은 AirPods Android audio route와 동일 AAP profile을 재검증 |
| Head-tracked Spatial Audio | 미지원/조건부 | Android stereo spatialization과 AirPods head-tracking을 별도 검증해야 함 |
| Loud Sound Reduction | 루트/제조사 연동 필요 | VendorID spoofing/ATT hook 조건; 현재 기본 앱에서 차단 |
| Transparency 사용자 조정/EQ | 루트/ATT 조건 | 비공개 ATT characteristic; 안전한 공용 경로가 없어 차단 |
| Hearing Aid/Conversation boost | 루트/firmware 조건 | identifier는 문서화했지만 임의 payload 전송은 하지 않음 |
| Heart Rate Monitoring | 실험적 | rewrite branch와 특정 신형 AirPods 조건; 현재 기본 앱에서 차단 |
| 고품질 양방향 audio | WIP/미지원 | Android audio route와 codec 경로를 별도 구현해야 함 |
| Multi-device connectivity | 조건부 | Android Bluetooth ownership/Apple smart-routing protocol 미완성 |
| Find My | 미지원 | 공식 구현 경로와 권한·네트워크 조건이 별도이며 아직 구현하지 않음 |
| Root module의 시스템 widget/icon | 미지원 | root/Xposed 전용 확장 기능; 기본 앱의 권한 범위를 넘음 |
| Quick Settings noise mode | 구현 | AAP READY와 실제 Galaxy audio route가 모두 확인될 때만 활성; OFF는 AirPods가 허용했다고 보고한 경우만 순환 |

## 지원 tier

- `AAP_L2CAP_EXACT`: AAP handshake/READY와 Android audio route가 확인된 뒤
  제어 packet을 보낼 수 있는 상태.
- `AAP_PENDING`: Android A2DP/HEADSET는 연결됐지만 AAP L2CAP가 아직 READY가
  아닌 상태. 제어를 전송하지 않는다.
- `BLE_ONLY`: 주변 광고와 coarse battery만 확인된 상태. iPad 연결을 Android
  연결로 승격하지 않는다.
- `ROOT_OR_VENDOR_HOOK_REQUIRED`: VendorID spoofing, Xposed 또는 제조사
  특권 API가 필요하다.
- `EXPERIMENTAL`: 공개된 opcode는 있으나 firmware별 payload/응답을 실기기에서
  확인하지 못한 상태.

## 이번 변경의 검증 경계

이번 변경은 v0.4.20 코드 기준이다. Debug unit test 128개와 release unit test 128개가
모두 통과했고, `lintRelease`와 `assembleRelease`도 성공했다. APK는 `com.galaxyairpods`,
versionCode 42, versionName 0.4.20, minSdk 29, targetSdk 33이며, 직전 공개 APK와 서명
인증서 SHA-256이 같다. 연결 가능한 Galaxy/AirPods 기기와 emulator가 없어 실제
Bluetooth frame 수신, 전화 응답/거절, stem firmware intercept Gate는 통과로 표시하지 않는다.

폰이 연결되지 않은 환경에서는 다음을 자동 검증했다.

1. control packet padding/길이, stem press length/type/side, head-orientation frame 길이/센서 marker
2. stale/모호한 head-motion 샘플이 전화 동작을 일으키지 않는지
3. long-press customization mask와 OFF listening-mode 허용 조건
4. AAP profile ID 불일치 시 session command가 거부되는지
5. Android audio route가 없거나 identity가 맞지 않으면 설정·Quick Settings·gesture 동작을 막는지

아래는 여전히 구현하지 않았거나 별도 root/firmware/프로토콜 작업이 필요한 범위다.
VendorID/Xposed가 필요한 Loud Sound Reduction·Hearing Aid·Transparency 세부 조정,
Find My, Bluetooth multipoint, 시스템 spatializer/HRTF 연결, 신형 AirPods HRM,
고품질 AACP 마이크 스트림이 이에 해당한다. 이들은 코드 빌드 성공만으로 지원한다고
표시하지 않는다. 실제 AirPods가 명령을 수락하는지와 Samsung/Fold7 조합의 동작은
실기기 adb/logcat raw evidence를 확보한 뒤에만 통과로 보고한다.
