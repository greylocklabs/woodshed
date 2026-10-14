#!/usr/bin/env bash
# Cuts a release from main: bumps VERSION (major, minor or patch), commits it, tags v<version> and pushes the
# tag, which starts the release workflow. "current" tags the version already in VERSION without bumping it.
# Write the CHANGELOG.md section for the new version first; the release refuses to go out without it.
# DRY_RUN=1 runs the checks and prints what would happen without committing, tagging or pushing.
set -euo pipefail
cd "$(dirname "$0")/.."

part="${1:-}"
case "$part" in
  major | minor | patch | current) ;;
  *)
    echo "usage: scripts/release.sh <major|minor|patch|current>" >&2
    exit 1
    ;;
esac

branch="$(git rev-parse --abbrev-ref HEAD)"
if [ "$branch" != "main" ]; then
  echo "release: must be on main (currently on $branch)" >&2
  exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "release: tracked changes are uncommitted; commit or stash them first" >&2
  exit 1
fi
git fetch --quiet origin main
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "release: main is not the same as origin/main; pull or push first" >&2
  exit 1
fi

if [ "$part" = "current" ]; then
  version="$(tr -d '[:space:]' < VERSION)"
else
  version="$(scripts/bump-version.sh "$part")"
fi
if git rev-parse --quiet --verify "refs/tags/v${version}" > /dev/null; then
  echo "release: v${version} is already tagged" >&2
  exit 1
fi
scripts/release-notes.sh "$version" > /dev/null

if [ -n "${DRY_RUN:-}" ]; then
  [ "$part" != "current" ] && echo "would bump VERSION to ${version} and commit chore(release): bump to ${version}"
  echo "would tag v${version} and push it to origin with main"
  exit 0
fi
if [ "$part" != "current" ]; then
  scripts/bump-version.sh "$version" --write > /dev/null
  git add VERSION
  git commit -m "chore(release): bump to ${version}"
fi
# Annotated, since --follow-tags only pushes annotated tags.
git tag -a "v${version}" -m "v${version}"
git push --follow-tags origin main
echo "released v${version}"
