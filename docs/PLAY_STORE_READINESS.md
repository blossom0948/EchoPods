# EchoPods Google Play 준비 상태

이 문서는 Play 배포 변형과 GitHub 배포 변형의 차이, 제출 전에 남은 검증을 기록합니다. 빌드가 된다는 사실만으로 Google Play 심사 통과를 보장하지 않습니다.

## 배포 변형

- `foss`: GitHub 등에 배포하는 APK용입니다. 기존 앱 내 업데이트 확인/다운로드 흐름을 유지합니다.
- `play`: Google Play 배포용입니다. 앱 자체 APK 업데이트 권한과 설치 수신기를 제외하고, 업데이트 확인·다운로드·설치를 실행하지 않습니다. 앱 업데이트는 Google Play가 관리합니다.
- Play 변형은 일반 배포 앱에 허용되지 않는 시스템 전용 권한(`BLUETOOTH_PRIVILEGED`, `MODIFY_PHONE_STATE`, `LOCAL_MAC_ADDRESS`, `INTERACT_ACROSS_USERS`)도 요청하지 않습니다. 시스템 앱/Xposed 전용 통합은 Play 빌드에서 보장하지 않으며 FOSS 변형에 남아 있습니다.
- 개인정보 안내의 업데이트 설명도 변형별로 구분합니다. Play 버전은 GitHub 업데이트 확인을 하지 않는다고 표시하고, GitHub 링크는 사용자가 선택한 경우에만 연다고 안내합니다.
- 두 변형은 기존 사용자 데이터와 앱 업데이트 호환성을 위해 `com.galaxyairpods` application ID를 공유합니다. 같은 패키지 ID만으로 업데이트 호환성이 생기는 것은 아니며, 설치된 앱과 스토어 앱의 서명 인증서도 일치해야 합니다.

## 빌드

Play 변형의 디버그 AAB는 패키징 및 병합 매니페스트 점검용일 뿐, 업로드용이 아닙니다. 공개용 `fossRelease` APK와 `playRelease` AAB 모두 안정적으로 보관하는 프로덕션 키가 필요합니다. 이 저장소에 있던 로컬 `app-foss-release.apk`는 `Android Debug` 인증서(SHA-256 `669674a2114fb59d85c56bba774b89fec9ce7f7ee79825769826130362a091c9`)로 서명되어 있어 공개 배포에 사용하면 안 됩니다. 이후 공개 Release 빌드는 키가 설정되지 않으면 실패하도록 했습니다.

```sh
./gradlew :app:bundlePlayDebug
```

업로드용 AAB는 Play App Signing에 등록할 업로드 키로 서명해야 합니다. 저장소에 키나 비밀번호를 넣지 말고, 개발 환경의 `local.properties`에 다음 속성을 설정합니다.

