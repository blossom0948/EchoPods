# EchoPods — Google Play submission packet

Prepared October 25, 2026. This is local submission material, not a Play Console submission. Verify every declaration against the exact signed release artifact and Play Console before sending for review.

## Product details

- App name: `EchoPods`
- Application ID: `com.galaxyairpods`
- Play flavor: `play`
- Minimum Android version: Android 12 / API 31
- Current app version: `0.5.9` / version code `52`
- Target API: 37
- Privacy policy URL after this file is published: `https://github.com/blossom0948/EchoPods/blob/main/PRIVACY.md`
- Ads: none found in the source/dependency audit.
- Login/access: no account or sign-in is required. Reviewers need a supported AirPods model for Bluetooth-dependent behavior; no Bluetooth behavior can be represented as hardware-tested when the reviewer has no compatible device.

## Draft Korean store listing

**Short description**

Android에서 AirPods 배터리와 지원되는 기능을 확인하세요.

**Full description**

EchoPods는 Android에서 AirPods의 배터리와 연결 상태를 확인하고, 기기 조합이 지원하는 제어 기능을 사용할 수 있도록 돕는 독립 앱입니다.

주요 기능

- 왼쪽·오른쪽 이어버드와 케이스 배터리 표시
- 주변 감지, 케이스 상태, 연결 팝업 및 배터리 부족 알림
- 착용 감지와 선택적 미디어 일시정지/재생
- 지원되는 AirPods의 듣기 모드와 설정 제어
- 배터리 및 듣기 모드 홈 화면 위젯
- 일부 모델과 Android Bluetooth 스택에서 머리 동작 등 추가 제어

기능 지원은 AirPods 모델과 펌웨어, Android 버전, 휴대전화 Bluetooth 칩셋 및 제조사 소프트웨어에 따라 달라집니다. 일부 기기에서는 기본 Bluetooth 오디오만 가능하고 세부 제어는 제공되지 않을 수 있습니다. 배터리, 착용 감지, 케이스 상태 및 미디어 자동 제어가 모든 기기 조합에서 보장되지는 않습니다. 앱 안의 현재 연결 상태와 기능 지원 안내를 확인하세요.

EchoPods는 Apple과 제휴하거나 Apple이 보증하는 앱이 아닙니다. AirPods는 Apple Inc.의 상표입니다.

## Draft English store listing

**Short description**

Check AirPods battery and supported features on Android.

**Full description**

EchoPods helps you check AirPods battery and connection status on Android and use controls supported by your specific device combination.

Features include:

- Separate left, right, and case battery indicators
- Nearby detection, case status, connection popups, and low-battery alerts
- Optional wear detection and media pause/resume
- Listening-mode and setting controls for supported AirPods
- Battery and listening-mode home-screen widgets
- Additional controls, such as head gestures, on some models and Android Bluetooth stacks

Support depends on the AirPods model and firmware, Android version, phone Bluetooth chipset, and manufacturer software. Some phones may provide standard Bluetooth audio without advanced AirPods controls. Battery, wear detection, case status, and automatic media control are not guaranteed for every device combination. Check the connection and capability information shown in the app.

EchoPods is independent and is not affiliated with or endorsed by Apple. AirPods is a trademark of Apple Inc.

## App content and permission declarations to prepare

### Connected-device foreground service

- **Feature:** With background detection enabled by the user, `AirPodsService` maintains a Bluetooth control session with the selected AirPods so it can receive supported battery, case, and wear events, update the widgets/notifications, and provide the requested controls while another app is visible. The ongoing notification indicates that the service is active.
- **If delayed or interrupted:** AirPods audio remains under Android's Bluetooth system, but EchoPods may miss live battery/case/wear updates, popups, or optional media automation until the service resumes or the user opens the app.
- **Evidence needed:** Record a real-device video showing the user enabling the feature in EchoPods, connecting supported AirPods, leaving the app, then receiving an actual event/update. Do not use a mockup or imply universal model support.

### Display-over-other-apps special access

Justification: this optional access is used only to display the user's selected compact connection/low-battery overlay above the current app. If denied, EchoPods uses its in-app/notification fallback where available. Provide an in-app demonstration and make the permission explanation visible before sending the user to Android Settings.

### Phone permissions

`READ_PHONE_STATE` detects call-state transitions for call-aware gesture behavior. `ANSWER_PHONE_CALLS` is used only when the user enables the incoming-call head-gesture feature and grants the permission. In the Console review notes, show the setting and the incoming-call flow, and explain that call-control behavior is optional. Keep both permissions only while those shipped features remain enabled and accurately described.

### Bluetooth permissions

