#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_PATH="${HOME}/.android/echopods-galaxy-signing/echopods-galaxy-upload.jks"
KEYCHAIN_SERVICE="EchoPods Galaxy Store Release Signing"
DEBUG_KEYSTORE_PATH="${HOME}/.android/debug.keystore"
ANDROID_SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Library/Android/sdk}}"
APKSIGNER_PATH="${ANDROID_SDK_PATH}/build-tools/36.0.0/apksigner"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
    echo "EchoPods distribution signing key not found: $KEYSTORE_PATH" >&2
    exit 1
fi

if [[ ! -f "$DEBUG_KEYSTORE_PATH" || ! -x "$APKSIGNER_PATH" ]]; then
    echo "Android debug key or build-tools apksigner is missing." >&2
    exit 1
fi

if ! command -v security >/dev/null 2>&1; then
    echo "macOS Keychain access is required to retrieve the release password." >&2
    exit 1
fi

export ECHOPODS_RELEASE_STORE_FILE="$KEYSTORE_PATH"
export ECHOPODS_RELEASE_STORE_PASSWORD="$(security find-generic-password -s "$KEYCHAIN_SERVICE" -a release-password -w)"
export ECHOPODS_RELEASE_KEY_ALIAS="echopods-galaxy-upload"
export ECHOPODS_RELEASE_KEY_PASSWORD="$ECHOPODS_RELEASE_STORE_PASSWORD"

cd "$(dirname "$0")/.."
./gradlew :app:assembleFossRelease "$@"

APK_PATH="app/build/outputs/apk/foss/release/app-foss-release.apk"
LINEAGE_PATH="$(mktemp -t echopods-signing-lineage)"
SIGNED_APK_PATH="${APK_PATH%.apk}-lineage.apk"
trap 'rm -f "$LINEAGE_PATH" "$SIGNED_APK_PATH"' EXIT

# v0.6.5.5 was accidentally signed with the Android debug key. Keep it as an
# ancestor so Android can accept this update from both the old public key and
# that one bad release. minSdk 31 allows a v3-only proof-of-rotation signature.
"$APKSIGNER_PATH" rotate \
    --out "$LINEAGE_PATH" \
    --old-signer \
    --ks "$DEBUG_KEYSTORE_PATH" \
    --ks-key-alias AndroidDebugKey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --set-installed-data true \
    --set-permission true \
    --set-shared-uid true \
    --set-auth true \
    --new-signer \
    --ks "$KEYSTORE_PATH" \
    --ks-key-alias "$ECHOPODS_RELEASE_KEY_ALIAS" \
    --ks-pass env:ECHOPODS_RELEASE_STORE_PASSWORD \
    --key-pass env:ECHOPODS_RELEASE_KEY_PASSWORD

"$APKSIGNER_PATH" sign \
    --ks "$KEYSTORE_PATH" \
    --ks-key-alias "$ECHOPODS_RELEASE_KEY_ALIAS" \
    --ks-pass env:ECHOPODS_RELEASE_STORE_PASSWORD \
    --key-pass env:ECHOPODS_RELEASE_KEY_PASSWORD \
    --lineage "$LINEAGE_PATH" \
    --rotation-min-sdk-version 31 \
    --v1-signing-enabled false \
    --v2-signing-enabled false \
    --v3-signing-enabled true \
    --out "$SIGNED_APK_PATH" \
    "$APK_PATH"

"$APKSIGNER_PATH" verify --verbose --print-certs "$SIGNED_APK_PATH"
mv "$SIGNED_APK_PATH" "$APK_PATH"
echo "Created update-compatible APK: $APK_PATH"
