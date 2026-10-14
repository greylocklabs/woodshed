#!/usr/bin/env bash
# Prints the version after VERSION's, bumped by major, minor or patch, or an explicit X.Y.Z.
# With --write, also saves it to VERSION.
set -euo pipefail
cd "$(dirname "$0")/.."

part="${1:-}"
write="${2:-}"
current="$(tr -d '[:space:]' < VERSION)"
IFS=. read -r major minor patch <<< "$current"

case "$part" in
  major) next="$((major + 1)).0.0" ;;
  minor) next="${major}.$((minor + 1)).0" ;;
  patch) next="${major}.${minor}.$((patch + 1))" ;;
  [0-9]*.[0-9]*.[0-9]*) next="$part" ;;
  *)
    echo "usage: scripts/bump-version.sh <major|minor|patch|X.Y.Z> [--write]" >&2
    exit 1
    ;;
esac

# Play's version code is major * 10000 + minor * 100 + patch, so minor and patch stay below 100.
IFS=. read -r _ next_minor next_patch <<< "$next"
if [ "$next_minor" -ge 100 ] || [ "$next_patch" -ge 100 ]; then
  echo "bump-version: $next needs minor and patch below 100" >&2
  exit 1
fi

if [ "$write" = "--write" ]; then
  printf '%s\n' "$next" > VERSION
fi
echo "$next"
