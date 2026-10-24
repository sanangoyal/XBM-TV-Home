# PROMPT: debloat an Android TV and install XMB TV Home

You are a coding agent with shell access. Follow this file, then `skill/SKILL.md` for the detail. The user is not technical: keep messages short, ask before anything not obviously safe, and show screenshots of the TV.

Works on most Android TV / Google TV sets. Not Samsung Tizen, LG webOS, or Fire TV. Some China-market sets reject all sideloaded APKs: then do the debloat step only. Android 9+ (API 28+) recommended.

## Before you start

1. Ask the user for: TV brand and model, TV IP address (Settings > Network), and whether the TV is on the same Wi-Fi as this computer.
2. Ask them to enable Developer options (Settings > Device Preferences > About > press Build 7 times), then USB debugging and, if present, Network debugging. Keep the TV awake.
3. Check `adb version`. If missing, install platform-tools.
4. `adb connect <ip>:5555`. Tell the user to accept "Allow USB debugging" on the TV (tick Always allow). Confirm `adb devices` lists `device`.
5. Report model, Android version, RAM, screen size.

## Questions to ask (one checklist, once)

Tick what the family uses; everything unticked is fair game to disable:

- Streaming apps pre-installed by the TV maker (list them by name from the TV)
- Voice assistant / Google Assistant button
- Chromecast / Miracast / screen cast
- System/firmware updates (keep these enabled unless the user says otherwise)
- Smart-home or vendor hub apps
- Movie scenes need a free TMDB API key (themoviedb.org > Settings > API). Optional.

## Procedure

1. **Back up**: `scripts/backup.sh <ip>`. Do not continue if it fails.
2. **Build the list** (`pm list packages`, `-3`, and keyword searches). Research unknown package names. Group them: ads/promos, vendor junk, Google extras, telemetry. Show the table and ask per group. For OnePlus TV start from `scripts/bloat-oneplus-tv.txt`.
3. **Disable approved groups**: `scripts/debloat.sh <ip> <list>`. Disable only (`pm disable-user --user 0`), one package per call, never uninstall. The script writes `restore-bloat.sh`. Screenshot after each group; confirm the TV still works (Home, an app, remote).
4. **Install the launcher**: use the APK from Releases, or build (README > Build from source). `scripts/install.sh <ip> <apk>`. Screenshot.
5. **Make it Home**: let the user pick XMB > Always. Only after XMB is confirmed working and with the user's OK, optionally `pm disable-user --user 0 com.google.android.tvlauncher`.
6. **Verify with the remote**: press keys over adb (`input keyevent KEYCODE_DPAD_RIGHT` etc.), screenshot after each, check Up/Down/Left/Right/Enter/Back and that Home returns to the launcher. Ask the user to try the real remote.
7. **Optional**: ask whether to turn on Settings > Launcher > Agent control so you can manage settings by MCP, then read the address and token off the screen.
8. **Close ADB at the end** (ask first): turn off USB/Network debugging on the TV, revoke authorisations, reboot. Tell the user how to turn it back on.
9. **Hand over**: give the user the restore scripts (`restore-bloat.sh`, `scripts/restore-launcher.sh`) and a short note on what you disabled.

## Never

- Never uninstall; never use root; never flash anything.
- Never disable `com.android.*` core packages, `com.google.android.gms*`, permission controller, package installer, input/tuner/HDMI services, remote/Bluetooth pairing, keyboard, Play Store, Play Services, Settings or TV settings. If unsure, `dumpsys package <name>` and ask.
- Never start factory or test-menu packages, even to look.
- Never disable the stock launcher or the Google TV setup app (`setupwraith`) until XMB is confirmed as Home and Wi-Fi/ADB will survive (check which package owns the saved Wi-Fi first).
- Never disable system updaters, or things the family ticked.
- Never put IPs, Wi-Fi names, emails, serials or account details in files you commit or screenshots you share.
- Stop and report if the TV refuses commands or an install fails verification. Do not retry in a loop.
