# Canary maintenance and appearance review

## Scope and history

The review covers the 59 commits reachable from `canary` dated September 23–30,
2026, at head `6932180bb`. It checks the surviving implementation rather than
treating every earlier fix as a new regression. Follow-up commits preserve the
branch history.

The prompt's relative time did not identify a reproducible batch to revert.
Canary's September 30 commit changes the release version; no canary commit falls
in the half-hour before the October 1 session. No matching revert was found.
No unidentified batch was blindly reverted.

## Follow-up fixes

- Restore the gapless-album crossfade guard alongside the podcast guard.
- Keep the USB DAC lifecycle coherent when incompatible playback options disable
  bit-perfect output; localize its fallback messages.
- Use digest-based analysis filenames instead of collision-prone 32-bit keys.
- Handle opaque Tidal tokens without assuming that a JWT payload exists.
- Keep Apple Music AAC fallback consistent with the requested quality tier.
- Invalidate cached Spotify profile data on account changes and disconnects.
- Preserve custom-font backup entries during archive validation.
- Keep fallback canvas sources available when Apple Music canvas lookup fails,
  and use the app-wide low-data default.
- Avoid SponsorBlock lookups for podcasts when attaching the player as well as
  during transitions.
- Restart Spotify search pagination when the filter changes; dispatch TikTok
  like-failure toasts on the main thread; localize BitChord glyph descriptions.
- Remove unreferenced Home, SponsorBlock and Deezer helpers and unused Apple
  Music player state without replacing ViewModels or playback resolution.
- Apply interface-style preferences atomically, preserving the previous player
  design and respecting a design selected manually afterwards.

## Appearance ports

The design basis is [4nx3b/ArchiveTune](https://github.com/4nx3b/ArchiveTune),
reviewed at `5e0c8e452`. Its common ancestor with this canary is `646829cfd`;
the divergent tree is not suitable for a wholesale merge.

| Feature | Decision |
| --- | --- |
| Scroll-hidden navigation with mini-player takeover (`76cd0ae6d`) | Reimplemented with the existing nested-scroll and player-sheet architecture. Optional in Navigation bar settings; tab-content padding stays static. |
| Accent-tinted navigation (`940473914`, `6c8639207`) | Adapted to existing containers, with contrasting content in light, dark and AMOLED schemes. |
| Bottom search chrome (`bf905241f`, `481b13579`) | Reimplemented as an optional Search-tab position. Existing search, provider picker, voice search and ViewModels are retained. Keyboard and player insets are combined rather than added twice. |
| Apple Music presentation | Existing player, library, headers, menus and tab bar stay connected to the experience wiring. An Appearance selector replaces the isolated switch. Source login and playback are not changed by a theme choice. |
| Material 3 Expressive counterpart | Default for unwritten preferences: purple seed, tonal containers, 28dp search shape, expressive controls, Solar search glyph and spring transitions. Existing custom palettes and explicit Apple Music selections are preserved. |
| Fork settings redesign (`e50e8a606`) | Excluded as requested. New controls use the existing settings pages and search anchors. |
| Fork glass recorder and render-pipeline rewrites | Not copied. The fork subsequently fixes NavHost freezes (`53ede6f0d`) and recursive recording crashes (`cbf7d806c`); these are not prerequisites for the selected ports. |
| Existing alternate players, canvas, lyrics and frosted/glass surfaces | Retained rather than imported again or replaced with the divergent versions. |
| New audio engines, equalizer processing and catalog/business-logic rewrites | Outside the clarified appearance-port scope. Existing source and playback invariants remain intact. |

Credit to 4nx3b for the navigation, tint and bottom-search design basis. Ports are
adaptations to this architecture, not copies of the settings or render pipeline.
Existing GPL-3.0 notices and asset attribution are retained.

## Verification and rollout

Required checks are `assembleGmsMobileUniversalDebug` and
`:app:testGmsMobileUniversalDebugUnitTest`. Interface-style regression coverage
checks fresh preferences, idempotent selection, restoration and manually changed
player designs. Runtime appearance evidence and publication status belong in the
delivery report, not an assumption based on successful compilation.

No database migration, new provider account, secret rotation or production
backfill is required. The existing Apple Music boolean remains the persisted
experience key. Users can switch back through Appearance; the previous player
style is retained. The Java version declaration makes the documented JDK 21
requirement discoverable by fresh workspaces.
