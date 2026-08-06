#!/usr/bin/env bash

set -euo pipefail

readonly EXPECTED_REPOSITORY="greyfreedom/PocketVault"
readonly EXPECTED_PLAY_CERT_SHA256="80d6de925030843355be737d49f8f6a416855ab6cd59445aac93f27c6410e7f1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
BUILD_FILE="$ROOT_DIR/app/build.gradle.kts"
CHANGELOG_FILE="$ROOT_DIR/CHANGELOG.md"

AAB_PATH=""
APK_PATH=""
NOTES_FILE=""
OUTPUT_PATH=""
PLAY_CERT_SHA256="${PLAY_APP_SIGNING_SHA256:-$EXPECTED_PLAY_CERT_SHA256}"
PUBLISH=false

usage() {
    printf '%s\n' \
        "Usage: scripts/publish-github-release.sh [options]" \
        "" \
        "Verify the Play-uploaded AAB and Play-signed Universal APK, calculate" \
        "hashes, and generate GitHub Release notes. Nothing is uploaded unless" \
        "--publish is explicitly provided." \
        "" \
        "Options:" \
        "  --aab PATH                 Exact AAB uploaded to Google Play" \
        "  --apk PATH                 Universal APK downloaded from Play Console" \
        "  --play-cert-sha256 SHA256  Expected Play App Signing fingerprint" \
        "  --notes-file PATH          Markdown inserted under the Changes heading" \
        "  --output PATH              Generated GitHub Release notes path" \
        "  --publish                  Create the GitHub Release and upload only the APK" \
        "  -h, --help                 Show this help" \
        "" \
        "Default artifact paths are derived from app/build.gradle.kts:" \
        "  app/googlePlay/release/app-googlePlay-release.aab" \
        "  app/googlePlay/release/PocketVault-v<versionName>-universal.apk" \
        "" \
        "Examples:" \
        "  ./scripts/publish-github-release.sh" \
        "  ./scripts/publish-github-release.sh --publish" \
        "  ./scripts/publish-github-release.sh --aab /path/app.aab --apk /path/app.apk"
}

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

warn() {
    printf 'WARNING: %s\n' "$*" >&2
}

