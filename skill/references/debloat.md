# Debloat reference: OnePlus TV (Android 11)

All entries are disabled, not removed. Restore with `./restore-bloat.sh`.

| Package | What it is | Group | Risk |
|---|---|---|---|
| com.eros.now | Eros Now streaming app | Promo apps | none |
| com.jio.media.jiobeats | JioSaavn music app | Promo apps | none |
| com.jio.media.stb.ondemand | Jio on-demand app | Promo apps | none |
| com.google.android.videos | Play Movies & TV | Google extras | none |
| com.google.android.play.games | Play Games | Google extras | none |
| com.oneplus.tv.imagewall | Photo wall / ambient images | Vendor extras | none |
| com.oneplus.tv.userguide | On-screen user guide | Vendor extras | none |
| com.oneplus.tv.screensaver | Vendor screensaver | Vendor extras | none (the launcher keeps the screen awake itself) |
| com.google.android.backdrop | Google ambient mode screensaver | Google extras | none |
| net.oneplus.weather | Weather widget | Vendor extras | none |
| oneplus.smarthome | Smart-home hub app | Vendor extras | disables vendor smart-home control |
| com.google.android.tvrecommendations | Home "recommendations" / promoted content row (the ad row) | Ads | stock home loses its content rows |
| com.google.android.feedback | Feedback agent | Telemetry | none |
| com.google.android.partnersetup | Partner setup | Telemetry | none |
| com.google.android.onetimeinitializer | One-time init | Telemetry | none |
| com.mediatek.TimeMeasurementAgent | Timing/telemetry agent | Telemetry | none |
| com.mediatek.backgrounddetection | Background detection agent | Telemetry | none |
| com.heytap.mcs | Vendor push/messaging service | Telemetry | vendor push messages stop |
| com.heytap.oprcupgradeservice | Vendor upgrade-notification service | Telemetry | in-TV OTA notices stop; update via settings |

Later memory tweaks (optional): `com.oneplus.dlna` (DLNA renderer), `com.android.settings.intelligence` (settings suggestions). Undo with `pm enable`.

Replacing the home screen additionally disables `com.google.android.tvlauncher` (stock launcher), `com.google.android.katniss` (voice/search assistant) and `com.google.android.tungsten.setupwraith`. `scripts/restore-launcher.sh` re-enables them.

## Other brands

Do not copy this list blindly. Build your own: list packages, search unknown names, group them, show the user, disable one group at a time, check the TV after each group.
