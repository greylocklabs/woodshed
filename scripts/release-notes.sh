#!/usr/bin/env bash
# Prints the CHANGELOG.md section for a version, without its heading. Fails when there is none or it is empty,
# so a release cannot go out without notes.
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:-$(tr -d '[:space:]' < VERSION)}"
notes="$(awk -v v="$version" '
  $0 == "## " v { found = 1; next }
  found && /^## / { exit }
  found { print }
' CHANGELOG.md | sed -e '/./,$!d')"

if [ -z "${notes//[[:space:]]/}" ]; then
  echo "release-notes: CHANGELOG.md has no notes under '## $version'" >&2
  exit 1
fi
printf '%s\n' "$notes"
