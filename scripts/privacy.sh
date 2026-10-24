#!/usr/bin/env bash
# List voice, content-recognition (ACR), telemetry and ad-related packages present on the TV.
# Read-only: it changes nothing. Review the list, then add what you want off to your debloat list.
# Usage: scripts/privacy.sh <tv-ip[:port]>
D=${1:?usage: privacy.sh <tv-ip[:port]>}; case $D in *:*) ;; *) D=$D:5555;; esac
adb connect "$D" >/dev/null 2>&1 || true
all=$(adb -s "$D" shell pm list packages | tr -d '\r' | sed 's/package://')
dis=$(adb -s "$D" shell pm list packages -d | tr -d '\r' | sed 's/package://')
echo "Candidates (E = enabled, d = already disabled):"
echo "$all" | grep -i -E 'acr|voice|assistant|speech|mic|hotword|telemetry|analytics|stats|tracker|feedback|logger|crash|(^|\.)ads?(\.|$)|adservice|promo|recommend|usage|partner|upload|ota.?report' | sort | while read -r p; do
  if echo "$dis" | grep -qx "$p"; then echo "  d  $p"; else echo "  E  $p"; fi
done
echo
echo "Apps holding the microphone permission:"
adb -s "$D" shell dumpsys package | tr -d '\r' | awk '/^  Package \[/{gsub(/.*\[|\].*/,"");pk=$0} /android.permission.RECORD_AUDIO: granted=true/{print "  " pk}' | sort -u
echo
echo "Names are matches, not verdicts. Look up each package before disabling it, and never touch core com.android.* or com.google.android.gms*."