read_gradle_value() {
    local key="$1"
    local file="$2"

    awk -v key="$key" '
        $0 ~ "^[[:space:]]*" key "[[:space:]]*=" {
            value = $0
            sub(/^[^=]*=[[:space:]]*/, "", value)
            sub(/[[:space:]]*$/, "", value)
            gsub(/\"/, "", value)
            print value
            exit
        }
    ' "$file"
}

normalize_sha256() {
    printf '%s' "$1" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

format_sha256() {
    printf '%s' "$1" | sed -E 's/(..)/\1:/g; s/:$//' | tr '[:lower:]' '[:upper:]'
}

sha256_file() {
    local file="$1"

    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print $1}'
    else
        fail "Neither sha256sum nor shasum is installed."
    fi
}

canonical_existing_path() {
    local path="$1"
    local directory
    local filename

    directory="$(cd "$(dirname "$path")" && pwd -P)"
    filename="$(basename "$path")"
    printf '%s/%s\n' "$directory" "$filename"
}

find_android_tool() {
    local tool="$1"
    local sdk_root
    local candidate
    local found=""
    local local_sdk=""
    local sdk_roots=()

    if command -v "$tool" >/dev/null 2>&1; then
        command -v "$tool"
        return 0
    fi

    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        sdk_roots+=("$ANDROID_SDK_ROOT")
    fi
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        sdk_roots+=("$ANDROID_HOME")
    fi
    if [[ -f "$ROOT_DIR/local.properties" ]]; then
        local_sdk="$(awk -F= '$1 == "sdk.dir" {sub(/^[^=]*=/, ""); print; exit}' "$ROOT_DIR/local.properties")"
        if [[ -n "$local_sdk" ]]; then
            sdk_roots+=("$local_sdk")
        fi
    fi
    sdk_roots+=("$HOME/Library/Android/sdk" "$HOME/Android/Sdk")

    for sdk_root in "${sdk_roots[@]}"; do
        [[ -d "$sdk_root" ]] || continue

        if [[ "$tool" == "apkanalyzer" ]]; then
            for candidate in \
                "$sdk_root/cmdline-tools/latest/bin/apkanalyzer" \
                "$sdk_root"/cmdline-tools/*/bin/apkanalyzer \
                "$sdk_root/tools/bin/apkanalyzer"; do
                if [[ -x "$candidate" ]]; then
                    printf '%s\n' "$candidate"
                    return 0
                fi
            done
        else
            for candidate in "$sdk_root"/build-tools/*/"$tool"; do
                if [[ -x "$candidate" ]]; then
                    found="$candidate"
                fi
            done
        fi
    done

    if [[ -n "$found" ]]; then
        printf '%s\n' "$found"
        return 0
    fi

    return 1
}

extract_changelog_notes() {
    local version="$1"

    awk -v version="$version" '
        index($0, "## [" version "]") == 1 {
            found = 1
            next
        }
        found && /^## \[/ {
            exit
        }
        found && !started && /^[[:space:]]*$/ {
            next
        }
        found {
            started = 1
            print
        }
        END {
            if (!found) {
                exit 1
            }
        }
    ' "$CHANGELOG_FILE"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --aab)
            [[ $# -ge 2 ]] || fail "--aab requires a path."
            AAB_PATH="$2"
            shift 2
            ;;
        --apk)
            [[ $# -ge 2 ]] || fail "--apk requires a path."
            APK_PATH="$2"
            shift 2
            ;;
        --play-cert-sha256)
            [[ $# -ge 2 ]] || fail "--play-cert-sha256 requires a fingerprint."
            PLAY_CERT_SHA256="$2"
            shift 2
            ;;
        --notes-file)
            [[ $# -ge 2 ]] || fail "--notes-file requires a path."
            NOTES_FILE="$2"
            shift 2
            ;;
        --output)
            [[ $# -ge 2 ]] || fail "--output requires a path."
            OUTPUT_PATH="$2"
            shift 2
            ;;
        --publish)
            PUBLISH=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown option: $1"
            ;;
    esac
done

[[ -f "$BUILD_FILE" ]] || fail "Missing build file: $BUILD_FILE"

APPLICATION_ID="$(read_gradle_value "applicationId" "$BUILD_FILE")"
VERSION_CODE="$(read_gradle_value "versionCode" "$BUILD_FILE")"
VERSION_NAME="$(read_gradle_value "versionName" "$BUILD_FILE")"

[[ -n "$APPLICATION_ID" ]] || fail "Could not read applicationId from app/build.gradle.kts."
[[ -n "$VERSION_CODE" ]] || fail "Could not read versionCode from app/build.gradle.kts."
[[ -n "$VERSION_NAME" ]] || fail "Could not read versionName from app/build.gradle.kts."

TAG="v$VERSION_NAME"
AAB_PATH="${AAB_PATH:-$ROOT_DIR/app/googlePlay/release/app-googlePlay-release.aab}"
APK_PATH="${APK_PATH:-$ROOT_DIR/app/googlePlay/release/PocketVault-$TAG-universal.apk}"
OUTPUT_PATH="${OUTPUT_PATH:-$ROOT_DIR/app/googlePlay/release/GITHUB_RELEASE_$TAG.md}"

[[ -f "$AAB_PATH" ]] || fail "AAB not found: $AAB_PATH"
[[ -f "$APK_PATH" ]] || fail "Universal APK not found: $APK_PATH"
if [[ -n "$NOTES_FILE" ]]; then
    [[ -f "$NOTES_FILE" ]] || fail "Notes file not found: $NOTES_FILE"
    NOTES_FILE="$(canonical_existing_path "$NOTES_FILE")"
fi

AAB_PATH="$(canonical_existing_path "$AAB_PATH")"
APK_PATH="$(canonical_existing_path "$APK_PATH")"
mkdir -p "$(dirname "$OUTPUT_PATH")"
OUTPUT_PATH="$(cd "$(dirname "$OUTPUT_PATH")" && pwd -P)/$(basename "$OUTPUT_PATH")"

PLAY_CERT_SHA256="$(normalize_sha256 "$PLAY_CERT_SHA256")"
if [[ ${#PLAY_CERT_SHA256} -ne 64 || "$PLAY_CERT_SHA256" == *[!0-9a-f]* ]]; then
    fail "The expected Play App Signing SHA-256 fingerprint must contain 64 hexadecimal characters."
fi

APKSIGNER="$(find_android_tool apksigner)" || fail "apksigner was not found. Install Android SDK Build Tools."
APKANALYZER="$(find_android_tool apkanalyzer)" || fail "apkanalyzer was not found. Install Android SDK Command-line Tools."
command -v unzip >/dev/null 2>&1 || fail "unzip is required."
command -v jarsigner >/dev/null 2>&1 || fail "jarsigner is required. Use JDK 21."
command -v git >/dev/null 2>&1 || fail "git is required."

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pocketvault-release.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

printf 'PocketVault release verification\n'
printf '  Version: %s (%s)\n' "$VERSION_NAME" "$VERSION_CODE"
printf '  Package: %s\n' "$APPLICATION_ID"
printf '  AAB:     %s\n' "$AAB_PATH"
printf '  APK:     %s\n\n' "$APK_PATH"

printf '[1/7] Checking archive integrity...\n'
unzip -tq "$AAB_PATH" >/dev/null || fail "The AAB ZIP structure is damaged."
unzip -tq "$APK_PATH" >/dev/null || fail "The APK ZIP structure is damaged."

printf '[2/7] Verifying the AAB upload signature...\n'
if ! jarsigner -verify "$AAB_PATH" >"$TMP_DIR/aab-signature.txt" 2>&1; then
    sed -n '1,160p' "$TMP_DIR/aab-signature.txt" >&2
    fail "The AAB JAR signature is invalid."
fi

printf '[3/7] Verifying the APK signature and Play certificate...\n'
if ! "$APKSIGNER" verify --verbose --print-certs "$APK_PATH" >"$TMP_DIR/apk-signature.txt" 2>&1; then
    sed -n '1,200p' "$TMP_DIR/apk-signature.txt" >&2
    fail "The APK signature is invalid."
fi

SIGNER_COUNT="$(awk -F': ' '/^Number of signers:/ {print $2; exit}' "$TMP_DIR/apk-signature.txt")"
ACTUAL_PLAY_CERT_SHA256="$(awk -F': ' '/^Signer #1 certificate SHA-256 digest:/ {print $2; exit}' "$TMP_DIR/apk-signature.txt")"
SOURCE_STAMP_VERIFIED="$(awk -F': ' '/^Verified for SourceStamp:/ {print $2; exit}' "$TMP_DIR/apk-signature.txt")"
ACTUAL_PLAY_CERT_SHA256="$(normalize_sha256 "$ACTUAL_PLAY_CERT_SHA256")"

[[ "$SIGNER_COUNT" == "1" ]] || fail "Expected exactly one APK signer, found: ${SIGNER_COUNT:-unknown}"
[[ "$ACTUAL_PLAY_CERT_SHA256" == "$PLAY_CERT_SHA256" ]] || fail "APK signer does not match the configured Play App Signing certificate. Expected $(format_sha256 "$PLAY_CERT_SHA256"), found $(format_sha256 "$ACTUAL_PLAY_CERT_SHA256")."
[[ "$SOURCE_STAMP_VERIFIED" == "true" ]] || fail "The APK does not have a verified Source Stamp. Download the signed Universal APK from Play Console again."

printf '[4/7] Checking package, version, and offline manifest...\n'
APK_APPLICATION_ID="$("$APKANALYZER" manifest application-id "$APK_PATH")"
APK_VERSION_NAME="$("$APKANALYZER" manifest version-name "$APK_PATH")"
APK_VERSION_CODE="$("$APKANALYZER" manifest version-code "$APK_PATH")"

[[ "$APK_APPLICATION_ID" == "$APPLICATION_ID" ]] || fail "APK package mismatch. Expected $APPLICATION_ID, found $APK_APPLICATION_ID."
[[ "$APK_VERSION_NAME" == "$VERSION_NAME" ]] || fail "APK versionName mismatch. Expected $VERSION_NAME, found $APK_VERSION_NAME."
[[ "$APK_VERSION_CODE" == "$VERSION_CODE" ]] || fail "APK versionCode mismatch. Expected $VERSION_CODE, found $APK_VERSION_CODE."

"$APKANALYZER" manifest permissions "$APK_PATH" >"$TMP_DIR/apk-permissions.txt"
FORBIDDEN_PERMISSIONS="$(awk '
    /^android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_WIFI_STATE)$/ {
        print
    }
' "$TMP_DIR/apk-permissions.txt")"
if [[ -n "$FORBIDDEN_PERMISSIONS" ]]; then
    printf '%s\n' "$FORBIDDEN_PERMISSIONS" >&2
    fail "The APK contains a forbidden network permission."
fi

"$APKANALYZER" manifest print "$APK_PATH" >"$TMP_DIR/apk-manifest.xml"
if grep -Eiq 'firebase|crashlytics|google\.android\.gms\.measurement' "$TMP_DIR/apk-manifest.xml"; then
    fail "The APK manifest contains a Firebase, Crashlytics, or Analytics component."
fi

printf '[5/7] Checking source tag traceability...\n'
git -C "$ROOT_DIR" rev-parse --git-dir >/dev/null 2>&1 || fail "The project is not a Git repository."
SOURCE_COMMIT="$(git -C "$ROOT_DIR" rev-parse --verify "${TAG}^{commit}" 2>/dev/null)" || fail "Source tag $TAG does not exist locally."
git -C "$ROOT_DIR" show "${TAG}:app/build.gradle.kts" >"$TMP_DIR/tag-build.gradle.kts" || fail "Could not read app/build.gradle.kts from $TAG."

TAG_APPLICATION_ID="$(read_gradle_value "applicationId" "$TMP_DIR/tag-build.gradle.kts")"
TAG_VERSION_CODE="$(read_gradle_value "versionCode" "$TMP_DIR/tag-build.gradle.kts")"
TAG_VERSION_NAME="$(read_gradle_value "versionName" "$TMP_DIR/tag-build.gradle.kts")"
[[ "$TAG_APPLICATION_ID" == "$APPLICATION_ID" ]] || fail "$TAG uses package $TAG_APPLICATION_ID instead of $APPLICATION_ID."
[[ "$TAG_VERSION_CODE" == "$VERSION_CODE" ]] || fail "$TAG uses versionCode $TAG_VERSION_CODE instead of $VERSION_CODE."
[[ "$TAG_VERSION_NAME" == "$VERSION_NAME" ]] || fail "$TAG uses versionName $TAG_VERSION_NAME instead of $VERSION_NAME."

TAG_TYPE="$(git -C "$ROOT_DIR" cat-file -t "refs/tags/$TAG")"
if [[ "$TAG_TYPE" != "tag" ]]; then
    warn "$TAG is a lightweight tag, not an annotated/signed tag. Do not move it now; use a signed tag for future releases."
elif ! git -C "$ROOT_DIR" tag -v "$TAG" >"$TMP_DIR/tag-signature.txt" 2>&1; then
    warn "$TAG is annotated but its signature could not be verified."
fi

printf '[6/7] Calculating SHA-256 digests...\n'
AAB_SHA256="$(sha256_file "$AAB_PATH")"
APK_SHA256="$(sha256_file "$APK_PATH")"
FORMATTED_PLAY_CERT_SHA256="$(format_sha256 "$ACTUAL_PLAY_CERT_SHA256")"

printf '[7/7] Generating GitHub Release notes...\n'
if [[ -n "$NOTES_FILE" ]]; then
    CHANGES="$(<"$NOTES_FILE")"
else
    [[ -f "$CHANGELOG_FILE" ]] || fail "CHANGELOG.md is missing. Use --notes-file to provide release changes."
    CHANGES="$(extract_changelog_notes "$VERSION_NAME")" || fail "CHANGELOG.md has no section for $VERSION_NAME. Use --notes-file to provide release changes."
fi

{
    printf '# PocketVault %s\n\n' "$TAG"
    printf '%s\n\n' 'This is the official Play App Signing build generated from the same Android App Bundle published on Google Play.'
    printf -- '- Version name: `%s`\n' "$VERSION_NAME"
    printf -- '- Version code: `%s`\n' "$VERSION_CODE"
    printf -- '- Package name: `%s`\n' "$APPLICATION_ID"
    printf -- '- Source tag: [`%s`](https://github.com/%s/tree/%s)\n' "$TAG" "$EXPECTED_REPOSITORY" "$TAG"
    printf -- '- Source commit: [`%s`](https://github.com/%s/commit/%s)\n' "$SOURCE_COMMIT" "$EXPECTED_REPOSITORY" "$SOURCE_COMMIT"
    printf -- '- Distribution artifact: Play-generated signed Universal APK\n'
    printf -- '- Play App Signing certificate SHA-256: `%s`\n' "$FORMATTED_PLAY_CERT_SHA256"
    printf -- '- Uploaded AAB SHA-256: `%s`\n' "$AAB_SHA256"
    printf -- '- Universal APK SHA-256: `%s`\n\n' "$APK_SHA256"
    printf '## Changes\n\n%s\n\n' "$CHANGES"
    printf '## Verification\n\n'
    printf '%s\n' 'The attached APK was downloaded from Google Play Console, carries a verified Source Stamp, and is signed with the official Play App Signing certificate.'
    printf '%s\n\n' 'The Google Play and GitHub distributions use the same package name and signing identity.'
    printf '%s\n' 'Only the Play-generated Universal APK is attached to this release. The AAB is retained locally for traceability and is not distributed as an installable GitHub asset.'
} >"$OUTPUT_PATH"

printf '\nAll release verification checks passed.\n'
printf '  Source commit: %s\n' "$SOURCE_COMMIT"
printf '  AAB SHA-256:   %s\n' "$AAB_SHA256"
printf '  APK SHA-256:   %s\n' "$APK_SHA256"
printf '  Play cert:     %s\n' "$FORMATTED_PLAY_CERT_SHA256"
printf '  Release notes: %s\n' "$OUTPUT_PATH"
printf '\nConfirm that the AAB above is the exact file uploaded for this Play Console release.\n'

if [[ "$PUBLISH" == true ]]; then
    command -v gh >/dev/null 2>&1 || fail "GitHub CLI is not installed. Install it with 'brew install gh', then run 'gh auth login' once."
    gh auth status --hostname github.com >/dev/null 2>&1 || fail "GitHub CLI is not authenticated. Run 'gh auth login' once."
    if gh release view "$TAG" --repo "$EXPECTED_REPOSITORY" >/dev/null 2>&1; then
        fail "GitHub Release $TAG already exists. It was not modified."
    fi

    printf '\nPublishing GitHub Release %s...\n' "$TAG"
    gh release create "$TAG" "$APK_PATH" \
        --repo "$EXPECTED_REPOSITORY" \
        --title "PocketVault $TAG" \
        --notes-file "$OUTPUT_PATH" \
        --verify-tag \
        --latest
    printf 'GitHub Release published successfully.\n'
else
    printf '\nNo files were uploaded. Review the generated notes, then publish with:\n'
    printf '  ./scripts/publish-github-release.sh --publish\n'
fi
