#!/usr/bin/env bash
# Go back to the stock launcher. Usage: scripts/restore-launcher.sh <tv-ip[:port]>
D=${1:?usage: restore-launcher.sh <tv-ip[:port]>}; case $D in *:*) ;; *) D=$D:5555;; esac
adb connect "$D" >/dev/null 2>&1
for p in com.google.android.tvlauncher com.google.android.katniss com.google.android.tungsten.setupwraith; do adb -s "$D" shell pm enable $p; done
adb -s "$D" shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb -s "$D" shell pm uninstall com.sanan.xmb
adb -s "$D" shell input keyevent KEYCODE_HOME
