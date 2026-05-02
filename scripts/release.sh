#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/release.sh [--dry-run] <version>

Examples:
  ./scripts/release.sh v0.1.1
  ./scripts/release.sh 0.1.1
  ./scripts/release.sh --dry-run v0.1.1

The Gradle versionName is written without a leading "v".
The Git tag is created with exactly one leading "v".
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_file="$repo_root/app/build.gradle.kts"
dry_run=false
release_paths=(
  app
  gradle
  .github/workflows/release.yml
  settings.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
  scripts/release.sh
)

if [[ "${1:-}" == "--dry-run" ]]; then
  dry_run=true
  shift
fi

if [[ $# -ne 1 || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 1
fi

input_version="$1"
version_name="$input_version"

while [[ "$version_name" == v* || "$version_name" == V* ]]; do
  version_name="${version_name:1}"
done

if [[ ! "$version_name" =~ ^[0-9]+[.][0-9]+[.][0-9]+$ ]]; then
  echo "Version must be numeric semver: vMAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH" >&2
  exit 1
fi

IFS=. read -r major minor patch <<<"$version_name"

if (( 10#$minor > 99 || 10#$patch > 99 )); then
  echo "Minor and patch versions must be <= 99 because versionCode is derived as major*10000 + minor*100 + patch." >&2
  exit 1
fi

version_code=$((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
tag_name="v$version_name"

if (( version_code <= 0 || version_code > 2100000000 )); then
  echo "Derived Android versionCode is out of range: $version_code" >&2
  exit 1
fi

branch="$(git -C "$repo_root" branch --show-current)"
if [[ -z "$branch" ]]; then
  echo "Release must be run from a branch, not a detached HEAD." >&2
  exit 1
fi

echo "Release version: $version_name"
echo "Android versionCode: $version_code"
echo "Git tag: $tag_name"
echo "Branch: $branch"

if [[ "$dry_run" == true ]]; then
  echo "Dry run only; no files, commits, tags, or remotes were changed."
  exit 0
fi

if git -C "$repo_root" rev-parse -q --verify "refs/tags/$tag_name" >/dev/null; then
  echo "Tag already exists locally: $tag_name" >&2
  exit 1
fi

if git -C "$repo_root" ls-remote --exit-code --tags origin "refs/tags/$tag_name" >/dev/null 2>&1; then
  echo "Tag already exists on origin: $tag_name" >&2
  exit 1
fi

dirty_release_files="$(git -C "$repo_root" status --porcelain -- "${release_paths[@]}")"
if [[ -n "$dirty_release_files" ]]; then
  echo "Release-affecting files have uncommitted changes:" >&2
  git -C "$repo_root" status --short -- "${release_paths[@]}" >&2
  exit 1
fi

perl -0pi -e "s/versionCode = [0-9]+/versionCode = $version_code/; s/versionName = \"[^\"]+\"/versionName = \"$version_name\"/" "$gradle_file"

if ! grep -q "versionCode = $version_code" "$gradle_file" || ! grep -q "versionName = \"$version_name\"" "$gradle_file"; then
  echo "Failed to update version values in app/build.gradle.kts." >&2
  exit 1
fi

./gradlew testDebugUnitTest

git -C "$repo_root" add "$gradle_file"
git -C "$repo_root" commit -m "Release $tag_name"
git -C "$repo_root" tag "$tag_name"
git -C "$repo_root" push origin "$branch"
git -C "$repo_root" push origin "$tag_name"

echo "Release tag pushed: $tag_name"
