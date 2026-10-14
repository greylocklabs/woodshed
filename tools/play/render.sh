#!/usr/bin/env bash
# Renders Play's store icon and feature graphic from icon.html and feature.html with headless Chrome,
# into app/src/main/play. Run from anywhere: tools/play/render.sh
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../../app/src/main/play/listings/en-US/graphics"
chrome="$(command -v google-chrome || command -v chromium || command -v chromium-browser)"
shot() {
  "$chrome" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --window-size="$2" --screenshot="$3" "file://$here/$1" 2> /dev/null
}
shot icon.html 512,512 "$out/icon/icon.png"
shot feature.html 1024,500 "$out/feature-graphic/feature-graphic.png"
# Play wants no transparency in the icon, and both as plain sRGB PNGs.
for f in "$out/icon/icon.png" "$out/feature-graphic/feature-graphic.png"; do
  magick "$f" -alpha off -strip "$f"
done
echo "wrote $out/icon/icon.png and $out/feature-graphic/feature-graphic.png"
