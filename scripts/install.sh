#!/usr/bin/env bash
# Install the launcher APK and grant what it needs. Usage: scripts/install.sh <tv-ip[:port]> [apk]
D=${1:?usage: install.sh <tv-ip[:port]> [apk]}; case $D in *:*) ;; *) D=$D:5555;; esac
APK=${2:-android/out/xmb-launcher.apk}; P=com.sanan.xmb
adb connect "$D" >/dev/null 2>&1
adb -s "$D" install -r "$APK" || exit 1
adb -s "$D" shell cmd notification allow_listener $P/$P.NotifService
adb -s "$D" shell appops set $P GET_USAGE_STATS allow
adb -s "$D" shell appops set $P MANAGE_EXTERNAL_STORAGE allow
adb -s "$D" shell pm grant $P android.permission.ACCESS_COARSE_LOCATION
adb -s "$D" shell pm grant $P android.permission.READ_EXTERNAL_STORAGE
adb -s "$D" shell input keyevent KEYCODE_WAKEUP
adb -s "$D" shell am force-stop $P
adb -s "$D" shell input keyevent KEYCODE_HOME
echo "installed. If the TV asks which home app to use, choose XMB (Always)."
