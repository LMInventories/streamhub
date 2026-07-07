# TO DO NEXT

Updated after working through items 1, 3, and 4 below (commits `ccd62e8`, `c2ef766`, and the
mini-player-continuity nav fix). Design system landed too (`51bca74`) - dark-first palette,
Space Grotesk/Inter/JetBrains Mono type, per-source badge colors, the Signal Bar progress motif,
all in a new `:core-design` module shared by both themes.

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

## 2. Full 7-day EPG grid (traditional multi-channel x time guide) - NOT STARTED, biggest remaining item

Currently EPG is "now/next" only, fetched on-demand per focused channel
(`IptvBrowseRepository.getNowNext`). A real grid view needs:

- **Xtream**: `get_short_epg`'s `limit` param only returns a handful of upcoming programs: for a
  7-day guide, either raise `limit` substantially per channel (many small calls - N+1 across
  every visible channel, likely too slow/rate-limit-prone) or fetch `xmltv.php` in bulk instead
  (Xtream panels serve a full XMLTV dump at `http://host:port/xmltv.php?username=...&password=...`,
  same format already parsed via `XmlTvParser` for M3U sources - and now includes `<desc>` too,
  per item 4 below). Prefer the bulk XMLTV route for both source types for consistency - one
  parser, one caching strategy.
- **Storage**: a week of EPG for a large channel list is a lot of data to hold in memory
  (`IptvBrowseRepository`'s current `cachedEpgByChannelId` in-memory map won't scale well) -
  this is where Room (already in the version catalog dependencies, unused so far per the
  original architecture plan) should finally get wired in: a `programmes` table keyed by
  (channelId, startAt), queried by channel + time range for the grid's visible window, refreshed
  periodically (e.g. once per day) rather than re-parsed every app launch.
- **UI**: a genuine EPG grid widget (channels down the side, horizontal scrolling timeline,
  "now" indicator line, tap a program to see details / jump to live) is a substantial new
  Compose component - horizontally-scrolling `LazyRow` per channel row synchronized against a
  shared scroll state, or a custom layout. Worth a fresh design pass (reuse `core-design`'s
  Signal Bar for the "now" indicator?) rather than bolting onto `EpgInfoPanel`. Likely its own
  screen/route (`Route.EpgGrid`?) reachable from Live TV rather than replacing the mini-player's
  now/next panel.

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
