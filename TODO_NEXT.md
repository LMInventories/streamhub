# TO DO NEXT

Updated after working through items 1, 3, and 4 below (commits `ccd62e8`, `c2ef766`, and the
mini-player-continuity nav fix). Design system landed too (`51bca74`) - dark-first palette,
Space Grotesk/Inter/JetBrains Mono type, per-source badge colors, the Signal Bar progress motif,
all in a new `:core-design` module shared by both themes.

## Nav restructure (post item-2) - DONE

Per user request, on top of the 7-day grid below:
- Bottom nav / TV tab row is now 5 tabs: Home, Live TV, VOD, Emby, Jellyfin.
- Home is a dashboard of 4 entry cards (Live TV/VOD/Jellyfin/Emby) rather than a flat aggregated
  item list - `HomeViewModel`/`MediaSource` aggregation is kept (unused by Home now) since it's
  still the intended seam for the future Master Search milestone, not dead code to remove.
- New VOD tab: Xtream Codes movies only (`get_vod_categories`/`get_vod_streams`) - category list
  -> poster grid -> plays through the existing Player screen. M3U sources show a "needs Xtream"
  message since M3U has no standardized way to separate VOD from live channels. Series
  (`get_series`/`get_series_info`) not implemented - movies only for now.
- Emby/Jellyfin tabs show a shared `ComingSoonScreen` placeholder until M3/M4 land for real.
- New Settings hub (`Route.Settings`) lists Live TV & VOD (-> existing IptvSettingsScreen),
  Jellyfin, Emby (both disabled/"not set up yet") - reached from Home's gear icon. Live TV/VOD's
  own gear icons stay a direct shortcut to IptvSettingsScreen (most relevant, no extra hop).
- The 7-day EPG grid (see item 2) is no longer a separate screen - it's now `EpgGridPanel`,
  inlined directly into the Live TV screen: phone shows it only in landscape (portrait keeps the
  plain channel list + the existing now/next panel), TV shows it unconditionally since TV is
  always landscape-shaped. Also added a real download-percentage progress bar (`SignalBar`,
  streamed via okio byte-counting against the response's Content-Length) shown while the guide is
  actually being fetched - not shown at all on a cache hit.
- Fixed a pre-existing bug found while touching this area: `IptvSettingsScreen` uses mobile
  Material3 components but is reachable from the TV nav host, which only provides tv-material3's
  MaterialTheme - added a local M3 dark color scheme wrapper (same pattern as EpgGridPanel/
  PlayerScreenTv's dialogs) so it renders correctly on TV instead of falling back to M3's default
  light scheme.
- Fixed a real correctness bug introduced by merging VOD into `IptvMediaSource.browse()`: Xtream
  live and VOD stream ids are separate numbering spaces and can collide, so a naive merge could
  resolve playback to the wrong (same-numbered) item. VOD ids are now namespaced (`vod:<id>`) via
  `vodPlaybackId()`, used consistently by both `IptvMediaSource` and `IptvVodRepository`.

## 1. Keep the mini-player playing while browsing menus - DONE (narrow interpretation)

Two real bugs fixed:
- `PhoneNavHost`/`TvNavHost`'s bottom-nav `onNavigate` only had `launchSingleTop = true`, missing
  the standard `popUpTo(startDestination) { saveState = true }` + `restoreState = true` pattern.
  Without it, switching Home -> Live TV -> Home -> Live TV pushed a *fresh* backstack entry (and
  therefore a fresh `LiveTvViewModel`/mini-player) every time instead of resuming the one already
  running. Now tab switches properly save/restore each tab's ViewModel state, including the live
  mini-player.
- `LiveTvViewModel.clearCategorySelection()` was calling `miniPlayerController.pause()` and
  clearing `focusedChannel` - directly contradicting "keep showing the selected channel while
  menus are browsed." It now only resets `selectedCategory`/`channels`, leaving the mini-player
  and its EPG info completely untouched. `selectCategory()` also no longer auto-switches the
  preview when browsing into a *different* category if something's already focused - only
  auto-previews on first load.

**Not done**: a true cross-app floating/PiP mini-player that survives navigating to Home or
Settings *from a cold start* where Live TV was never opened this session, or that floats above
non-Live-TV screens as an actual overlay (the marketing screenshot's PiP feature). What's
implemented now covers "don't interrupt browsing within Live TV" and "resume when switching back
to the Live TV tab" - a real docked overlay visible from every screen is a bigger, separate
feature (hoist the PlayerController out of `LiveTvViewModel` into something above the NavHost)
worth its own design pass if still wanted.

## 2. Full 7-day EPG grid (traditional multi-channel x time guide) - DONE

Built as planned: bulk `xmltv.php` fetch for Xtream (same endpoint/parser M3U's optional EPG URL
already used), Room-backed cache (`EpgDatabase`/`EpgDao`/`ProgrammeEntity` in `feature-iptv`,
refetched roughly once a day via a DataStore timestamp), grid UI with channel labels fixed in a
left column and program blocks positioned by absolute offset inside a horizontally-scrolling
timeline (robust to real-world gaps/overlaps in provider data), one shared `ScrollState` keeping
every row's timeline and the time header in sync.

**Revised after initial ship**: originally a separate screen/route reachable via a "7-day guide"
button. Per user feedback, it's no longer a separate screen - it's inlined directly into the Live
TV screen (`EpgGridPanel`), landscape-only on phone (portrait keeps the plain channel list), and
shown unconditionally on TV (always landscape-shaped). A live download-percentage progress bar
(`SignalBar`, real byte-count progress via okio against the response's Content-Length) shows
while the guide is actually downloading - see "Nav restructure" above for the full list of
related changes.

**Not done**: no "now" indicator line on the timeline, and tapping a program block just focuses
that channel rather than showing a program detail popup - both reasonable follow-ups if wanted.

## 3. Missing audio on some streams - DONE

Root cause was AC3/EAC3 (Dolby) audio, which core Media3 doesn't decode in software for
licensing reasons. Fixed via `org.jellyfin.media3:media3-ffmpeg-decoder` (GPL-3.0, fine for
personal-use distribution) - Google's own FFmpeg extension isn't on Maven at all. Required
downgrading `androidx.media3` from 1.10.1 to 1.9.0 to match the decoder extension's target
version and avoid a cross-version ABI risk. `PlayerController` now builds its `ExoPlayer` with
`DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)`.

**Worth watching**: if a future Media3 upgrade is wanted, check whether
`org.jellyfin.media3:media3-ffmpeg-decoder` has a release matching the new version first -
https://github.com/jellyfin/jellyfin-androidx-media/releases.

## 4. Show program description/blurb in the EPG info panel - DONE

`EpgProgram.description: String?` now exists, decoded from Xtream's base64 `description` field
and XMLTV's `<desc>` element (`XtreamEpgModels.kt`, `XmlTvParser.kt`). Shown truncated (2 lines,
ellipsis) under the current program's Signal Bar in `EpgInfoPanel.kt`. Full untruncated text
should show in the eventual EPG grid's per-program detail view (item 2) instead.
