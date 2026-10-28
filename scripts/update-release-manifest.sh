#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 TAG VERSION_NAME VERSION_CODE APK_FILE [MANIFEST_FILE] [--dry-run]" >&2
  exit 2
}

[[ $# -ge 4 && $# -le 6 ]] || usage

tag="$1"
version_name="$2"
version_code="$3"
apk_file="$4"
manifest_file="${5:-update.json}"
dry_run="${6:-}"

[[ -z "$dry_run" || "$dry_run" == "--dry-run" ]] || usage
[[ "$tag" =~ ^v[0-9]+(\.[0-9]+){1,3}$ ]] || { echo "Invalid release tag: $tag" >&2; exit 1; }
[[ "$tag" == "v${version_name}" ]] || { echo "Tag and versionName do not match" >&2; exit 1; }
[[ "$version_code" =~ ^[0-9]+$ ]] || { echo "Invalid versionCode: $version_code" >&2; exit 1; }
[[ -s "$apk_file" ]] || { echo "APK is missing or empty: $apk_file" >&2; exit 1; }
[[ -f "$manifest_file" ]] || { echo "Manifest is missing: $manifest_file" >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }

repo="${GITHUB_REPOSITORY:-blossom0948/EchoPods}"
[[ "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "Invalid repository: $repo" >&2; exit 1; }

if command -v sha256sum >/dev/null 2>&1; then
  sha256="$(sha256sum "$apk_file" | awk '{print $1}')"
else
  sha256="$(shasum -a 256 "$apk_file" | awk '{print $1}')"
fi
apk_url="https://github.com/${repo}/releases/download/${tag}/app-foss-release.apk?download=1"
current_code="$(jq -er '.versionCode | numbers' "$manifest_file")"

if (( 10#$version_code < current_code )); then
  echo "Refusing to replace versionCode $current_code with older $version_code" >&2
  exit 1
fi

candidate="$(jq -n \
  --argjson versionCode "$version_code" \
  --arg versionName "$version_name" \
  --arg apkUrl "$apk_url" \
  --arg sha256 "$sha256" \
  '{versionCode: $versionCode, versionName: $versionName, apkUrl: $apkUrl, sha256: $sha256}')"

if (( 10#$version_code == current_code )); then
  if [[ "$(jq -cS . "$manifest_file")" == "$(jq -cS . <<<"$candidate")" ]]; then
    echo "Manifest already matches $tag"
    printf '%s\n' "$candidate"
    exit 0
  fi
  echo "Refusing to replace existing versionCode $current_code with different release metadata" >&2
  exit 1
fi

if [[ "$dry_run" == "--dry-run" ]]; then
  printf '%s\n' "$candidate"
  exit 0
fi

temporary_file="$(mktemp "${manifest_file}.tmp.XXXXXX")"
trap 'rm -f "$temporary_file"' EXIT
printf '%s\n' "$candidate" > "$temporary_file"
mv "$temporary_file" "$manifest_file"
trap - EXIT
echo "Updated $manifest_file for $tag ($version_code / $version_name)"
