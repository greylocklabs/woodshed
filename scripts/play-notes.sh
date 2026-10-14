#!/usr/bin/env bash
# Prints a version's CHANGELOG.md section as plain text for Play's "What's new", which takes no Markdown and at
# most 500 characters: headings and bullet markers go, bold scopes and links become their text, and anything
# past the limit is cut at a word with "...".
set -euo pipefail
cd "$(dirname "$0")/.."

scripts/release-notes.sh "${1:-}" | python3 -c '
import re, sys
lines = []
for line in sys.stdin.read().splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    line = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", line)   # [text](url) -> text
    line = re.sub(r"\*\*([^*]+):\*\*\s*", "", line)          # **scope:** prefix
    line = line.replace("**", "").replace("`", "")
    if line.startswith("- "):
        lines.append(line[2:])
    elif lines:
        lines[-1] += " " + line                              # a wrapped bullet continues
    else:
        lines.append(line)
text = "\n".join(l[0].upper() + l[1:] for l in lines)
if len(text) > 500:
    text = text[:497].rsplit(" ", 1)[0] + "..."
print(text)
'
