#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_PATH="${HOME}/.android/echopods-galaxy-signing/echopods-galaxy-upload.jks"
KEYCHAIN_SERVICE="EchoPods Galaxy Store Release Signing"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
    echo "Galaxy Store signing key not found: $KEYSTORE_PATH" >&2
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
exec ./gradlew :app:bundleGalaxyRelease :app:assembleGalaxyRelease "$@"
