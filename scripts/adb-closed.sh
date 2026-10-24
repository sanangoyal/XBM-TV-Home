#!/usr/bin/env bash
# Close network ADB on the TV and confirm port 5555 no longer answers. Run it when you are done.
# Usage: scripts/adb-closed.sh <tv-ip[:port]>
IP=${1:?usage: adb-closed.sh <tv-ip[:port]>}; D=${IP%%:*}; case $IP in *:*) P=${IP##*:};; *) P=5555;; esac
adb connect "$D:$P" >/dev/null 2>&1 || true
echo "Turning off network ADB and USB debugging on the TV..."
adb -s "$D:$P" shell "settings put global adb_enabled 0; settings put global adb_wifi_enabled 0; setprop service.adb.tcp.port -1" 2>&1 | tr -d '\r'
adb disconnect "$D:$P" >/dev/null 2>&1
sleep 3
if (exec 3<>/dev/tcp/$D/$P) 2>/dev/null; then
  echo "STILL OPEN: $D:$P answers. Turn off Settings > Developer options > USB debugging / Network debugging on the TV, then run again, or reboot the TV."
  exit 1
else
  echo "CLOSED: $D:$P no longer answers."
fi
echo "Also revoke old authorisations: Settings > Developer options > Revoke USB debugging authorisations."