```properties
RELEASE_STORE_FILE=/absolute/path/to/upload-key.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

설정한 뒤 다음을 실행합니다.

```sh
./gradlew :app:bundlePlayRelease
```

### 서명 키 보안 권고 및 업데이트 호환성

공개 v0.5.3/v0.5.9 APK와 이 개발 환경의 기본 debug keystore가 같은 `CN=Android Debug` 인증서를 사용합니다. 이 인증서는 기본 Android 개발용 키이므로 보안이 필요한 배포용 앱 서명 키로 재사용하지 않는 것을 권장합니다. 권장 기본안은 Google Play가 보관하는 별도 앱 서명 키와, 강한 비밀번호로 보호·백업한 별도 업로드 키입니다. 다만 서명 인증서가 바뀌면 기존 사이드로드 앱을 일반 업데이트로 덮어쓸 수 없을 수 있습니다. 서명 호환이나 1회 재설치 필요 여부를 확인하지 않은 채 “기존 설치에서 바로 업데이트된다”고 약속하지 않습니다.

현재 `com.galaxyairpods` 패키지명은 Google Play 밖에서도 이미 사용됐습니다. 2026년 Android 개발자 검증 안내상 기존 패키지명은 소유한 개인 키 증명이 요구될 수 있고, 허용 가능한 인증서는 Play Console에서 확인해야 합니다. 기본 debug 인증서로 키 소유 증명을 할 수 있다는 점과 그 키를 앱 서명에 쓰는 것이 안전하다는 판단은 별개입니다. Play Console 관리자가 해당 패키지 등록 절차와 키 선택을 확인하기 전에는 production AAB를 서명·제출하지 않습니다. [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756), [앱 서명·키 변경 안내](https://developer.android.com/studio/publish/app-signing), [기존 패키지명 키 선택](https://support.google.com/googleplay/android-developer/answer/16762143)

필수 키가 없거나 keystore 파일이 없으면 모든 공개 Release 패키징은 의도적으로 실패합니다. GitHub APK와 Play 설치본 사이의 앱 내 업데이트를 이어가려면 Play App Signing의 앱 서명 인증서가 기존 GitHub APK 인증서와 일치해야 합니다. AAB 업로드 키만 일치하는 것으로는 충분하지 않습니다. 현재 확인된 로컬 APK는 Android Debug 인증서이므로 실제 공개 GitHub APK와 Fold 설치본의 인증서도 별도로 확인하고 서명 전환을 결정해야 합니다. 서명이 다르면 기존 설치본을 덮어 업데이트할 수 없어 앱을 제거 후 다시 설치해야 할 수 있고, 앱 내부 데이터가 유지된다고 보장할 수 없습니다.

## 제출 전 남은 작업

- 프로덕션 앱 서명 키를 안전하게 보관하고, Play App Signing의 앱 서명 키와 GitHub APK 인증서의 호환 전략을 확정합니다. AAB 업로드 키와 앱 서명 키는 구분합니다.
- Play Console에서 실제 Play Release 병합 매니페스트와 권한 목록을 다시 확인합니다. 특히 foreground service의 핵심 기능 설명, 사용자 고지, Play Console 선언 및 검토 자료가 필요할 수 있습니다.
- 공개 개인정보처리방침 초안은 [PRIVACY.md](../PRIVACY.md)에 두었습니다. 실제 Play Console Data safety 답변은 Play Review SDK의 사용자가 제출한 리뷰 데이터와 모든 최종 SDK를 다시 확인한 뒤 계정 소유자가 확정해야 합니다.
- 스토어 설명 초안과 필요한 Console declaration 자료는 [PLAY_STORE_SUBMISSION_PACKET.md](PLAY_STORE_SUBMISSION_PACKET.md)에 있습니다. 선택된 20번 아이콘의 512×512 내보내기와 EchoPods 1024×500 기능 그래픽은 준비했습니다. 실제 서명된 Play 빌드의 스크린샷은 아직 필요합니다. Apple 또는 LibrePods의 시스템 화면·로고를 스토어 자료로 사용하지 않습니다.
- 새 개인 개발자 계정에 적용되는 경우, 프로덕션 액세스 전 테스트 트랙 및 테스터 요건을 Play Console에서 확인합니다.
- Fold 계열 및 일반 Android 기기에서 실제 Play 설치본을 설치/업데이트하고 Bluetooth, 백그라운드 실행, 오버레이, 위젯, 배터리 사용량을 검증합니다.

## 2026-10-25 추가 감사 결과

- `settings.xml`에는 AirPods BLE 광고를 식별/복호화하는 IRK 및 암호화 키가 저장됩니다. 기본 Android 백업 규칙은 대부분의 앱 데이터를 포함하므로, `settings.xml`, `packet_logs.xml`, 앱 내부 `logs/`를 Android 클라우드 백업과 기기 간 이전에서 제외하도록 규칙을 명시했습니다. 새 기기에서는 AirPods를 다시 연결해야 할 수 있습니다.
- 앱은 Bluetooth peripheral 광고를 하지 않으며 Android 12 이상만 지원합니다. 따라서 오래된 `BLUETOOTH`, `BLUETOOTH_ADMIN`, 사용하지 않는 `BLUETOOTH_ADVERTISE` 선언 및 onboarding 요청을 제거하고 `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`만 남겼습니다.
- GitHub 문제 제보는 이전에 기기/앱 정보를 URL query로 포함해 브라우저를 열었습니다. 브라우저가 페이지를 여는 순간 query가 GitHub로 전송될 수 있으므로, 지금은 빈 GitHub 제보 양식을 열고 보고서는 사용자가 요청할 때만 클립보드에 복사합니다. 사용자가 붙여넣고 제출해야 GitHub로 갑니다.
- Play 빌드는 Google Play In-App Review를 호출합니다. 사용자가 제출한 별점/리뷰 문구는 Google Play로 전달되며 공개 또는 closed-test 비공개 공유될 수 있습니다. 이 사실을 앱 내 Play 개인정보 안내 및 공개 `PRIVACY.md`에 추가했습니다. Data safety 폼에도 현재 필드에 맞춰 선언해야 합니다.
- 16 KB 페이지 점검에서 debug AAB의 arm64-v8a/x86_64 native library `LOAD` 세그먼트가 모두 `0x4000` 정렬로 확인됐습니다. signed release bundle 검증은 아직 남았습니다.
- 스토어 설명 초안, connected-device FGS 설명, privacy document, 그래픽/심사 증빙 checklist는 [PLAY_STORE_SUBMISSION_PACKET.md](PLAY_STORE_SUBMISSION_PACKET.md)에 있습니다.
- 공개 GitHub 릴리스 자산을 직접 내려받아 서명을 확인했습니다. v0.5.3과 v0.5.9 APK 모두 `CN=Android Debug`, SHA-256 `669674a2114fb59d85c56bba774b89fec9ce7f7ee79825769826130362a091c`로 서명되어 있습니다. 현재 개발 머신의 `~/.android/debug.keystore` 인증서 지문도 정확히 같습니다. v0.5.9의 공개 APK와 로컬 `app-foss-release.apk`는 파일 해시도 같습니다.
- 이 일치로 기존 GitHub 설치본과 Play 설치본이 같은 서명 키를 쓰도록 할 수 있는 경로는 확인됐지만, 현재 키는 기본 Android 디버그 키/기본 암호를 사용합니다. 이를 보안 검토 없이 프로덕션 앱 서명 키로 보내거나 새 업로드 키와 혼용하지 않습니다. 교차 배포 업데이트를 보존하려면 기존 키를 Play App Signing의 앱 서명 키로 등록하고 별도의 강한 업로드 키를 만들어야 합니다. 이를 선택하지 않고 Google이 새 앱 서명 키를 만들면 기존 사이드로드 설치본이 그대로 업데이트될 것이라고 보장할 수 없습니다. 최종 선택과 Play Console 등록은 앱 소유자가 해야 합니다.
- 코드 감사에서 ViewModel의 Android Service 강참조, low-battery 오버레이의 비공개 `status_bar_height` 리소스 조회, 외부 터치 전용 팝업 리스너의 접근성 lint 오탐을 처리했습니다. Service가 파괴될 때 전역 참조도 비우도록 했고, 오버레이는 동적 `WindowInsets`를 사용합니다.
- 10월 25일 재검증: Play/FOSS 단위 테스트 각각 75개 전부 통과, Play lint task 통과(당시 HTML 보고서 warning 351건), Play debug AAB/APK 생성 및 에뮬레이터 실행 확인. 이 산출물은 `Android Debug` 인증서로 서명되어 업로드용이 아닙니다. `bundlePlayRelease`는 프로덕션 서명 정보가 없어 의도한 guard에서 중단됩니다.
- Android 16 `medium_phone` 에뮬레이터에서 Play debug APK를 설치하고 MainActivity를 실행했습니다. Debug 전용 저전력 알림 미리보기에서 앱 안/홈 화면 위 오버레이를 표시하고 스크린샷을 확인했으며 앱 crash와 inset 갱신 오류 로그는 없었습니다. 이는 Samsung/AirPods 실기기 기능 검증이나 스토어 스크린샷을 대체하지 않습니다.
- 이번 작업 중 ADB에 Fold7이 연결된 것을 확인했지만 기기에는 설치·실행 명령을 보내지 않았습니다. 실기기 Gate는 아직 통과하지 않았습니다.
- 번역/리소스 경고를 추가 정리한 뒤 재검증: Play/FOSS 테스트 각각 75개 통과(실패·오류·건너뜀 0), `lintPlayDebug` 성공(오류 0, 경고 282), `bundlePlayDebug`와 `assemblePlayDebug` 성공. 앱 이름 중복 선언과 중국어 앱 설명의 잘못된 번역 금지 표시를 제거했고, 디버그 미리보기 문자열을 리소스로 옮겼습니다. Google Fonts 인증서 Base64 오타 경고는 데이터 변경 없이 해당 리소스 범위에서만 억제했습니다.
- 최신 디버그 산출물(2026-10-25 main 재검증): AAB SHA-256 `1e4f3aefddb6c5bfb78830b6dc5cb7b56c91872342742b5495a91697d1aea969`, APK SHA-256 `4eac381f496b9c98e9a0e697215bee6533cf66f9483493cddd75a21b55def84d`. APK 서명 검증과 `zipalign -c -P 16` 검증을 최신 APK에서 통과했습니다. 서명자는 여전히 `Android Debug`입니다. 이 AAB 해시에 대한 native ELF LOAD 정렬은 이 검증에서 다시 측정하지 않았으므로, signed release bundle의 16 KB 검증은 제출 전 남아 있습니다. 앱 ID `com.galaxyairpods`, version `0.5.9-debug`/52, min API 31, target API 37이며 이것은 Play 업로드용 Release가 아닙니다.
- 2026년 8월 31일부터 신규 앱 및 업데이트는 Google Play 제출 시 Android 16/API 36 이상을 target해야 합니다. 현재 debug 패키지는 API 37을 target하지만, 제출 시점의 최종 Release manifest와 Play Console 검증이 필요합니다. [공식 Target API 요건](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en)
- 공개 GitHub 저장소의 About 설명과 주제 태그를 EchoPods의 Android companion 용도 및 기기별 지원 차이를 드러내도록 채웠습니다. 과거 릴리스 노트에서 기종/제조사 특정 표현은 가능한 범위에서 일반 Android 기기 표현으로 바꾸되, 실기기 설치 확인과 기능별 검증 미완료 여부는 구분해 유지했습니다. 이 metadata/노트 정리는 앱 바이너리 배포나 소스 push가 아닙니다.
- 공개 `README.md`는 현재 공개 branch의 코드/릴리스 흐름과 맞는 버전으로 남겨 두었습니다. 워크트리의 새 README 초안은 공개되지 않은 Play flavor·release-signing guard와 파일을 참조하므로, 그 앱 소스와 함께 검증·게시하기 전에는 README만 따로 올리지 않습니다.
- Play Debug 및 Release의 병합 매니페스트 생성 작업을 다시 통과시켰습니다. 양쪽 모두 `com.galaxyairpods`, min API 31, target API 37이며, Play Release 매니페스트에는 `REQUEST_INSTALL_PACKAGES`, 시스템 전용 Bluetooth/전화 권한, `UpdateInstallReceiver`가 없습니다. 대신 `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `SYSTEM_ALERT_WINDOW`는 남아 있어 출시 전 Console 선언·검토 적합성을 확인해야 합니다.
- 이 확인 시점 ADB에는 SM-F966N(Fold7, Android 17)이 연결되어 있었고 설치된 앱은 `com.galaxyairpods` 0.5.9-debug / versionCode 52였습니다. 이번 추가 확인은 읽기 전용으로 했으며 설치·실행·설정 변경은 하지 않았으므로 현재 워크트리 빌드나 AirPods 기능의 실기기 동작을 검증한 것은 아닙니다.
- 스토어 그래픽 파일 형식/크기를 재확인했습니다: 앱 아이콘은 RGBA PNG 512×512, 기능 그래픽은 불투명 JPEG 1024×500입니다. GitHub Pages API는 404를 반환해 별도 Pages 사이트는 활성화되어 있지 않습니다. 정리된 공개 진입점은 저장소 페이지와 README입니다.
- 최종 재검증 명령 `:app:testPlayDebugUnitTest :app:testFossDebugUnitTest :app:lintPlayDebug :app:bundlePlayDebug :app:assemblePlayDebug`가 성공했습니다(기존 산출물·분석 결과는 캐시 적중). Play/FOSS 테스트 결과는 각각 75개 통과, lint 오류 0/경고 282이며, Play debug AAB/APK만 생성됩니다. 이들은 Android Debug 키 서명이라 업로드할 수 없습니다.
- 설정의 앱 정보에서 개발자 내부 용어 `Flavor`가 노출되지 않도록 `배포 방식`과 빌드 유형의 현지화 표시로 바꿨습니다. API 36 `medium_phone` 에뮬레이터에서 Play debug APK를 설치·실행하고 iOS 밝은/어두운 모드, Android 밝은/어두운 모드, 설정 최하단 스크롤을 확인했습니다. 앱 정보에는 `Google Play`와 `디버그`가 표시되고 오픈소스 라이선스 행까지 스크롤 가능했습니다. 2,000줄의 AndroidRuntime 오류 로그에서 해당 앱의 crash/ANR 흔적은 없었습니다. 이는 Fold 실기기 또는 AirPods 연결 기능 검증은 아닙니다.
- Play Console에 로그인된 브라우저 세션으로 접속했으나 개발자 대시보드가 아닌 계정 유형 선택/개발자 계정 등록 화면으로 이동했습니다. 계정 종류·약관·등록은 계정 소유자 단계이며 대행하지 않았습니다. 이번 확인에서 Android 디버거나 USB 기기는 연결되어 있지 않았고, release signing 속성 및 keystore도 로컬에 없었습니다.

## 공식 참고자료

- [Google Play 타깃 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Google Play의 패키지 설치 권한 정책](https://support.google.com/googleplay/android-developer/answer/18258653)
- [Android App Bundle 안내](https://developer.android.com/guide/app-bundle)
- [Play App Signing 안내](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Android 앱 서명과 여러 스토어에서 같은 키 사용 안내](https://developer.android.com/studio/publish/app-signing)
- [Data safety 섹션 작성 안내](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Foreground service 선언 요구사항](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Play 스토어 등록 그래픽 규격](https://support.google.com/googleplay/android-developer/answer/9866151)
