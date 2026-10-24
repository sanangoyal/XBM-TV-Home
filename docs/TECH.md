# XMB TV Home — Tech Rules
Living file. Every technical decision becomes a rule here.

## Target
- Package `com.sanan.xmb`. WebView shell + JS bridge (`window.Android`). Real Android TV launcher (HOME category).
- Device: OnePlus TV 55 Y1S Pro, Android 11, 4K panel, UI surface 1080p. ADB over network from the Mac.

## Safety rules
- Back up before changing anything on the TV. Always provide a restore script.
- Disable, never uninstall, when debloating. Ask first.
- Verify every step with a TV screenshot. Test Home from inside an app.
- Do not drop UI resolution to 720p.
- No VPN on the Mac (it drops the bridge).
- Use adb-shell Python if macOS blocks adb.

## Bridge API (`window.Android`)
`apps()`, `launch(pkg)`, `open(spec)` (`c:pkg/.Class`, `a:action`, `i:pkg`), `can(spec)`, `settings(action)`, `inputs()`, `input(id)`, `dim(bool)`, `log(m)`, `status()` (net/notif count/account), `avatar()` (data URI or ""), `coldStart()` (true once per device boot), `notifs()` (JSON of active notifications).

## Data sources
- Network: ConnectivityManager/NetworkCapabilities (eth/wifi/none).
- Notifications: `NotifService` (NotificationListenerService). Grant after install: `cmd notification allow_listener com.sanan.xmb/com.sanan.xmb.NotifService`.
- Account: Android 11 hides accounts from third-party apps. The avatar shows the initial of the address in `/sdcard/xmb-account.txt` (one line), or "G" if the file is absent.
- Avatar photo: Google keeps it private; OAuth route never returned on this TV. Loads `files/avatar.jpg` if present.
- Cold start: compares device boot time (`currentTimeMillis - elapsedRealtime`) with stored value in prefs `av`/`boot`.

## Settings navigation
- Stock Settings = `com.android.tv.settings` (priv-app). `openSet(title)` walks `SETACT[title]` spec list, falls back to `android.settings.SETTINGS`.

## Build (cloud, no Gradle)
- Template source of truth: `scratchpad/xmb3.tpl.html`. Backups before each change: `xmb3.pre_*.html`, `MainActivity.pre_*.java`.
- Toolchain: aapt2, javac `--release 8`, R8 D8 (`/tmp/r8.jar` from dl.google.com r8-8.5.35), zipalign, apksigner (`dbg.jks`, pass `android`, alias `k`). SDK `/opt/androidsdk`, build-tools 34.0.0, android-30.
- Rebuild: `python3 mk3.py && cp /mnt/user-data/outputs/xmb-launcher.html xmbapp/assets/index.html && sed TMDB key in && cd xmbapp && bash build.sh`.
- JS syntax check before deploy: parse `<script>` with `new Function`.
- Avoid name clashes with globals (e.g. `bar` is the category bar element; bar nav state is `bnav`).

## Deploy
- `device_commit_files` limit 30 MB, APK ~34 MB → split into 2×17,000,000 B parts, `cat` on the Mac, install with `adb install -r`.
- After install: wake, re-grant notification listener, force-stop, press HOME, wait ~10–15 s (intro), screenshot.
- Screenshots: `adb exec-out screencap -p`; convert with `sips -Z`; Netflix is DRM-black.

## Known open items
- Blue video unconfirmed on TV; ~1 s intro stall; HDMI sources and Back/Menu inside launcher untested; debug logging + WebView debugging still on; ZEE5 mark muddy; no real Restart/Shutdown; real Google avatar photo.

- Bar centring: `#info .l,.r{align-self:stretch;align-items:center}`; `#dt,#tm` flex, height 64px, padding-top 3px. Tweak padding-top/translateY if off.

- Bridge `Android.icon(pkg)`: renders app launcher icon (adaptive ok) to 144px PNG data URI, cached in Java map. JS fills `img.aic[data-pkg]` one per 40ms starting 4s after load (no startup cost). PLG map pkg->LG key picks our logos.
- LG entry `ytmusic` built in mk3.py from `yt/Asset 1YTmusic.svg` (style block stripped, fill inline).
- Launcher restores last category/folder after force-stop; remote nav tests must account for it. LEFT/RIGHT change category only at list top.

## Google launcher switch
- `com.google.android.tvlauncher` is DISABLED on TV (debloat). If enabled it gets HOME priority 2 > ours (0) and steals HOME.
- Power item `googleHome()`: `Android.open('c:com.google.android.tvlauncher/.MainActivity')`; on fail opens App info (`i:`), user presses Enable.
- Return to ours: `adb shell pm disable-user --user 0 com.google.android.tvlauncher` (or restore-launcher.sh), then HOME.

