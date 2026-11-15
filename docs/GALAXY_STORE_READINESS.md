# Galaxy Store 제출 준비 — EchoPods

현재 상태: Galaxy 전용 release APK/AAB를 로컬에서 생성하고 서명·에뮬레이터 설치를 확인했습니다. Seller Portal 계정, 상업 판매자 승인, 앱 등록, 업로드, 심사는 아직 진행하지 않았습니다.

## 빌드 변형

| 변형 | 배포 방식 | 앱 내 업데이트 | 앱 ID |
|---|---|---|---|
| `foss` | GitHub APK | GitHub 확인·다운로드 가능 | `com.galaxyairpods` |
| `play` | Google Play AAB | Google Play 관리 | `com.galaxyairpods` |
| `galaxy` | Galaxy Store APK | Galaxy Store 관리 | `com.galaxyairpods` |

Galaxy Store는 앱 내부에서 자동 업데이트를 시작하거나 앱 다운로드를 제공하는 것을 금지합니다. `galaxy` 변형은 자동 확인·다운로드·설치 경로를 비활성화하고 `REQUEST_INSTALL_PACKAGES`, 업데이트 receiver, 시스템 전용 Bluetooth/전화 권한을 최종 매니페스트에서 제외합니다. Play 전용 리뷰 SDK는 Play 변형에만 포함됩니다.

현재 앱 ID는 세 배포 변형에서 동일합니다. Galaxy Store에 기존 GitHub 사용자가 설치된 앱을 업데이트하려면 최종 서명 인증서가 기존 설치 인증서와 일치해야 합니다. 프로젝트 기록상 공개 v0.5.3/v0.5.9 APK는 널리 알려진 `Android Debug` 인증서를 사용했습니다. 이 키를 정식 배포에 재사용하지 않습니다. 안전한 프로덕션 키로 이전하면 기존 설치는 서명 불일치로 덮어쓸 수 없으며, 한 번 제거 후 재설치해야 할 수 있습니다. 이 앱은 일부 로컬 데이터를 백업에서 제외하므로 제거 시 설정 및 AirPods 암호화 키가 사라질 수 있습니다. 첫 제출 전에 기존 사용자 이전 방법과 Samsung의 APK 서명 처리 방식을 Seller Portal에서 검증해야 합니다.

## 빌드 및 검증 명령

서명 없는 디버그 점검:

```sh
./gradlew :app:testGalaxyDebugUnitTest :app:lintGalaxyDebug :app:bundleGalaxyDebug :app:assembleGalaxyDebug
```

정식 Galaxy APK와 AAB (이 Mac에 생성된 키 필요):

```sh
bash scripts/build-galaxy-store-release.sh
```

Galaxy 전용 배포 키는 저장소 바깥 `~/.android/echopods-galaxy-signing/echopods-galaxy-upload.jks`에 있고 비밀번호는 macOS Keychain의 `EchoPods Galaxy Store Release Signing` 항목에서 가져옵니다. 별칭은 `echopods-galaxy-upload`, 인증서 SHA-256 지문은 `BB:E6:10:3B:BD:9D:DF:37:7E:D8:67:7F:11:50:02:4B:42:FB:29:32:3B:C3:AC:38:C6:60:F1:45:D7:3E:B5:A9`입니다. Gradle은 `ECHOPODS_RELEASE_*` 환경 변수도 지원하며, 일반 `local.properties` 설정은 기존 호환성을 위해 유지됩니다. 빌드 스크립트는 Keychain에서 암호를 받아 사용합니다. Samsung은 AAB의 서명 키를 Galaxy Store가 관리하도록 요구하므로 Seller Portal에서 이 인증서를 키로 등록/선택해야 합니다. 등록 과정에서 Samsung-managed key를 새로 선택하면 실제 설치 인증서가 달라질 수 있으니 이후 업데이트와 교차 스토어 호환성 정책을 확인하고 최초 키 선택을 고정하세요. 이 키를 분실하면 향후 업데이트가 막힐 수 있으니 keystore 파일을 오프라인 안전 위치에 백업하고 Keychain도 안전하게 보관합니다. 키와 비밀번호는 저장소에 커밋하지 않습니다.

현재 정식 산출물은 버전 `0.5.9` / 코드 `52`입니다. APK는 약 9.4 MB, AAB는 약 10 MB입니다. APK 서명 검증과 AAB 서명 검증을 통과했고 둘 다 위 SHA-256 인증서를 사용합니다. 정식 APK는 Android 16 에뮬레이터에 설치되어 런처에서 시작됐으며 5초간 FATAL EXCEPTION/ANR은 관찰되지 않았습니다. 이는 Samsung 실기기나 AirPods Bluetooth 기능 검증을 뜻하지 않습니다.

기본 구성은 앱 ID `com.galaxyairpods`, 버전 `0.5.9` / 코드 `52`, 최소 Android API 31, target API 37입니다. Samsung의 현재 FAQ는 target API 33 이상과 64비트 바이너리를 요구합니다. 제출 직전 실제 정식 산출물에서 ABI와 최종 매니페스트를 다시 확인합니다.

## 상품 등록 자료

### 한국어 초안

**앱 이름:** EchoPods
**짧은 설명:** Android에서 AirPods 배터리와 지원되는 기능을 확인하세요.

**자세한 설명:**

EchoPods는 Android에서 지원되는 AirPods의 배터리와 연결 상태를 확인하고, 기기 조합에서 제공되는 제어 기능을 사용할 수 있도록 돕는 독립 앱입니다.