The app needs `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`. The redundant legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` and unused `BLUETOOTH_ADVERTISE` permissions were removed because the app targets Android 12+ and does not advertise as a Bluetooth peripheral.

## Data safety draft — verify in the Play Console form

- No EchoPods backend, account, ad SDK, analytics SDK, or automatic diagnostics upload was found in the Play source/dependency audit. While the Bluetooth service runs, the app does automatically retain up to 1,000 local packet-log metadata entries (source label and packet byte count only; not packet contents). Full Android logcat capture is an explicit troubleshooting action, saved locally, and may contain system/app details that users should review before sharing.
- Bluetooth observations, AirPods state, app settings, call state, and media-session state are processed locally by the app. The release backup rules exclude the settings file (which contains AirPods proximity/encryption keys), packet-log preferences, and saved diagnostic logs from cloud backup and device-to-device transfer.
- The Play build does not use the GitHub APK updater. A user-initiated repository link may open a browser, which contacts GitHub.
- The Play In-App Review SDK is invoked by the Play build. If the user submits a rating/comment, Google Play receives the user-entered review and may publish it or share it privately in a closed test. Include this SDK-handled review data in the Data safety answer according to the current Console field definitions and Google's [In-App Review disclosure](https://developer.android.com/guide/playcore/in-app-review#data-safety).
- The app copies an issue report to the local clipboard only after the user requests it; the report is not embedded in a GitHub URL. The user must paste and submit it in GitHub. The report may contain the user's text plus app/device/Android version details.
- Google requires developers to disclose third-party SDK handling and makes the developer responsible for the accuracy of the Data safety answer. Recheck all resolved SDKs and the actual release package before submitting.

## Store graphics and screenshots

The app icon selected for the app is concept `20`; its 512×512 transparent PNG export is `assets-source/play-store/echopods-icon-512.png` (43,280 bytes). Inspect the final mask-safe crop in Play Console. The Korean 1024×500 feature graphic source is `assets-source/play-store/feature-graphic-ko.svg`; the store-ready export is `assets-source/play-store/feature-graphic-ko.jpg` (opaque JPEG, 1024×500). The earlier RGBA PNG is a working export only because Play requires JPEG or 24-bit PNG without alpha for this asset. Real screenshots from the current signed Play build are still needed. Recommended screenshots: connection/battery status, a supported listening-mode control, the supported capability/help states, and the home-screen widgets. Avoid Apple or LibrePods screenshots/logos and don't stage sample battery values as live telemetry.

## Items requiring the owner or Play Console

- Create/select the Play Console app and provide the required developer contact email and account/legal details.
- Choose the Play signing strategy. The public v0.5.3 and v0.5.9 APKs and the current local debug keystore all use the same `CN=Android Debug` certificate (SHA-256 `669674a2114fb59d85c56bba774b89fec9ce7f7ee79825769826130362a091c`). This preserves a possible cross-store update path if that exact existing key is selected as the Play app-signing key, with a separate strong upload key. The existing keystore has Android's conventional debug credentials and must not be treated as a production key without a deliberate security decision. A Google-generated app-signing key will not automatically match existing sideload installs.
- Supply the stable upload keystore/passwords through local `local.properties` or a secure CI secret store. Never commit them.
- Complete Data safety, content rating, target audience, app access, ads, and foreground-service/permission declarations in Play Console.
- Record the real-device videos required for foreground-service and special-permission review.
- Upload approved graphics and screenshots and confirm the final package/permission report.
- If the developer account is a personal account created after November 13, 2023, complete the required closed test with at least 12 continuously opted-in testers for 14 days before applying for production access.
- Review GitHub licensing/NOTICE and confirm the source corresponding to the Play binary is published under the intended license.

## Current local verification

- `:app:bundlePlayDebug` and `:app:assemblePlayDebug`: pass. Current inspection artifacts are `app/build/outputs/bundle/playDebug/app-play-debug.aab` (SHA-256 `82c38f532f7c2bf1e9529953fad5ceea8eb61883cded384d130d80d0b5e30901`) and `app/build/outputs/apk/play/debug/app-play-debug.apk` (SHA-256 `4eac381f496b9c98e9a0e697215bee6533cf66f9483493cddd75a21b55def84d`). Both are debug-signed; do not upload them. APK signature verification and 16 KB `zipalign -c -P 16` validation pass.
- `:app:testPlayDebugUnitTest` and `:app:testFossDebugUnitTest`: 75 tests each, zero failures/errors/skips.
- `:app:lintPlayDebug`: task passes with 282 warnings and zero errors. Four actionable findings were removed (ViewModel's strong Service reference, hidden status-bar resource lookup, two resulting inset API warnings, and an outside-touch-only accessibility false positive); the media-session callback also now uses Android's canonical playback-state constants. A later resource pass removed duplicate app-name entries, corrected Chinese translation metadata, localized debug preview strings, and suppressed false-positive typo warnings only on Google Fonts certificate Base64 arrays. Remaining warnings are dominated by unused resources, icon placement, and dependency suggestions; they still need release-specific triage but were not blindly deleted/upgraded.
- `:app:bundlePlayRelease`: intentionally fails until the stable production/upload keystore properties are configured. No production key was generated or committed.
- The Play merged manifest must be checked after each manifest change. It must not request `REQUEST_INSTALL_PACKAGES` or system-only Bluetooth/phone permissions.
- The Android 16 `medium_phone` emulator installed and launched the Play debug APK. The debug low-battery preview also displayed its overlay over both the app and emulator home screen with no inset-update warning or app crash. This is not a Samsung/AirPods hardware test and the screenshots are not valid store screenshots.
- No Play Console upload or public release was performed. The Fold7 was not sent install or interaction commands; no Samsung/AirPods runtime result is claimed.
- The prior 16 KB ELF check applied to the earlier debug AAB only. Recheck native `LOAD` segment alignment on the final signed Play AAB before submission.
