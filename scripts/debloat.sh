#!/usr/bin/env bash
# Disable (not uninstall) packages from a list. Writes a restore script.
# Usage: scripts/debloat.sh <tv-ip[:port]> [list-file]   (run scripts/backup.sh first)
D=${1:?usage: debloat.sh <tv-ip[:port]> [list]}; case $D in *:*) ;; *) D=$D:5555;; esac
L=${2:-$(dirname "$0")/bloat-oneplus-tv.txt}; R=restore-bloat.sh
printf '#!/bin/sh\nD=${1:-%s}\n' "$D" > $R
grep -v '^#' "$L" | grep -v '^$' | while read -r p; do
  if adb -s "$D" shell pm list packages "$p" | grep -q "package:$p\$"; then
    r=$(adb -s "$D" shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r'); echo "$p -> $r"
    echo "adb -s \$D shell pm enable $p" >> $R
  else echo "$p -> not installed, skipped"; fi
done
chmod +x $R; echo "undo with ./$R"