- 왼쪽·오른쪽 이어버드와 케이스 배터리 상태
- 주변 감지, 케이스 상태, 연결 팝업 및 배터리 부족 알림
- 선택형 착용 감지와 미디어 제어
- 지원되는 모델의 듣기 모드와 기기 설정
- 배터리 및 듣기 모드 홈 화면 위젯

지원 범위는 AirPods 모델과 펌웨어, Android 버전, 휴대전화의 Bluetooth 칩셋 및 제조사 소프트웨어에 따라 다릅니다. 일부 기기에서는 기본 Bluetooth 오디오만 가능하며 배터리, 케이스, 착용 감지 또는 고급 제어가 제공되지 않을 수 있습니다. 앱에 표시되는 연결 상태와 기능 지원 정보를 확인하세요.

EchoPods는 Apple과 제휴하거나 Apple의 보증을 받은 앱이 아닙니다. AirPods는 Apple Inc.의 상표입니다.

### 영어 초안

**Short description:** Check AirPods battery and supported features on Android.

**Full description:**

EchoPods is an independent Android companion for checking AirPods battery and connection status and using controls supported by your device combination.

- Separate left, right, and case battery status
- Nearby and case status, connection popups, and low-battery alerts
- Optional wear detection and media controls
- Listening modes and device settings on supported models
- Battery and listening-mode home-screen widgets

Support depends on the AirPods model and firmware, Android version, phone Bluetooth chipset, and manufacturer software. Some devices may provide standard Bluetooth audio without battery, case, wear-detection, or advanced controls. Check the connection and capability information shown in the app.

EchoPods is independent and is not affiliated with or endorsed by Apple. AirPods is a trademark of Apple Inc.

### 그래픽과 스크린샷

- 등록 아이콘: Android용 PNG 512×512px, 최대 1MB. 현재 후보는 `assets-source/play-store/echopods-icon-512.png`; 제출 전 Samsung Seller Portal 미리보기에서 마스크/여백을 확인합니다.
- 등록 스크린샷: 4~8장 필수. JPG/PNG, 각 변 320~3840px, 가로세로 비율 최대 2:1. 실제 Galaxy 기기에서 촬영한 최신 화면으로 준비해야 하며, 배터리 예시값이나 다른 앱의 화면을 실제 기능처럼 꾸며 넣지 않습니다.
- 앱 그래픽/스크린샷은 실제 앱과 기능을 정확히 보여야 합니다. Apple 시스템 팝업·LibrePods 화면이나 상표 자산은 사용하지 않습니다.

### 개인정보·심사 안내

- 공개 개인정보처리방침: https://github.com/blossom0948/EchoPods/blob/main/PRIVACY.md
- 앱 로그인은 필요하지 않습니다. 검토자는 실제 AirPods 없이 Bluetooth 전용 기능을 검증할 수 없으므로, 기능 지원 범위와 기본 화면 진입 방법을 심사 메모에 설명합니다.
- 최종 지원 이메일과 판매자 공개 정보는 계정 소유자가 결정·입력해야 합니다.
- Seller Portal 데이터 안전 항목, 연령 등급, 국가/지역, 카테고리, 권한 설명은 정식 APK/AAB의 실제 동작을 확인한 뒤 작성합니다.
- Samsung 배포 지침은 앱 기능과 디자인의 고유성을 요구합니다. GPL-3.0-or-later 라이선스 및 LibrePods 기반 코드 출처는 공개하되, EchoPods 브랜드·스크린샷·설명은 독립 제품으로 정확하게 제시해야 합니다. 심사 통과는 보장할 수 없습니다.

## 소유자 계정 단계

1. Samsung Account로 [Seller Portal](https://seller.samsungapps.com/)에 등록합니다.
2. 무료 앱을 배포하려면 개인 상업 판매자 상태 승인도 필요합니다. Samsung은 가입비/연회비를 받지 않지만 본인·정산 정보 및 계정 검증을 요구할 수 있습니다.
3. 국가·지역과 판매자 연락처를 확정합니다. 이 정보는 앱 상세 페이지에 노출될 수 있으므로 개인 정보 사용 여부를 확인합니다.
4. 최초 제출은 Galaxy Store의 공개 심사 전 비공개/베타 절차가 가능한지 Seller Portal에서 확인하고, 키·설치·업데이트 전환을 실제 Fold 기기에서 검증합니다.

## 현재 검증 요약

- `:app:testGalaxyDebugUnitTest`: 76 tests, 0 failures
- `:app:testPlayDebugUnitTest`: 75 tests, 0 failures
- `:app:testFossDebugUnitTest`: 75 tests, 0 failures
- `:app:lintGalaxyDebug`: 0 errors, 282 warnings (기존 코드/의존성 경고 포함)
- `:app:bundleGalaxyRelease` 및 `:app:assembleGalaxyRelease`: 성공
- Release APK 설치 및 첫 화면 실행: Android 16 AVD에서 통과. 실제 Galaxy/AirPods 연결 gate는 미수행.
- APK 매니페스트에서 `REQUEST_INSTALL_PACKAGES`, `BLUETOOTH_PRIVILEGED`, `MODIFY_PHONE_STATE`, `LOCAL_MAC_ADDRESS`, `INTERACT_ACROSS_USERS` 및 Play Review 의존성을 찾지 못함.

## 공식 참고자료

- [Galaxy Store 등록 및 AAB 서명 안내](https://developer.samsung.com/galaxy-store/launch.html)
- [Galaxy Store FAQ — 수수료, target API, 64비트 및 스크린샷 규격](https://developer.samsung.com/galaxy-store/faq.html)
- [Samsung 앱 배포 정책](https://developer.samsung.com/galaxy-store/distribution-guide.html?lang=en)
- [Galaxy Store 등록 API — 아이콘·스크린샷 규격](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/reference.html)
