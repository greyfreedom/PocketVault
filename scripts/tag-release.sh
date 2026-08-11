#!/usr/bin/env bash

set -euo pipefail

readonly REMOTE="origin"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
BUILD_FILE="$ROOT_DIR/app/build.gradle.kts"
CHANGELOG_FILE="$ROOT_DIR/CHANGELOG.md"

usage() {
    printf '%s\n' \
        "Usage: ./scripts/tag-release.sh" \
        "" \
        "Read versionName from app/build.gradle.kts, create its signed release" \
        "tag, verify the signature, and push the tag to origin." \
        "" \
        "The working tree must be clean and the matching CHANGELOG entry must" \
        "contain a publication date instead of Unreleased."
}

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
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

if [[ $# -gt 0 ]]; then
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown option: $1"
            ;;
    esac
fi

command -v git >/dev/null 2>&1 || fail "git is required."
[[ -f "$BUILD_FILE" ]] || fail "Missing build file: $BUILD_FILE"
[[ -f "$CHANGELOG_FILE" ]] || fail "Missing changelog: $CHANGELOG_FILE"
git -C "$ROOT_DIR" rev-parse --git-dir >/dev/null 2>&1 || fail "The project is not a Git repository."

# 版本号以 Gradle 配置为唯一信源，避免创建 Tag 时重复手改版本。
VERSION_NAME="$(read_gradle_value "versionName" "$BUILD_FILE")"
[[ -n "$VERSION_NAME" ]] || fail "Could not read versionName from app/build.gradle.kts."
if [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]]; then
    fail "versionName is not a supported release version: $VERSION_NAME"
fi

TAG="v$VERSION_NAME"
HEAD_COMMIT="$(git -C "$ROOT_DIR" rev-parse --verify HEAD)"
WORKTREE_STATUS="$(git -C "$ROOT_DIR" status --porcelain=v1 --untracked-files=normal)"

if [[ -n "$WORKTREE_STATUS" ]]; then
    printf '%s\n' "$WORKTREE_STATUS" >&2
    fail "The working tree is not clean. Commit or stash all changes before creating $TAG."
fi

grep -Fq "## [$VERSION_NAME] -" "$CHANGELOG_FILE" || \
    fail "CHANGELOG.md has no release section for $VERSION_NAME."
if grep -Fq "## [$VERSION_NAME] - Unreleased" "$CHANGELOG_FILE"; then
    fail "Replace Unreleased in the $VERSION_NAME changelog section with the publication date first."
fi

git -C "$ROOT_DIR" remote get-url "$REMOTE" >/dev/null 2>&1 || \
    fail "Git remote '$REMOTE' is not configured."

printf 'PocketVault release tag\n'
printf '  Version: %s\n' "$VERSION_NAME"
printf '  Tag:     %s\n' "$TAG"
printf '  Commit:  %s\n' "$HEAD_COMMIT"
printf '  Remote:  %s\n\n' "$REMOTE"

if ! REMOTE_TAG="$(git -C "$ROOT_DIR" ls-remote --tags "$REMOTE" "refs/tags/$TAG")"; then
    fail "Could not check whether $TAG already exists on $REMOTE."
fi
[[ -z "$REMOTE_TAG" ]] || fail "$TAG already exists on $REMOTE; it will not be replaced."

# 若签名成功但推送失败，可重新执行脚本并安全复用指向当前提交的本地 Tag。
if git -C "$ROOT_DIR" show-ref --verify --quiet "refs/tags/$TAG"; then
    TAG_COMMIT="$(git -C "$ROOT_DIR" rev-parse --verify "${TAG}^{commit}")"
    [[ "$TAG_COMMIT" == "$HEAD_COMMIT" ]] || \
        fail "Local $TAG points to $TAG_COMMIT instead of current commit $HEAD_COMMIT."
    [[ "$(git -C "$ROOT_DIR" cat-file -t "refs/tags/$TAG")" == "tag" ]] || \
        fail "Local $TAG is lightweight. Delete it manually and create a signed tag."
    printf '[1/3] Reusing the existing local signed tag %s.\n' "$TAG"
else
    printf '[1/3] Creating signed tag %s...\n' "$TAG"
    git -C "$ROOT_DIR" tag -s "$TAG" -m "PocketVault $TAG"
fi

printf '[2/3] Verifying tag signature...\n'
if ! git -C "$ROOT_DIR" tag -v "$TAG"; then
    fail "Signature verification failed for $TAG. The tag was not pushed."
fi

printf '[3/3] Pushing tag to %s...\n' "$REMOTE"
git -C "$ROOT_DIR" push "$REMOTE" "refs/tags/$TAG"

printf '\nDone: %s was signed, verified, and pushed to %s.\n' "$TAG" "$REMOTE"
