# PS5 shortcut + GTA VI-style photo slideshow, as built

Built and tested on a TCL "Smart TV Pro" G08 (board tcl9618/mt5896, 2GB RAM, Google TV 14), but
nothing here is TV-specific except where noted.

## What this adds

The home screen is **this repo** (`com.sanan.xmb`), a PS3 XMB-style launcher forked from
[sanangoyal/XBM-TV-Home](https://github.com/sanangoyal/XBM-TV-Home), with two feature additions
and several bug fixes layered on top.

## Category bar, left to right

Power · Settings · Video · Music · **PS5** (a one-press shortcut to the console, see below) ·
Games · Apps · Sources.

**PS5** is a custom addition, not upstream: one category, the real PlayStation logo (traced from
Simple Icons, MIT). Selecting it calls `Android.inputs()`, matches an input label containing
`playstation`/`ps5` (case-insensitive) and switches straight to it over HDMI-CEC — tested against
a real PS5 whose input surfaced two matching entries (a raw `HW<n>` port labelled "PS5", and a
separate CEC logical input labelled "PlayStation 5"); either one satisfies the match, so it works
whichever one a given board/firmware exposes.

## The wallpaper slideshow

Background is a rotating photo slideshow (built and tested with promotional artwork for a game
release; any set of same-aspect-ratio JPEGs bundled as `android/assets/wallpaper_0.jpg` …
`wallpaper_N.jpg` works the same way — swap the files and the `SL_N` count in `web/xmb.tpl.html`).

This is a real architectural change from upstream XBM-TV-Home, not a config flag:

- The photos are **not** drawn through the native Canvas/SurfaceView path the rest of the
  backgrounds use. They're two stacked `<img id="wpA">`/`<img id="wpB">` DOM layers
  (`file:///android_asset/wallpaper_N.jpg`), cross-faded and slid with `opacity`/`transform` only
  — the two properties a GPU composites for free. That was a deliberate choice to get a slide+fade
  transition without the cost of animating two full-screen bitmaps through the native Canvas blit
  path, which would mean real per-frame compositing work on a GPU-constrained board; CSS
  opacity/transform costs nothing extra once painted.
- Rotation: every 8 seconds (`SL_N`, `SL_I` cycles, see `slideShow()` in `web/xmb.tpl.html`),
  starting index seeded from wall-clock time so repeated app restarts during development don't
  always land on the same image.
- A **scrim** (`#wps`, a static CSS gradient, no blur) darkens the left side of the screen where
  the category bar/item list sit (near-opaque through ~20%, gently fading out by 85% across) so
  the artwork itself stays visible on the right. This replaced an earlier attempt at a blanket
  8-direction text-shadow outline on every element, which worked but looked like a hack, not a
  design — the scrim is the same technique real console UIs (and this project's own TMDB-caption
  code, `#art .sl::after`) already use: text sits on a dark panel over the photo, not outlined
  letter by letter. Its width/opacity curve went through two rounds of tuning (too little contrast,
  then too hard an edge) before landing on the current gentle falloff — expect to retune per
  wallpaper set, it's inherently a matter of taste.
- Images render at their real size, no crop/overscan — an earlier version oversized the `<img>` by
  6% *and* applied a `scale(1.06)` transform on top (double-counting the zoom, compounds to ~12%)
  to leave slide-room without exposing a bare edge; cut in favour of a thin, brief edge gap during
  the ~0.9s transition (invisible in practice since it coincides with the opacity fade) rather than
  crop the image.
- The boot/intro logo ("XMB"/"TV HOME") had no backing at all over a photo background and was
  unreadable. Fix needed its own layer (`#introdim`, toggled by a new `html.intro` class set in
  `startIntro()`/cleared in `finishIntro()`) rather than reusing the existing `#bfx` dimmer — `#bfx`
  lives inside `#stage`, which itself stays `opacity:0` until `#stage.rev` fires at the *end* of the
  intro, so anything inside it is invisible for the entire intro sequence by construction.
- The native `custom` (single user-picked image) background path is untouched and still goes
  through Java (`MainActivity.bgPlay`, off-UI-thread JPEG decode — that fix is real and still
  matters for that path, see below). Only the new `slide` mode moved to the DOM.

## Bug fixes made along the way

- **`html.lite` (the auto-on low-GPU mode) missed `#opts`** (the settings/menu popup's 26px
  backdrop-blur) — only `#app`/`#info` were covered. Added `#opts` to the lite override. This was
  a real, measurable jank source (confirmed via `dumpsys gfxinfo`) independent of anything
  wallpaper-related.
- **Background image decode ran on the UI thread** (`BitmapFactory.decodeFile`/`decodeStream`
  directly inside `bgPlay`), causing multi-hundred-ms freezes on every background change. Moved to
  a worker thread with the result posted back via `runOnUiThread`, guarded by the existing
  `bgGen` staleness counter. Still used for the native `custom` path.
- **Holding Up at the top of a list flew into the status bar** instead of stopping. Root cause:
  the fix can't rely on `KeyboardEvent.repeat` (unreliable through this WebView/remote-key path) —
  track real keydown/keyup pairing instead (`upFresh`, cleared unconditionally on *every* ArrowUp
  keydown, only reset true by an actual keyup). The first attempt only cleared the flag inside the
  bar-entry branch, which missed the case where the hold started a few items below the top — fixed
  to clear on every Up keydown regardless of branch.
- **Icon sizing**: the sheet's existing glyphs are all pre-normalized to a ~75–80 unit bounding box
  before `giN`/`giS` do their area-based scaling. A pasted icon at its native coordinate scale (the
  PlayStation logo's Simple Icons path is a 24-unit viewBox) renders far smaller than its neighbors
  unless wrapped in a normalizing `<g transform="scale(4)">` first. Worth remembering for any
  future icon addition from an external source.
- **The animation snap-threshold bug — this is the one actually worth upstreaming on its own.**
  `animTick`'s per-frame exponential-ease loop stops animating a property once
  `Math.abs(target - current)` is under a threshold, originally a single flat `0.0005` for every
  property it drives (`x`, `y`, `o`, `s`, `lo`, `so`). That's a reasonable snap distance for the
  0–1-range properties (opacity, scale) but `x`/`y` move in page pixels — a category-bar slide
  moves ~200px. Decaying a 200px delta down to within 0.0005px of target, at this loop's position
  time constant (`tau≈63ms`), takes `tau·ln(200/0.0005) ≈ 0.063·12.9 ≈ 0.81s`. So every single
  Left/Right or Up/Down press kept the animation loop "busy" (`ch=true`, still calling `n.fn` every
  frame, still writing styles) for the better part of a second *after the motion was visually
  finished* (human eye can't resolve sub-pixel movement, settles perceptually within ~150–300ms).
  Fixed by scaling the snap threshold per property — `0.05` for `x`/`y` (still far below one screen
  pixel, so nothing looks different) vs the original `0.0005` for the 0–1-range properties. See
  `const snap=...` in `animTick`.
  Reported as still feeling "not much different" after this fix alone by the one tester so far —
  plausible explanations not yet ruled out: (a) the fix is real but the *perceived* choppiness has
  a second, undiagnosed cause riding alongside it; (b) the per-property threshold value chosen
  (0.05px) still isn't generous enough and could go higher (0.5px, 1px) with zero visible cost;
  (c) the underlying exponential time constants (`kp`/`ki`/`ko` — 63/35/60ms) are simply slower
  than this project's own "fitted to recording" comment implies feels right on a TV at couch
  distance, independent of the threshold bug. Worth a maintainer's own eyes on a real device before
  concluding the fix (or this non-conclusion) is complete.
- Alongside the threshold fix, the unselected-item/category scale targets were softened
  (item `.52`→`.72`, category `.7`→`.85`) since the original jump read as items visibly "shrinking
  away" rather than smoothly deselecting — a taste call, not a bug, easy to revert if a maintainer
  disagrees.

## Known follow-ups, not done

- `Sources`' HDMI 2/3 entries still carry upstream's placeholder descriptions ("Soundbar / AVR",
  "Game console") — not customized per-device.
- No `docs/DESIGN.md`/`TECH.md` updates were made to reflect the above; upstream's own design docs
  describe the original author's own setup, not this fork's.
- The bundled wallpaper images in this branch are promotional artwork for an unreleased game
  release, used here purely as a personal-build example of the slideshow feature. **Anyone
  building on this branch should swap them for images they have the rights to redistribute** —
  the slideshow code itself doesn't care what the images are.
