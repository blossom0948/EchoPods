# AirPods Galaxy

AirPods Galaxy is a free Android app built by porting the Android application from [LibrePods](https://github.com/librepods-org/librepods). The app keeps its own name, package ID (`com.galaxyairpods`), icon, and update channel while carrying over LibrePods' Android screens and Bluetooth/control implementation.

The port is licensed under **GPL-3.0-or-later**. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for attribution and asset notes. This project is not affiliated with or endorsed by Apple or the LibrePods project.

## Features

The FOSS build exposes all app-level settings without a paid-feature gate, including:

- AirPods battery and charging information, device naming, and model-specific product artwork.
- Listening-mode controls (Off, Transparency, Adaptive, and Noise Cancellation), per-earbud press-and-hold actions, call controls, and Quick Settings controls.
- Ear detection, head gestures, conversational awareness, personalized volume, accessibility settings, and supported audio customizations.
- Battery and noise-control widgets, notifications, connection handoff, and background monitoring.
- GitHub update checks and an in-app download/install flow for AirPods Galaxy releases.

This is feature parity at the application/source level, not a promise that every AirPods firmware feature works on every Android device. Available controls depend on AirPods model/firmware, Android Bluetooth stack, Android version, and sometimes root/Xposed. Vendor-ID spoofing features require the relevant privileged hook; some items in upstream LibrePods are still experimental, unknown, or not implemented. The project does not claim that Find My, head-tracked spatial audio, heart-rate monitoring, or high-quality two-way audio work universally. Read [LibrePods' Android notes](https://github.com/librepods-org/librepods/blob/main/android/README.md) before enabling root/Xposed features.

## Build

Use Android Studio's JDK 21+ runtime and install Android SDK Platform 37, NDK `30.0.14904198`, and CMake `3.22.1`.

```sh
./gradlew :app:assembleFossDebug
./gradlew :app:assembleFossRelease
```

The distributable APK is produced at `app/build/outputs/apk/foss/release/app-foss-release.apk`. The release variant uses the configured local release key when `local.properties` contains the `RELEASE_*` values; otherwise it uses the local Android debug key, matching the current sideload update channel on the development machine. Do not publish a differently signed APK as an in-place update.

## Updating

The app checks `update.json` from this repository. The manifest's `versionCode`, `versionName`, `apkUrl`, and optional `sha256` must match the APK attached to the corresponding GitHub release. Android still requires its system confirmation for sideload installs; if automatic installation is blocked, use the in-app update card and grant “Install unknown apps” to AirPods Galaxy.

## Preserved prior implementation

The previous AirPods Galaxy implementation and its tests are retained under [`legacy-app/`](legacy-app/). It is not part of the active Android build. This keeps the earlier BLE battery, popup, artwork, and media-control work available for reference while the default `:app` module is the LibrePods-based port.
