# Assets

Everything the launcher uses, as plain files, so you can edit it or reuse it in a port to another platform (tvOS, webOS, Tizen, a desktop shell, a game engine). The app itself builds from the packed copies in `web/src`; these are readable exports of the same material.

| Folder | What | Format |
|---|---|---|
| `sounds/` | XMB navigation sounds: `cursor`, `decide`, `cancel`, `category`, `option`, `focus`, `ok`, `ng`, `click`, `sysok`, `trophy`, `pagein`, `pageout`, `cartadd`, `cartremove`, `checkout`, plus `boot` and `storeboot` | 44.1 kHz WAV, boot sounds MP3 |
| `glyphs/` | The 58 XMB menu glyphs (`g1_*`, `g2_*`, `g3_*` by source sheet), cropped to their own viewBox | white SVG, scale freely, recolour with `fill` |
| `logos/` | App logos used in the lists, single-colour where the design needs it. `logo-fit.json` holds the size and offset used to optically centre each logo | SVG |
| `../android/assets/` | Backgrounds: `bg_*.mp4` loops, `bg_*_g.png` grid and `bg_*_r.png` ribbon stills, `ambient.ogg` | MP4, PNG, Ogg |
| `../android/res/drawable-xhdpi/` | App icon and TV banner | PNG |

How things fit together (layout, spacing, motion, colours, rules): [`docs/DESIGN.md`](../docs/DESIGN.md). Bridge and build rules: [`docs/TECH.md`](../docs/TECH.md).

## Rights

The sounds, glyphs and ribbon styling are modelled on, or taken from, Sony's PlayStation 3 XMB, and the logos belong to their brands. They are here for personal, non-commercial, fan-project use. See [`ASSETS.md`](../ASSETS.md). If you ship a public port, replace the Sony-derived material and use each brand's official assets under its own guidelines.
