#!/usr/bin/env bash
# Cuts a release from main: bumps VERSION (major, minor or patch), names the notes under ## Unreleased in
# CHANGELOG.md for it, runs make check, commits, tags v<version> and pushes the tag, which starts the release
# workflow. "current" tags the version already in VERSION without bumping it. The release refuses to go out
# without notes. DRY_RUN=1 runs the checks and prints what would happen without committing, tagging or pushing.
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
# The notes collect under ## Unreleased until a release names them.
name_notes=0
if ! grep -qx "## ${version}" CHANGELOG.md && grep -qx "## Unreleased" CHANGELOG.md; then
  name_notes=1
  scripts/release-notes.sh Unreleased > /dev/null
else
  scripts/release-notes.sh "$version" > /dev/null
fi

if [ -n "${DRY_RUN:-}" ]; then
  [ "$part" != "current" ] && echo "would bump VERSION to ${version}"
  [ "$name_notes" = 1 ] && echo "would rename ## Unreleased in CHANGELOG.md to ## ${version}"
  echo "would run make check, commit, tag v${version} and push it to origin together with main"
  exit 0
fi
start="$(git rev-parse HEAD)"
[ "$part" = "current" ] || scripts/bump-version.sh "$version" --write > /dev/null
[ "$name_notes" = 0 ] || sed -i "s/^## Unreleased\$/## ${version}/" CHANGELOG.md
if ! make check; then
  git checkout -- VERSION CHANGELOG.md
  echo "release: make check failed on ${version}; VERSION and CHANGELOG.md are as they were" >&2
  exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  git add VERSION CHANGELOG.md
  git commit -m "chore(release): bump to ${version}"
fi
git tag -a "v${version}" -m "v${version}"
# Atomic, so the tag cannot land without main and start a release from a commit main does not have. If the push
# fails, nothing reached origin, so the local tag and bump are undone and the release can simply be run again.
if ! git push --atomic origin main "refs/tags/v${version}"; then
  git tag -d "v${version}" > /dev/null
  git reset --quiet --hard "$start"
  echo "release: the push failed, so the local tag and version bump were undone; fix the problem and run it again" >&2
  exit 1
fi
echo "released v${version}"
