#!/usr/bin/env bash
# Starts a virtual device and installs and opens the debug app on it, for trying a change without a phone.
# `make emulator [device]` runs it; the device is one `emulator -list-avds` names, made in Android Studio or by hand.
# A device already running on the port is reused, so running it again just reinstalls.
set -euo pipefail
cd "$(dirname "$0")/.."

device=${1:-}
port=${EMULATOR_PORT:-5580}
serial=emulator-$port
sdk=${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}
sdk=${sdk:-$HOME/Android/Sdk}
emulator=$sdk/emulator/emulator
adb=$sdk/platform-tools/adb

if [[ -z $device ]]; then
  echo "Which device? make emulator <device>, one of:" >&2
  "$emulator" -list-avds | sed 's/^/  /' >&2
  exit 2
fi

if [[ $("$adb" -s "$serial" get-state 2>/dev/null) == device ]]; then
  running=$("$adb" -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')
  if [[ $running != "$device" ]]; then
    echo "$running is already running on port $port; close it, or set EMULATOR_PORT for another." >&2
    exit 1
  fi
else
  mkdir -p build
  echo "Starting $device; its output goes to build/emulator.log."
  nohup "$emulator" -avd "$device" -port "$port" > build/emulator.log 2>&1 &
  timeout 60 "$adb" -s "$serial" wait-for-device ||
    { echo "$device did not start; see build/emulator.log." >&2; exit 1; }
fi

for _ in $(seq 90); do
  [[ $("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]] && break
  sleep 2
done
[[ $("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]] ||
  { echo "$device did not finish starting; see build/emulator.log." >&2; exit 1; }

ANDROID_SERIAL=$serial ./gradlew :app:installDebug
"$adb" -s "$serial" shell monkey -p com.larakelley.woodshed.debug -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
echo "Woodshed is open on $device. Close its window to stop it."
