# EchoPods

EchoPods is an independent Android companion app for AirPods. Its design and feature direction have been informed by several apps in this category, alongside ongoing Android-specific development. The current free and open-source client also incorporates Android code adapted from [LibrePods](https://github.com/librepods-org/librepods), under **GPL-3.0-or-later**; this source relationship is credited transparently in [UPSTREAM.md](UPSTREAM.md), [NOTICE](NOTICE), and the retained source headers.

EchoPods is not a one-to-one product clone. It has its own app identity, update flow, device-compatibility handling, background popup behavior, widgets, and project-specific improvements built around that open-source foundation. This project is not affiliated with or endorsed by Apple or LibrePods.

## Features

The FOSS build exposes all app-level settings without a paid-feature gate, including:

- AirPods battery and charging information, device naming, and model-specific product artwork.
- Listening-mode controls (Off, Transparency, Adaptive, and Noise Cancellation), per-earbud press-and-hold actions, call controls, and Quick Settings controls.
- Ear detection, head gestures, conversational awareness, personalized volume, accessibility settings, and supported audio customizations.
- Battery and noise-control widgets, notifications, connection handoff, and background monitoring.
- GitHub update checks and an in-app download/install flow for EchoPods releases.

Available controls depend on the AirPods model and firmware, Android version, and the phone manufacturer's Bluetooth implementation. Some older Android releases or device-specific Bluetooth stacks may expose only basic Bluetooth audio and not the control or battery channels this app needs; advanced features can also require privileged system access. The app does not claim universal support for Find My, head-tracked spatial audio, heart-rate monitoring, or high-quality two-way audio. Read [LibrePods' Android notes](https://github.com/librepods-org/librepods/blob/main/android/README.md) before enabling root/Xposed features.

## Build

Use Android Studio's JDK 21+ runtime and install Android SDK Platform 37, NDK `30.0.14904198`, and CMake `3.22.1`.

```sh
./gradlew :app:assembleFossDebug
./gradlew :app:assembleFossRelease
```

The distributable APK is produced at `app/build/outputs/apk/foss/release/app-foss-release.apk`. The release variant uses the configured local release key when `local.properties` contains the `RELEASE_*` values; otherwise it uses the local Android debug key, matching the current sideload update channel on the development machine. Do not publish a differently signed APK as an in-place update.

## Updating

The app checks `update.json` from this repository. The manifest's `versionCode`, `versionName`, `apkUrl`, and optional `sha256` must match the APK attached to the corresponding GitHub release. Android still requires its system confirmation for sideload installs; if automatic installation is blocked, use the in-app update card and grant “Install unknown apps” to EchoPods.

## Preserved prior implementation

The earlier EchoPods implementation and its tests are retained under [`legacy-app/`](legacy-app/); it is not part of the active Android build. The current `:app` module combines the licensed LibrePods-derived Android implementation with EchoPods-specific changes. See [UPSTREAM.md](UPSTREAM.md) for the source revision and attribution details.
