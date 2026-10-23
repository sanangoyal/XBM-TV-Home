# XMB TV Launcher

**The classic PlayStation 3 XrossMediaBar, rebuilt as a fast, clean home screen for Android TV.**
Debloat the TV, install the launcher, let your AI agent manage it.

Made by [Sanan Goyal](https://sanangoyal.com) · MIT licence · see [ASSETS.md](ASSETS.md) for third-party material

## Features

| | | |
|---|---|---|
| **Classic Sony PS3 style UI**<br>Horizontal categories, vertical items, glowing ribbon. The XMB you remember. | **Custom optimised UI**<br>Hand-tuned for 2GB RAM TVs. Lite rendering paths, memory guard, no heavy effects. | **Dynamic backgrounds**<br>Looping ribbon video in gold, blue and dark, or a static image. Auto colour by time of day. |
| **Authentic system audio**<br>Real XMB navigation sounds. Soft ambient music after 5 min idle. | **Speed**<br>Static mode draws one image and idles at zero redraws. Smooth on weak hardware. | **AI / MCP enabled**<br>Built-in MCP server on the TV. Agents read and change settings, press keys, open apps, take screenshots. |
| **Customisable**<br>Background, colour, solid colours, your own image, music volume, all on the TV. | **Custom clean icons**<br>Consistent glyphs, real brand logos for apps. No hand-drawn mush. | **Remove bloatware and ads**<br>Disable (never uninstall) promo apps, ad services and junk. One command, full restore script. |

Also: trending movie and series scenes (TMDB) behind your apps, full-bleed with a left fade. Stays awake: the UI is the screensaver.

## Install

### Easiest: let your AI do it

Open Claude (or any coding agent that can run shell commands) with `adb` available on your computer, and paste:

```
Read https://github.com/YOUR_USER/xmb-tv-launcher and follow skill/SKILL.md.
My Android TV is at <TV IP>. Back it up first, show me the bloatware you plan to
disable and ask before each group, install the XMB launcher, grant its permissions,
make it my home screen, and confirm with a screenshot. Ask before anything destructive.
Keep a restore script.
```

That is all. The agent connects over ADB, backs up, debloats, installs, verifies. The skill in `skill/` tells it exactly how.

### Custom install (manual)

1. **TV:** Settings > Device Preferences > About > tap *Build* 7 times. Then Developer options > enable *USB debugging* / *Network debugging*. Note the TV's IP (Network settings).
2. **Computer:** install `adb` (macOS `brew install android-platform-tools`, Linux `apt install adb`, Windows: platform-tools zip).
3. **Connect** (accept the prompt on the TV):

```
adb connect <TV-IP>:5555
adb devices
```

4. **Back up** (always):

```
scripts/backup.sh <TV-IP>
```

5. **Debloat** (optional; disables, does not uninstall; edit `scripts/bloat-oneplus-tv.txt` first for other brands):

```
scripts/debloat.sh <TV-IP>          # writes restore-bloat.sh
```

6. **Get the APK:** download `xmb-launcher.apk` from Releases, or build it (below).
7. **Install and grant:**

```
scripts/install.sh <TV-IP> xmb-launcher.apk
```

8. **Make it home:** press Home on the remote, pick *XMB*, choose *Always*. If the stock launcher keeps winning:

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

9. **Undo everything:** `scripts/restore-launcher.sh <TV-IP>` and `./restore-bloat.sh`.

Tested on OnePlus TV 55 Y1S Pro (Android 11, 1080p UI, 2GB RAM). Android 9+ should work.

## Settings (on the TV: Settings > Launcher)

| Item | Options |
|---|---|
| Background | Static (lightest), Dynamic (video) |
| Color | Auto (blue morning, gold afternoon and evening, dark night), Random (each load and every 30 min), Gold, Blue, Dark, Custom image |
| Solid colour | Black, Ruby, Amber, Emerald, Teal, Indigo, Violet (static) |
| Choose custom image | File browser: TV storage or USB (static) |
| Ambient music | Off, Soft, Medium, Loud (plays after 5 min idle, stops on any key) |
| Agent control | On/Off, shows address and token, New token |
| About | Credits, licence, disclaimers |

## AI control (MCP)

Settings > Launcher > Agent control > On. The TV shows the endpoint and a token.

```
URL:     http://<TV-IP>:8765/mcp
Header:  Authorization: Bearer <token>
```

Add it to any MCP client (HTTP transport). Tools: `status`, `get_settings`, `set_setting`, `press_key`, `list_apps`, `open_app`, `screenshot`.

Quick test:

```
curl -s -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_settings","arguments":{}}}' \
  http://<TV-IP>:8765/mcp
```

Off by default. LAN only: do not expose the port to the internet. Without it, agents can still use `adb shell am start -n com.sanan.xmb/.MainActivity --es js '<javascript>'`.

## Build from source

Needs JDK 8+, Android SDK (`platforms/android-30`, build-tools 34), the R8 jar, Python 3, `zip`.

```
echo YOUR_TMDB_V3_KEY > android/tmdb.key     # free key from themoviedb.org
export ANDROID_HOME=/path/to/sdk R8_JAR=/path/to/r8.jar
bash android/build.sh                          # -> android/out/xmb-launcher.apk
```

`tools/mk.py` assembles the UI (`web/xmb.tpl.html` + icon, logo and sound data) into the app. Docs: [docs/DESIGN.md](docs/DESIGN.md), [docs/TECH.md](docs/TECH.md).

## For AI agents

Start at [`skill/SKILL.md`](skill/SKILL.md). It covers connecting to the TV, backup, safe debloating, installing this launcher, and using it as an example for building your own custom TV UI.

## Disclaimer

Provided as is, no warranty. Use at your own risk; the author is not liable for damage, data loss, bricked or misbehaving devices. Disabling system apps can break TV features: back up first and keep the restore scripts. Personal, non-commercial use intended.

Not affiliated with Sony, Google, Netflix, Amazon, Apple, YouTube, Spotify or any app shown. "PlayStation" and "XMB" are trademarks of Sony Interactive Entertainment. Movie data and images courtesy of TMDB; this product uses the TMDB API but is not endorsed or certified by TMDB. Rights holders: see [ASSETS.md](ASSETS.md).

## Licence

MIT. See [LICENSE](LICENSE). Copyright (c) 2026 Sanan Goyal.
