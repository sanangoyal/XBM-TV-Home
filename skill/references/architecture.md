# Architecture (short)

```
android/
  src/com/sanan/xmb/MainActivity.java   Activity, JS bridge, video/static BG, ambient music
  src/.../AgentServer.java              MCP server (HTTP JSON-RPC, bearer token)
  src/.../MemGuard.java                 frees RAM for the launcher on weak TVs
  src/.../NotifService.java             notification listener (badges)
  assets/index.html                     generated UI (do not edit; gitignored)
  assets/bg_*.mp4  bg_*_g.png bg_*_r.png  dynamic videos, static grid + ribbon images
  assets/ambient.ogg                    1 loop of the ambient music
web/xmb.tpl.html                        UI source (HTML/CSS/JS), placeholders filled by tools/mk.py
web/src/                                icon/logo/sound source data
tools/mk.py                             assembles web/ -> android/assets/index.html
scripts/                                adb helpers (backup, debloat, install, restore)
```

Rendering stack: FrameLayout [ SurfaceView (background: MediaPlayer video or canvas image) + transparent WebView (UI) ]. The page toggles `html.lite` and CSS transitions to cut cost. Static mode paints one pre-rendered grid with a code-drawn gradient and ribbon layer; no animation, no redraw.

Settings live in the page's localStorage (`xmb_bg`, `xmb_col`, `xmb_mus`). `window.__agent.get/set` is the single entry for agents. Music runs natively: a 100 ms handler fades a looping MediaPlayer in after 5 min without a key.

Rebuild loop: edit `web/xmb.tpl.html` or Java > `python3 tools/mk.py` > `bash android/build.sh` > `scripts/install.sh <ip> android/out/xmb-launcher.apk` > screenshot.
