# Porting prompt: XMB TV Home on another TV

Give this to an AI agent (or follow it yourself) when building for a different TV, fresh or already set up.

```
Build XMB TV Home for THIS TV. Use this repo as the guide for structure, look and behaviour, but let the TV's own OS decide the content.

1. Read the device first: model, Android/Google TV version, resolution, RAM, installed apps, Settings activities (adb shell dumpsys, pm list, cmd package resolve-activity). Fresh or already set up: do not assume either, check.
2. Settings: do NOT copy the OnePlus tree. Build the Settings category from what this device actually exposes, in the device's own default order and naming (read it from the stock Settings app: screenshot or uiautomator dump). Only keep our structure (glass rows, icons, confirm popups, Launcher item) as the wrapper. If an item is missing on this OS, leave it out. If the OS adds one, add it. Never reorder to taste.
3. Keep: XMB layout, categories bar, glass material, sounds, motion rules, performance rules (docs/DESIGN.md, docs/TECH.md).
4. Adapt per device: resolution scale (stage is 1920x1080), RAM budget (static background on <=2 GB, video optional), remote key map, app list and package names, input sources, Home intent filters.
5. Stock screens we cannot restyle (translucent Settings etc.): use the cover behaviour (onPause -> cover(1), dim and hide UI), do not try to theme them.
6. Safety: back up first, disable never uninstall (ask), verify with TV screenshots, no API keys in git or APK, ADB and Agent control off when done.
7. Update docs/DESIGN.md and docs/TECH.md with anything device-specific you changed.
```

Notes
- Confirm popups (`SETCONF` in `web/xmb.tpl.html`) exist only for destructive or confusing items (Factory reset, audio/picture restore). Developer options opens directly.
- The licensed font `clarendon.otf` is not in the repo. Without it the fallback font is used.