## Submenu
- `layout()` toggles `html.sub` from `CATS[cat].cur.parent`. CSS filter blur on `#art,#bgv,#gl`.
- ArrowLeft: if `l.parent` go to parent, else change category.

## Long-press
- Enter keydown on pkg items is deferred: timer 550ms or `e.repeat` opens `openOpts()`; keyup w/o long fires synthetic Enter (`lpGo`). optOpen ignores repeats.
- Bridge `open()` specs added: `d:pkg` (ACTION_DELETE uninstall), `m:pkg` (market://details).
- adb `--longpress` is unreliable for testing (sent too fast); test with real remote.

- App menu = real child list (`it.am=1`, `it.children`, rows have `fn`) built for every pkg item outside Power/Settings; `amOpen()` enters it. Enter on `it.fn` row runs it.

- Icon symbols `src_cat`, `src_av`, `src_hdmi`, `src_tv` in the sheet (inline SVG paths from npm: @material-symbols/svg-400, bootstrap-icons, @phosphor-icons/core). `own/livetv.svg` = Phosphor television-simple.

- `html.sub` = submenu OR bar focus (`bnav>=0`); `html.onbar` dims menu. `let bnav` declared before layout (TDZ).
- Avatar click: `Android.account()` starts tv.settings `.accounts.AccountSyncActivity` with extras account_name/account_type from `gacct()`. The old `AccountActivity` exits immediately on this TV (that was the "does nothing").
- Video dark state `vDk` depends on `artTint` only (set when image shown), not `artPend`.

## Performance (TV, gfxinfo jank; run `~/tv-debloat/perf.sh install`)
- Baseline: nav 47-53% janky, p50 15-17ms, p90 32-34ms. Now ~28-30% janky, p50 12ms, p90 28ms (menu nav, Video cat nav, category switching).
- Rules learned:
  - BG video must be 1280x720 @24fps h264 main crf27 (~1.2MB each; originals 1080p30 16MB kept in scratchpad `vid_orig/`). UI stays 1080p; only the soft video is 720p. APK now ~3MB (no split needed).
  - `setT` (CT mode) skips when target unchanged. layout() iterates every item each key; unchanged writes were the main cost.
  - No `will-change` on `.item/.pitem` (hundreds of hidden layers on a 2GB TV = swap + jank). Toggling will-change per visibility made it far WORSE (88% jank): never do it.
  - App menus (long-press) are built lazily on first open.
  - `--tint` is set on `#toast` only, never on `<html>` (root custom property change restyles whole tree).
  - `vidTick` runs at 200ms (was 50ms).
- Remaining cost: GPU thread compositing (video 24fps + masked logos); TV has ~75MB free RAM and ~420MB swap used. Ideas not yet tried: pause video during nav bursts, fewer `.lgo` CSS-mask layers, rasterize logos to PNG.

## Native BG video + perf round 3
- BG video plays NATIVELY: `MainActivity` has FrameLayout[SurfaceView(MediaPlayer, looping, muted) + transparent WebView]. Bridge: `bgv(key)`, `bgvShow(bool)`, `bgvPause(bool)`; JS `NV` flag adds `html.nv`; `#bgv` becomes a black cover whose opacity = dark/intro fade. Fallback `nvFail()` returns to the in-page <video>. Videos are 1080p24 h264 high crf19 (~12.5MB each, stored uncompressed in assets for openFd). Originals in scratchpad `vid_orig/`, 720p versions at /tmp only.
- While in a submenu or bar focus the native video can't be CSS-blurred: it is dimmed (cover opacity .3) instead; art blur unchanged.
- Video freezes (pause, last frame stays) while keys were pressed in the last 1.5s (`bgvPause`).
- MemGuard (`MemGuard.java`): keeps 3 most recent launchable apps used within 15 min + any playing media; kills other launchable apps' background processes. First run 120s after process start, then every 3 min; skipped while the launcher is foreground and a key was pressed in the last 30s. Needs `appops set com.sanan.xmb GET_USAGE_STATS allow` (re-run after every reinstall). Off switch: `appops set ... deny`. Protected prefixes in the class.
- Logos are now `<img>` (blob URL per logo, SVGs whitened at build time in mk3.py) instead of CSS-masked divs.
- Measured (jank, menu nav): 720p in-page 28-30% | 1080p in-page 60% | 1080p native 38-42% | + img logos/freeze/idle guard 38-39% (cat switching 49%). Video-off floor is the same, so the remaining cost is the page + transparent WebView compositing, not video.
- `perf.sh [install] [black]` in tv-debloat repeats the test.

## Page-cost experiments (Oct 24)
Method: inject CSS via `am start --es js` debug hook, `dumpsys gfxinfo` jank over 8 DOWN + 8 UP.
- Hiding #items: 42%->26%; hiding all UI: 16%. Item list is the cost.
- Text-shadow/filters/icons/.t/.s/.ic transitions/duration/`.nt` skip hidden: no gain.
- Transitions off on .item only or .cat only: no gain. Both off: 54%->28%. Stepped easing: no gain.
- Idle: ~25 fps frames, 25% jank = native 24fps video forcing WebView recomposite. No CSS/JS animations idle.
- Conclusion: cost = per-frame compositing of animating 1080p transparent WebView over SurfaceView. Only no-animation fixes it.

## Static BG + launcher settings
- Static = native SurfaceView gets a jpg drawn via lockCanvas (`bgvStatic(true)`), no MediaPlayer, 0 idle redraws. Assets `bg_gold.jpg`, `bg_blue.jpg` (frame at 3s of the videos), custom = `files/custom_bg.jpg` (downsampled to <=1920w by `setCustom`).
- Prefs in localStorage `xmb_bg|col|man|img|night` (object `PF`). `vidKey()` order: intro, night, static image, scrCol, black mode, manual, random, auto.
- File browser: `Android.ls(path)`, needs `appops set com.sanan.xmb MANAGE_EXTERNAL_STORAGE allow` (+ READ_EXTERNAL_STORAGE grant) after each reinstall.
- Metric caveat: gfxinfo jank % is not comparable between video and static (video adds cheap frames). Use p50/p90 frame time + feel.

## Static render + solids + dark video (update)
- Surface RGBA_8888. Gold/blue/black static = grid PNG 17x9 (bilinear, dither) + ribbon PNG (PorterDuff ADD). Avoids banding/blotch of jpg.
- Solid `sc_*` = code gradient (Linear+Radial) + 4/255 noise tile; black c1 0xFF34363B c2 0xFF040405. No image assets.
- Dark video v5: ffmpeg temporal median (removes particles, keeps lines crisp) + supersample AA; blend filters need gbrp. Script mkA.sh. Gold/blue videos 1080p24 stored uncompressed (`zip -0`), apk ~29MB (commit limit 30MB).
- Stale flag fix: JS always calls `Android.bgvStatic(STATIC)` in apply()/boot.
- Bridge: bgvStatic, hasCustom, ls, setCustom. After each reinstall: allow_listener, appops GET_USAGE_STATS + MANAGE_EXTERNAL_STORAGE, pm grant ACCESS_COARSE_LOCATION + READ_EXTERNAL_STORAGE.
- Panel UI: `#opts.rp` CSS, `openMenu({panel:1,desc,rows})`.
- vidKey auto -> tod() by getHours; random RK=rpick() on load + setInterval 30min.
- Dead code removed (ponytail audit): Bridge.loc()/can(), JS TILE/known, CSS .bw, ACCESS_COARSE_LOCATION permission (least privilege).

## Ambient music, agent control, about (v1.0)
- `ambient.ogg`: one 187.7 s loop cut from the 1 h source (autocorrelation found the repeat period), 6 s equal-power crossfade at the seam, Opus 40 kbps, ~1 MB. Stored uncompressed in the APK. Native MediaPlayer, `ambCfg(vol,preview)` bridge, 100 ms handler fades in after 5 min without a key (static `lastKey`), fast fade out on key, pauses off-screen.
- Keep awake: `FLAG_KEEP_SCREEN_ON` on the window.
- `AgentServer.java`: ServerSocket on 8765, POST /mcp JSON-RPC (initialize, tools/list, tools/call, ping), `Authorization: Bearer` token from SharedPreferences `agent`, off by default. Bridge: `agentInfo/agentSet/agentNewToken`. JS side `window.__agent.get/set`. Screenshot uses PixelCopy of the window (includes the SurfaceView).
- Image scenes: `#art .hw` is full-bleed; the left fade is `#art .sl::after` (100% black at the edge, to transparent by 72%).
- About panel: last item in Launcher settings.

## Background video rules (learned the hard way)
- A SurfaceView surface that has been drawn on with `lockCanvas` (static mode) cannot be handed to the video decoder again (`IllegalStateException`). Leaving static mode must recreate the surface: hide the SurfaceView, show it again, let `surfaceCreated` start the video (`resetSurface()`).
- This TV's MediaTek AVC decoder fails to allocate buffers if players are torn down and rebuilt in quick succession. `bgPlay` waits 250 ms and only the latest request starts (`bgGen`).
- Never call `start()`, `pause()` or `isPlaying()` on a player before `onPrepared` (`mpReady`). Doing it gives error -38.
- Errors from a player we already replaced are ignored. A real error retries up to 3 times before the JS side falls back (`nvFail`). `nvFail` is permanent until restart, so it is the last resort.
- Colour change in dynamic mode: do nothing native in `bgvStatic`; JS calls `bgv(newKey)` once.
