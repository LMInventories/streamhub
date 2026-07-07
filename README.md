# StreamHub

Unified Android client for IPTV, Jellyfin, and Emby - phone and Android TV, one app. Personal-use project, not distributed.

## Status

**Milestone 1: core playback + navigation foundation.** A stub `MockMediaSource` feeds a bundled test clip (sidecar subtitle tracks) and a public multi-track HLS stream through the real player pipeline, on both a phone UI (bottom nav, Material3) and a TV UI (top tabs, tv-material, D-pad focus). Nothing here talks to a real IPTV/Jellyfin/Emby backend yet - see the plan doc for the full milestone sequence.

## Modules

- `app` - entry point, DI wiring, mock data source, nav/home screens for both form factors.
- `core-common` - shared domain (`MediaSource`, `PlaybackItem`, `Route`), no Android deps.
- `core-player` - Media3/ExoPlayer wrapper: track selection, external-player handoff.
- `core-ui-phone` / `core-ui-tv` - per-form-factor theme + scaffold (shared logic lives below the UI layer).
- `feature-player-screen` - the player screen, phone and TV variants, both on `PlayerViewModel`.

Future milestones (`feature-iptv`, `feature-jellyfin`, `feature-emby`, `feature-search`, `feature-favorites`) plug into the `MediaSource` seam without touching the above.

## Building

CI (`.github/workflows/build.yml`) builds a debug APK and runs unit tests on every push, since this dev machine doesn't carry the Android SDK. Grab `streamhub-debug-apk` from the workflow run's artifacts and install it on a phone/TV device via `adb install` or by sideloading.

To build locally instead, you need JDK 17 and the Android SDK (compileSdk 37, build-tools 36+), then:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest test
```
