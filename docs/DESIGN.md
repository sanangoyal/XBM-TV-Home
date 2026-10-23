# XMB Launcher — Design Rules
Living file. Every design decision becomes a rule here. Newest changes go in the Changelog at the bottom.

## Identity
- PS3 XMB look on Android TV. Horizontal category bar, vertical item list, wave background, glass UI.
- Stage is 1920×1080 design space. CSS viewport 960×540 @ DPR 2, scaled by `fit()`. Never drop UI resolution to 720p.

## Typography
- Font: "SCE-PS3 Rodin LATIN" (embedded woff2) everywhere, incl. top bar.
- Top bar date and time: same font, same size (28px), same weight (500), tabular numerals. No size/weight mismatch between them.
- Bar icons 30px (+5px padding box). Popup rows 28px, popup title 23px.

## Colour
- UI foreground white. Opacity levels via `--o-*` vars.
- Glass fill: white 7–16% gradient, 1px white 34% border, inset top highlight, soft drop shadow.
- No Google-coloured avatar ring, no red badges/dots anywhere.
- Background palette follows time-of-day / colour mode; intro starts gold/brown, then blue video.
- Disabled/no-network icon: 40% opacity.

## Top bar (floating info bar)
- Position: top 75px (design px), right edge flush and bleeds 20px past screen edge. Width 730px.
- Corners: 3px radius on left side, square on right. No right border.
- Order left→right: avatar · network · bell · (gap) · date · time.
- Avatar: signed-in Google account; real photo if available, else initial letter on grey circle.
- Network icon: Google launcher Wi-Fi glyph; Material "lan" glyph for ethernet. Bell: Material "notifications", no badge.
- Focus style: scale 1.28 + radial white glow, no box/hard edge. Avatar keeps circular 2px white ring.
- Icon size 37px (avatar 38px). Network/bell icons.
- Alignment: all bar items share one vertical centre line. Date/time are flex-centred spans, `line-height:1`, `height:64px`, `padding-top:3px` optical offset. Groups `.l`/`.r` use `align-self:stretch`.
- Fades in with the UI reveal; hidden during intro.

## Navigation
- Up from the top row of any menu enters the bar on avatar. Left/Right moves avatar→network→bell. Down / Back leaves the bar.
- OK actions: avatar → Google account settings; network → Network settings; bell → glass popup listing notifications ("No notifications" when empty).
- Date/time are display-only, not focusable.

## Screens and flow
- No "Press any button" gate on the TV. Remove demo-only screens.
- Intro (logo + animation) plays ONLY on first launch after TV boot. Coming back from an app/Home goes straight to the XMB. Power→Restart/Shutdown replays it.
- Home resets XMB to the Video category.

## Settings tree
- Flatten redundant submenus. Leaves: Network and internet, Sound, Remotes and accessories.
- Groups: Picture (Picture Mode, Picture settings), Personalise, Apps, Accounts and sign-in, More Settings.
- Stock right-side Settings panel cannot be restyled (no overlayable resources); our own glass popup (`#opts`) is used for our menus.
- No Cast in Sources.

## Performance rules
- `html.lite` auto-on for host: no backdrop-filter, no SVG filters/drop-shadow filters.

## Changelog
- 2026-10-23: floating bar introduced; Google icons; no badge; date/time unified; bar bleeds off right edge; 3px corners; bar focus navigation; intro only on cold boot; gate screen removed.

- 2026-10-23: bar icons 42→37px; bar items centred on one line; focus = glow, no box.
- 2026-10-23: left-edge fade over BG: mask on `#art .hw` fixed div, 120px wide, black gradient .4→0, pointer-events none, above video/canvas, below stage. Hides bright-image hard edge.
- 2026-10-23: YouTube Music uses supplied white ring+play logo (mono, same as other logos). All Apps list: our mono logo if we have one, else app's own launcher icon (square, 84px, 16px radius, soft shadow). Letter-circle fallback removed.

## Submenu behaviour
- LEFT inside a submenu = Back (same as Back key). At category level LEFT/RIGHT change category.
- While inside a submenu (`html.sub`), BG (`#art`, `#bgv`, `#gl`) blurs 5px + brightness .82 + scale 1.05 (hides blur edge vignette), .35s ease. Removed on exit.

## Power menu
- Last item "Google TV Home" (house glyph `pw_home`, stroke 22) switches to stock Google launcher.

## Own logos
- Camera, File Manager, BrowseHere, Bluetooth Stereo, Live TV: hand-drawn white 100x100 SVG masks (`own/*.svg`), 62px.

## App long-press menu
- Hold OK on any app item (550ms or first key repeat) = inline submenu beside the app (same as folders, with BG blur): Open, App info, Uninstall, Play Store page. LEFT/Back closes. Short press launches on key-up. No popup.

## Sources icons (open-source, not hand-drawn)
- Category: Material Symbols `input` (Apache 2.0). HDMI: Bootstrap Icons `hdmi` (MIT). AV: Material Symbols `settings_input_svideo`. USB: original kept.
- Live TV everywhere (Apps, Sources, All Apps): Phosphor `television-simple` (MIT). Rule: use only vetted open-source icon sets for system glyphs; do not draw them.

## Top bar focus
- LEFT from the first bar icon (avatar) returns to the menu.
- While the bar is focused: BG blurs (same as submenu) and menu (`#bar`,`#items`) dims to 30% so the bar selection reads clearly.

## Backdrop loading
- Focused item with recommended-art: default video BG keeps playing until the image has fully loaded; then crossfade to dark + image. Item change before load keeps the video.

## Launcher settings (Settings > Launcher)
Background: Static (default) / Dynamic. Color (dynamic only): Auto / Select manually (Gold, Blue) / Random per launch. Static image: Gold / Blue / Black / Custom. Choose custom image: in-launcher file browser (TV storage + USB). Current choice shown with a check in each popup.

## Launcher settings (update)
- Launcher item is first in Settings, icon `tune`. Children: Background (wallpaper), Color (palette), Solid colour (fill bucket), Choose custom image (add photo).
- Options open in right-side full-height panel, check on current. Menu common to Static and Dynamic. Custom image + Solid colour are static only.
- Color: Auto / Random / Gold / Blue / Dark / Custom image. Solid colour: Black, Ruby, Amber, Emerald, Teal, Indigo, Violet; each a slight gradient.
- Auto colour: 05-12 blue, 12-19 gold, 19-05 dark (TV local time). Random: new pick (gold/blue/black) each load + every 30min.

## v1.0 additions
- Settings > Launcher: Background, Color, Solid colour, Choose custom image, Ambient music (music_note), Agent control (smart_toy), About (info). Each item has its own icon.
- Auto colour: blue 05-12, gold 12-19, dark 19-05 (TV local time). Random: new colour each load and every 30 min.
- Up inside a submenu never jumps to the top bar; Left leaves the submenu first.
- Image scenes full-bleed, black fade from the left, starts fully black at the edge.
