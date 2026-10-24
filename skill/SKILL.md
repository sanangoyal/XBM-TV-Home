---
name: android-tv-xmb
description: Connect to an Android TV over ADB, back it up, safely disable bloatware and ad services, optimise it, and install or build the custom PS3 XMB-style launcher (this repo). Use when the user wants to debloat or speed up an Android TV, replace its home screen, or build a custom TV launcher UI.
---

# Android TV: ADB, debloat, custom XMB TV Home

Three parts: (1) connect, (2) back up and optimise, (3) install XMB TV Home, or use it as the example for building a custom launcher. Do them in order. Ask the user before every change that is hard to see or undo.

## Rules (never skip)

1. **Back up before changing anything** (`scripts/backup.sh`). Save package lists, settings, current home app, and a screenshot.
2. **Disable, never uninstall** (`pm disable-user --user 0`). Always write a restore script as you go.
3. **Ask first.** Show the user the package list, grouped (ads/promos, vendor junk, Google extras, telemetry). Disable one group at a time and confirm each.
4. **Verify with a TV screenshot** after each step (`adb exec-out screencap -p > shot.png`, then look at it). Do not assume it worked.
5. **Never touch**: `com.android.*` core packages, `com.google.android.gms*`, `com.google.android.tv*` input/settings, `com.android.providers.*`, the keyboard/IME, `com.android.tv.settings`, the active home app until the new one is confirmed working.
6. If anything looks wrong (black screen, remote dead), run the restore scripts. Keep the user informed in short messages.
7. No VPN requirement; the TV and computer must be on the same LAN.

Also read `PROMPT.md` at the repo root: it is the step-by-step prompt for the whole job, including the questions to ask. Extra guardrails: never start factory/test-menu packages (some boards set a flag the shell cannot reverse); before disabling `com.google.android.tungsten.setupwraith` (Google TV setup), check which package owns the saved Wi-Fi, since disabling it can cut Wi-Fi and ADB; on CVTE-type boards keep the stock launcher enabled and set Home with `cmd package set-home-activity`, reboot once, confirm Home and ADB return; at the end offer to close ADB (turn debugging off, revoke authorisations, reboot).

## 1. Connect

1. User enables Developer options: Settings > Device Preferences > About > press *Build* 7 times; then Developer options > USB debugging / Network debugging on. Get the TV IP from Network settings.
2. Computer needs `adb` (`adb version`). Install if missing (macOS `brew install android-platform-tools`).
3. `adb connect <ip>:5555`, then `adb devices`. The TV shows an "Allow debugging?" prompt; the user must accept (tick "Always allow"). Retry until the device shows `device` (not `unauthorized`).
4. Gather facts: `adb shell getprop ro.product.model`, `ro.build.version.release`, `ro.build.version.sdk`, `adb shell wm size`, `adb shell cat /proc/meminfo | head -2`. Tell the user the model, Android version, RAM.

Gotchas: `adb connect` may need the port from Wireless/Network debugging; if the TV sleeps, `adb shell input keyevent KEYCODE_WAKEUP`; adb over Wi-Fi drops when the TV sleeps.

## 2. Back up, debloat, optimise

```
scripts/backup.sh <ip>                 # creates backup-YYYYMMDD-HHMMSS/ and .latest_backup
```

Build the candidate list:

```
adb shell pm list packages -3          # user-installed
adb shell pm list packages -s | grep -i -E "ads|promo|demo|store|recommend|feedback|stats|tracker|weather|screensaver|userguide|imagewall|partner|onetime"
```

For OnePlus TV, start from `scripts/bloat-oneplus-tv.txt` (see `references/debloat.md` for what each package is). For other brands, research each unknown package before listing it (search the package name), and mark it in a group. Show the user the table (package, what it is, risk). After approval:

```
scripts/debloat.sh <ip> <list-file>    # writes restore-bloat.sh
```

Check the TV still boots to home and apps open. Screenshot.

Optional speed-ups (ask first, all reversible):

