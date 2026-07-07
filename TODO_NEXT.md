# TO DO NEXT

Picking up after the IPTV categories/EPG/mini-player/aspect-ratio milestone (commit `c2d01f6`,
CI green: https://github.com/LMInventories/streamhub/actions/runs/28862787096). Current state:
Live TV tab with category browsing, on-demand now/next EPG, a muted live mini-player, and a
full-screen player with a 4:3/16:9/Fit/Fill picker + landscape lock + insets fixes.

Four things flagged by the user to pick up next, roughly in the order they were raised:

## 1. Keep the mini-player playing while browsing menus

Right now `LiveTvViewModel.miniPlayerController` is scoped to `LiveTvScreenPhone`/`Tv` via
`hiltViewModel()` - it's a fresh `PlayerController` per ViewModel instance, released in
`onCleared()`. This currently means the preview is *only* alive while the Live TV screen itself
is composed.

Ambiguous requirement worth clarifying with the user before building: does "whilst menus are
browsed" mean -
- (a) just don't let category/channel list scrolling or the category-back-navigation interrupt
  playback (this should already mostly work today - focusChannel() only calls prepare() when the
  channel id actually changes - but verify there's no unwanted restart), or
- (b) the mini-player should persist as a docked/PiP-style overlay when navigating away to Home
  or Settings entirely, i.e. survive leaving the Live TV composable.

(b) is the bigger lift: it means hoisting the mini-player's PlayerController lifetime above the
per-screen ViewModel - e.g. into a small `@Singleton` holder injected wherever a persistent
overlay is rendered (probably in `MainActivity`/`PhoneApp`/`TvApp`, above the NavHost), plus a
floating mini-player composable that stays visible across tab switches until dismissed. Worth
confirming intent before implementing since it changes where the ExoPlayer instance lives.

## 2. Full 7-day EPG grid (traditional multi-channel x time guide)

Currently EPG is "now/next" only, fetched on-demand per focused channel
(`IptvBrowseRepository.getNowNext`). A real grid view needs:

- **Xtream**: `get_short_epg`'s `limit` param only returns a handful of upcoming programs: for a
  7-day guide, either raise `limit` substantially per channel (many small calls - N+1 across
  every visible channel, likely too slow/rate-limit-prone) or fetch `xmltv.php` in bulk instead
  (Xtream panels serve a full XMLTV dump at `http://host:port/xmltv.php?username=...&password=...`,
  same format we already parse via `XmlTvParser` for M3U sources). Prefer the bulk XMLTV route
  for both source types for consistency - one parser, one caching strategy.
- **Storage**: a week of EPG for a large channel list is a lot of data to hold in memory
  (`IptvBrowseRepository`'s current `cachedEpgByChannelId` in-memory map won't scale well) -
  this is where Room (already in the version catalog dependencies, unused so far per the
  original architecture plan) should finally get wired in: a `programmes` table keyed by
  (channelId, startAt), queried by channel + time range for the grid's visible window, refreshed
  periodically (e.g. once per day) rather than re-parsed every app launch.
- **UI**: a genuine EPG grid widget (channels down the side, horizontal scrolling timeline,
  "now" indicator line, tap a program to see details / jump to live) is a substantial new
  Compose component - horizontally-scrolling `LazyRow` per channel row synchronized against a
  shared scroll state, or a custom layout. Worth a fresh design pass rather than bolting onto
  `EpgInfoPanel`. Likely its own screen/route (`Route.EpgGrid`?) reachable from Live TV rather
  than replacing the mini-player's now/next panel.

## 3. Missing audio on some streams (likely AC3/EAC3 codec support)

Very likely cause: many IPTV/satellite-sourced streams carry Dolby Digital (AC3) or Dolby
Digital Plus (EAC3) audio tracks, which ExoPlayer/Media3's core does **not** decode in software
by default (licensing reasons) - it relies on the device's hardware decoder, and plenty of
phones/TV boxes don't have one, or the stream uses a passthrough-only format the device can't
handle. Symptom matches exactly: video plays, audio is silent, no error surfaced.

Fix: add the Media3 FFmpeg audio decoder extension (`media3-exoplayer-ffmpeg` /
`media3-decoder-ffmpeg` - check current artifact name/version against the Media3 release Media3
was on when this was written, 1.10.1) to `:core-player`, and make sure `PlayerController`'s
`ExoPlayer.Builder` is configured with `setRenderersFactory` using
`DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)` (prefers
platform decoders, falls back to the FFmpeg extension for unsupported formats like AC3/EAC3/DTS
instead of silently failing). Note the FFmpeg extension typically needs to be built from source
or pulled from Maven depending on how Google is currently distributing it for Media3 1.10.x -
verify before assuming a plain Maven coordinate resolves cleanly, since this has changed across
ExoPlayer/Media3 versions historically.

## 4. Show program description/blurb in the EPG info panel

`EpgProgram` (`feature-iptv/src/main/kotlin/.../data/EpgProgram.kt`) currently only carries
`title`, `startAt`, `endAt` - no description field, even though both sources actually provide
one:
- Xtream's `get_short_epg` response includes a base64-encoded `description` field per listing
  (same encoding convention as `title`, already handled by `decodeXtreamText` in
  `XtreamEpgModels.kt` - just needs a second field decoded the same way).
- XMLTV's `<programme>` element has a `<desc>` child (`XmlTvParser.kt` currently only captures
  `<title>` - add the same start/end-tag character-accumulation pattern for `<desc>`).

Once `EpgProgram.description: String?` exists, surface it in `EpgInfoPanel.kt` (currently in
`feature-iptv/src/main/kotlin/.../livetv/EpgInfoPanel.kt`) under the now-playing title/time line,
probably truncated with an ellipsis given the mini-player's limited width - full text could show
in the eventual EPG grid's per-program detail view (see item 2) instead of trying to cram it into
the small preview panel.
