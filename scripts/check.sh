#!/usr/bin/env bash
# After a firmware update: find packages that came back, and that XMB TV Home is still Home.
# Usage: scripts/check.sh <tv-ip[:port]> [list-file] [--fix]
#   --fix  disable again whatever the update re-enabled (still disable-only)
D=${1:?usage: check.sh <tv-ip[:port]> [list] [--fix]}; case $D in *:*) ;; *) D=$D:5555;; esac
L=${2:-$(dirname "$0")/bloat-oneplus-tv.txt}; FIX=0; for a in "$@"; do [ "$a" = --fix ] && FIX=1; done
[ "$L" = --fix ] && L=$(dirname "$0")/bloat-oneplus-tv.txt
adb connect "$D" >/dev/null 2>&1 || true
echo "Home app:"; h=$(adb -s "$D" shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r'); echo "  $h"
case $h in com.sanan.xmb/*) echo "  OK: XMB TV Home is Home";; *) echo "  CHANGED: Home is not XMB TV Home. Run scripts/install.sh again, or press Home and choose XMB.";; esac
dis=$(adb -s "$D" shell pm list packages -d | tr -d '\r'); n=0
echo "Packages on your list that are enabled again:"
grep -v '^#' "$L" | grep -v '^$' | while read -r p; do
  if adb -s "$D" shell pm list packages "$p" | tr -d '\r' | grep -q "package:$p\$" && ! echo "$dis" | grep -q "package:$p\$"; then
    echo "  $p"; [ $FIX = 1 ] && adb -s "$D" shell pm disable-user --user 0 "$p" | tr -d '\r' | sed 's/^/    /'
  fi
done
[ $FIX = 0 ] && echo "(run again with --fix to disable them)"
echo "Done."