- Animations: `adb shell settings put global window_animation_scale 0.5` (also `transition_animation_scale`, `animator_duration_scale`). Reset with 1.0.
- Kill background apps: the launcher includes a memory guard (needs Usage Access, granted by `scripts/install.sh`).
- Reboot after debloating: `adb reboot`.

Ads: on many TVs ads come from the stock launcher's "recommendations" row and promo apps. Disabling the stock launcher (after the XMB launcher works) removes the ad row entirely. Do not block hostnames system-wide unless the user asks.

## 3. Install XMB TV Home

Prefer the prebuilt APK from the repo Releases. To build: see README "Build from source" (needs TMDB v3 key for movie scenes; ask the user, they are free).

```
scripts/install.sh <ip> xmb-launcher.apk
```

This installs, grants notification access, usage access, storage, coarse location, then goes Home. Screenshot and confirm with the user.

Make it the default home: press Home on the remote and choose XMB > Always. If the stock launcher keeps appearing, with the user's OK:

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Undo: `scripts/restore-launcher.sh <ip>`.

First-run settings (on the TV: Settings > Launcher, or by MCP, see below): Background Static for weak TVs (lightest), Dynamic for video; Color Auto.

Controlling the launcher by agent: ask the user to turn on Settings > Launcher > Agent control, read the address and token from the screen, then use the MCP endpoint `http://<ip>:8765/mcp` with `Authorization: Bearer <token>`. Tools: `status`, `get_settings`, `set_setting(key,value)`, `press_key`, `list_apps`, `open_app`, `screenshot`. Use `screenshot` to verify what you changed. Keys inside the launcher UI: Up/Down move in a list; Left leaves a submenu; Right opens; Enter selects; Back closes panels.

Without MCP: `adb shell am start -n com.sanan.xmb/.MainActivity --es js '<js>'` runs JavaScript in the launcher page (debug hook).

## 4. Using this project as an example to build a custom XMB-style UI

Read `references/architecture.md`, then `docs/DESIGN.md` and `docs/TECH.md`. Key ideas to reuse:

- **Shell**: native Activity with a transparent WebView over a SurfaceView. UI is HTML/CSS/JS (fast iteration); the background (video via MediaPlayer, or image via `lockCanvas`) lives in the SurfaceView so the WebView never composites video.
- **Perf on weak TVs**: lite mode CSS (no backdrop-filter, no blur), a static-background mode with zero idle redraws, a CSS design resolution scaled to the real panel, no 720p downscaling of the UI.
- **Bridge**: `@JavascriptInterface` methods for app launching, settings, file listing; keep all privileged work in Java.
- **Assets**: videos and audio stored uncompressed in the APK so `AssetFileDescriptor` works; keep APK small.
- **Icons**: only vetted open-source icon sets or the brands' own marks; never hand-draw system glyphs.
- **Agent surface**: tiny in-app MCP server (`AgentServer.java`), off by default, bearer token, LAN only.
- **Workflow that worked**: edit template, rebuild with `tools/mk.py` + `android/build.sh`, `adb install -r`, restart, take a TV screenshot, compare. Keep a backup of every file before editing, keep DESIGN.md/TECH.md current.

When the user wants a different look (another console style, different brand), copy `web/xmb.tpl.html`, replace the visuals and the data tables (categories, icons, colours), keep the bridge and perf approach, and follow the same rules: back up, verify on the TV, keep restore scripts.

## Troubleshooting

- `unauthorized`: accept the prompt on the TV; revoke USB debugging authorisations and reconnect.
- Install fails `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: `adb uninstall com.sanan.xmb`, reinstall (signatures differ).
- Launcher stuck on a black screen: `adb shell am force-stop com.sanan.xmb`, then Home; or run `scripts/restore-launcher.sh`.
- File browser empty: re-run `adb shell appops set com.sanan.xmb MANAGE_EXTERNAL_STORAGE allow`.
- Music/video not playing after a long uptime: restart the launcher (`am force-stop`, Home).
- `pm disable-user` says "Unknown package" or "Security exception": the package does not exist on this TV; skip it.
