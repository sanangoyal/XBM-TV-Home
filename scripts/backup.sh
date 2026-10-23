#!/usr/bin/env bash
# Snapshot TV state before changing anything. Usage: scripts/backup.sh <tv-ip[:port]>
set -e
D=${1:?usage: backup.sh <tv-ip[:port]>}; case $D in *:*) ;; *) D=$D:5555;; esac
B=backup-$(date +%Y%m%d-%H%M%S); mkdir -p "$B/shots"
adb connect "$D" >/dev/null 2>&1 || true
adb -s "$D" shell pm list packages -f > "$B/packages_all.txt"
adb -s "$D" shell pm list packages -d > "$B/packages_disabled.txt"
adb -s "$D" shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > "$B/home_resolve.txt"
for ns in secure global system; do adb -s "$D" shell settings list $ns > "$B/settings_$ns.txt"; done
adb -s "$D" exec-out screencap -p > "$B/shots/00_before.png" || true
echo "$B" > .latest_backup; echo "backup in $B"
