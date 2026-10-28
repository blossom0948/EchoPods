# GitHub 배포와 앱 내 업데이트

## 사용자가 할 일

1. 공개할 `foss` APK를 안정적인 배포 서명 키로 빌드합니다.
2. GitHub에서 `v버전명` 형식의 Release를 만들고, APK를 정확히 `app-foss-release.apk`라는 이름으로 첨부해 게시합니다.
3. Release 게시 후 `Update in-app release metadata` 작업이 자동 실행되어 APK SHA-256과 다운로드 주소를 `update.json`에 반영합니다.

앱은 `update.json`의 더 높은 `versionCode`를 발견하면 APK를 내려받고 해시·패키지·버전을 검사한 뒤 Android 설치 화면을 요청합니다. 앱을 열 때 확인하고, AirPods 백그라운드 서비스가 실행 중이면 30분 간격으로도 확인합니다. 서비스가 중지된 동안에는 주기 확인이 보장되지 않으며, 다음 앱 실행 때 다시 확인합니다. 재실행해도 같은 릴리스 메타데이터라면 작업은 안전하게 끝나며, 낮거나 같은 버전 코드로 다른 파일을 덮어쓰지는 않습니다. 워크플로에는 `contents: write`만 명시해 `update.json` 갱신에 필요한 최소 권한만 줍니다. 저장소/조직 정책이나 `main` 브랜치 보호 규칙이 이를 제한하면 GitHub Actions 설정에서 해당 쓰기를 허용해야 합니다.

릴리스 APK는 현재 사용자 설치본과 동일한 서명 인증서로 서명되어야 하고 `versionCode`를 올려야 합니다. 이 저장소에는 FOSS 배포 키를 GitHub Actions에 넣지 않았으므로 APK 빌드·서명·Release 첨부 자체는 자동화하지 않습니다. 특히 Android 기본 debug 키를 CI 배포용 키로 복사하거나 새 키로 바꾸지 마세요. 서명이 바뀌면 기존 앱을 업데이트할 수 없을 수 있습니다.

## 간단 다운로드 페이지

`site/`의 정적 페이지는 GitHub Pages의 Actions 배포 작업으로 게시합니다. 저장소 관리자가 한 번만 다음 설정을 해야 합니다.

`Settings → Pages → Build and deployment → Source: GitHub Actions`

그 뒤 `main`에 `site/` 또는 Pages workflow 변경을 올리면 `https://blossom0948.github.io/EchoPods/`에서 페이지가 갱신됩니다. 페이지의 다운로드 버튼은 최신 Release의 고정 파일명 APK를 직접 가리킵니다.

## Android 설치 확인

업데이트 발견과 APK 다운로드는 자동화할 수 있지만, GitHub에서 받은 APK의 설치를 모든 Android 기기에서 무인으로 끝낼 수는 없습니다. 처음에는 EchoPods에 ‘이 출처 허용’ 권한을 허용해야 할 수 있습니다. 현재 코드는 과거 sideload 자체 업데이트 실패를 고려해 `USER_ACTION_REQUIRED`를 명시하므로, 설치할 때 Android 확인 화면을 띄웁니다. Android는 조건을 만족하는 일부 자체 업데이트에서 확인을 생략할 수 있지만 기기/설치 출처 조건에 따라 보장되지 않습니다. EchoPods는 이 동작을 아직 기기별로 검증하지 않았고, 확인 절차를 끄지 않습니다. 거부되거나 실패한 설치를 성공으로 처리하지 않습니다. [Android PackageInstaller 안내](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))를 참고하세요.
