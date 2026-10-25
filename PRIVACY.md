# EchoPods Privacy Policy

Last updated: October 25, 2026

EchoPods is an independent Android companion app for supported AirPods features. It is not affiliated with or endorsed by Apple. This policy covers the Google Play and GitHub-distributed Android builds; distribution-specific behavior is called out below.

## Information processed on your device

To provide Bluetooth and AirPods features, EchoPods processes nearby Bluetooth observations, device/profile identifiers, AirPods battery/case/wear reports, and settings on your device. During a supported control session, AirPods proximity identity/encryption keys may be stored in the app's private settings. If you enable the related features and grant access, EchoPods may also read call state, active media-session state, and notifications locally to provide call gestures or media controls. EchoPods has no service backend and does not transmit these data to an EchoPods server.

While the Bluetooth service is running, the app automatically keeps up to 1,000 short packet-log entries locally. Each entry records only a source label and packet byte count; this log does not store the packet contents. A separate Android logcat troubleshooting capture starts only when you explicitly run the troubleshooting flow. It is saved in app-private storage, may contain system/app diagnostic details, and should be reviewed before you choose to share it. Neither kind of diagnostic log is uploaded automatically.

The app does not include advertising, analytics, or tracking SDKs and does not require an account.

## Google Play build

- Google Play manages app updates. The Play build does not check GitHub for updates or download/install APK updates itself.
- The app may request the Google Play In-App Review flow after you have used it. If you choose to submit a star rating or written review, Google Play receives it. A production-track review may be public; a review submitted during a closed test may be shared privately with the developer. EchoPods does not receive the review text. Google describes this processing in its [In-App Reviews data safety guidance](https://developer.android.com/guide/playcore/in-app-review#data-safety).
- GitHub pages open only when you choose a repository or issue link. The browser/GitHub may receive ordinary connection metadata such as your network address.

## GitHub-distributed build

The GitHub build can check GitHub for release information and download a release APK. These requests may expose ordinary network metadata, including your network address and the requested app/release information, to GitHub. APK updates are downloaded only by the GitHub-distributed build.

## Optional issue reports

If you prepare an issue report in EchoPods, the report can include the text you entered, app version, device manufacturer/model, and Android build information. When you tap the prepare/share action, EchoPods copies the report to the Android clipboard and opens a blank GitHub issue form. EchoPods does not send the report to GitHub. You decide whether to paste, edit, and submit it. Once pasted/submitted, GitHub processes it under its own terms and privacy policy. Other apps with clipboard access may be able to read copied text.

## Local storage and device migration

AirPods identity/encryption keys are stored in app-private settings so encrypted Bluetooth advertisements can be associated with your AirPods. In this release, the settings file, diagnostic preferences, and saved diagnostic logs are excluded from Android cloud backup and direct device-to-device transfer. On a new phone, reconnect your AirPods so the required keys can be obtained again; app settings may need to be configured again.

Clearing EchoPods storage or uninstalling the app removes its local app data. Public GitHub issues and Play Store reviews are managed through GitHub or Google Play respectively.

## Permissions

Bluetooth permissions are used to discover, connect to, and control supported AirPods. Notification access is used only for active media-session controls. Phone permissions support call-state detection and optional incoming-call head gestures. The special “display over other apps” access is used for the connection and low-battery overlays. These permissions do not mean that the associated data is uploaded by EchoPods; related processing is local unless you explicitly share content as described above. If you deny a permission, the feature that depends on it may be unavailable.

## Contact

For privacy questions or requests, contact the EchoPods maintainers through the [public project repository](https://github.com/blossom0948/EchoPods). Please do not include sensitive personal information in a public issue.
