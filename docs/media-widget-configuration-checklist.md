# Media widget configuration checklist

Goal: make the Media Controls widget polished and robust from a 2x2 compact widget to a full-page widget, with opaque Material-style colors and every saved setting reflected in the rendered RemoteViews.

## Status

- [x] Inventory current widget settings and saved DataStore keys.
- [x] Confirm Material 3 dependencies and expressive opt-in are already present.
- [x] Render each app-widget id with its own launcher size options.
- [x] Use the computed play color as the widget background source color.
- [x] Derive the play button from the background: darker on light backgrounds, lighter on dark backgrounds.
- [x] Remove translucent/glass opacity from widget surfaces.
- [x] Replace hard visual section separators with a soft opaque gradient.
- [x] Support useful layouts from 2x2 to full page.
- [x] Add useful extra actions/details when there is enough empty space.
- [x] Verify all settings are reachable in configuration UI.
- [x] Run JVM/unit verification.
- [x] Run APK/build verification.
- [x] Build and publish a signed release instead of a debug APK.
- [x] Verify the in-app updater against a real published Pampa Store release.
- [x] Automated layout matrix coverage for 5 themes x 3 artwork sizes x 32 boolean masks x 6 size profiles.
- [x] Automated color coverage on device for all themes across representative album accents.
- [x] Serialize and conflate notification/session bursts before rendering.
- [x] Reject stale asynchronous reads with a latest-generation gate.
- [x] Skip identical visual states before bitmap creation and `updateAppWidget`.
- [x] Keep the last media-app launch target even when last-song display is disabled.
- [x] Remove the ambiguous root-tap fallback to Pampa/settings when no media target exists.
- [x] Keep cache metadata and artwork track-keyed, atomic, and throttled.
- [x] Replace background/artwork atomically and reserve animation for lightweight play/pause feedback; One UI briefly exposed the wallpaper when bitmap-heavy `ViewFlipper` layers animated.
- [ ] Device/launcher resize QA.
- [x] Manual Spotify close/reopen QA on a pinned launcher widget.

## Verification log

- [x] `./gradlew.bat :app:compileDebugKotlin` passed.
- [x] `./gradlew.bat testDebugUnitTest` passed.
- [x] `./gradlew.bat :app:assembleDebug` passed.
- [x] `./gradlew.bat :app:testReleaseUnitTest :app:assembleRelease` passed for release `0.2.8`.
- [x] `./gradlew.bat :app:testDebugUnitTest --tests com.pampa.widgets.widget.media.MediaWidgetLayoutSpecTest` passed.
- [x] `MediaWidgetLayoutSpecTest` covers 2,880 core layout combinations and asserts tappable controls, valid typography, collapsed mini layout, full-page emphasis, and useful extra rows only when there is enough height.
- [x] The matrix test caught an over-eager extra-actions threshold at 320x260; fixed so artwork-size settings continue to affect usable artwork in constrained layouts.
- [x] `./gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pampa.widgets.widget.media.MediaWidgetColorInstrumentedTest"` passed on SM-S931B / Android 16.
- [x] `MediaWidgetColorInstrumentedTest` proves background/play alpha is 255 and the play button becomes lighter on dark backgrounds or darker on light backgrounds.
- [x] `./gradlew.bat connectedDebugAndroidTest` passed on SM-S931B / Android 16.
- [x] 2026-08-22: `./gradlew.bat :app:testDebugUnitTest` passed with update-generation, render-signature, seek/state invalidation, launch-target, identity, and 2,880-layout-matrix coverage.
- [x] 2026-08-22: `./gradlew.bat connectedDebugAndroidTest` passed 13/13 tests on SM-S931B / Android 16 (API 36), including sequential full-update coherence, cache/artwork regressions, checkpoint throttling, static feedback-off rendering, and the remembered Spotify launch-target resolver.
- [x] 2026-08-22: `./gradlew.bat :app:lintDebug :app:testReleaseUnitTest :app:assembleRelease` passed; lint has 0 errors and the release APK verifies with the Pampa v2 signing certificate.
- [x] 2026-08-22: repeated playing snapshots with the same playback clock anchor produce the same render signature, preventing redundant launcher updates.
- [x] 2026-08-22: an unknown media launch target resolves to a no-op broadcast rather than Pampa or widget settings.
- [x] 2026-08-22: the release widget remained pinned as app-widget 435 in One UI Home at 315.43 x 233.90 dp after an in-place signed APK update.
- [x] 2026-08-22: 20 explicit refresh broadcasts at 80 ms intervals left app-widget 435 on the same launcher `RemoteViews` instance, proving that identical snapshots were rejected before `updateAppWidget`.
- [x] 2026-08-22: after `am force-stop com.spotify.music`, Spotify had no process or active media session; a refresh kept the cached song visible and retained the same launcher `RemoteViews` instance.
- [x] 2026-08-22: Android package visibility explicitly covers Spotify and every supported media package, so a remembered installed player resolves to its launcher intent; an unknown target remains a no-op.
- [x] 2026-08-22 final gate: `./gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease` passed in 1m04s with 24 debug unit tests, 24 release unit tests, and 13 device tests with 0 failures/errors/skips.
- [x] 2026-08-22 anti-flash gate: the same full Gradle gate passed again in 1m23s after heavy-layer animation was disabled; 24 debug unit tests, 24 release unit tests, 13 device tests, lint, and release assembly all passed.
- [x] 2026-08-22 controlled One UI video: next-track was tapped exactly 2s after recording began; the widget responded within the following frames and switched from `Hurt So Good` to `Lucky` atomically. Across all 98 frames, the clean background patch stayed spatially uniform (per-channel spatial deviation 1.79 before and 2.14 after) with no wallpaper-exposed frame.
- [x] 2026-08-22 root-tap QA: with Spotify force-stopped and no active media session, tapping the pinned widget root launched `com.spotify.music/.MainActivity`; Pampa and widget settings did not open.
- [x] `apksigner verify --verbose --print-certs app-release.apk` passed with APK Signature Scheme v2 and certificate `CN=Pampa, O=PampaStore, C=IT`.
- [x] Pampa Store publisher dry-run passed for `stable-pampa-widgets-v0.2.8`, APK asset `pampa-widgets-0.2.8.apk`, SHA-256 `00e825b28786bf81ae827ec58fd751335f8c9022b667964737e9e109381a9009`.
- [x] Pampa Store publish passed: GitHub release `https://github.com/Casual76/Pampa-widgets/releases/tag/stable-pampa-widgets-v0.2.8`; app manifest and store index updated.
- [x] Updater QA installed published `0.2.7` (`versionCode=9`) on SM-S931B user 0, launched the app, and confirmed the automatic banner `Aggiornamento 0.2.8 disponibile`.
- [x] Updater QA tapped in-app `Installa`; the flow downloaded/verified the APK and opened Android's `Aggiornare l'app?` confirmation for Pampa Widgets.
- [x] Updater QA completed the system install after the Play Protect prompt; `dumpsys package com.pampa.widgets --user 0` confirmed `versionCode=10` and `versionName=0.2.8`.
- [x] Updater QA reopened the updated app; the Store banner was gone and Settings showed `App aggiornata` / `Versione installata: 0.2.8` with install disabled.
- [x] `./gradlew.bat :app:installDebug` installed on SM-S931B.
- [x] Configuration Activity launched on device with `adb shell am start`.
- [x] UI tree confirmed theme chips: Vetro, Adattivo, Album, Chiaro, Scuro.
- [x] UI tree confirmed artwork chips: Compatta, Bilanciata, Grande.
- [x] UI tree confirmed behavior switches: Sorgente, Artista, Ultima canzone, Comandi immediati, Feedback leggero.
- [ ] Manual launcher resize QA: pin widget on home and inspect S0-S5. The connected device exposes no `cmd appwidget` shell implementation. The real widget is inside a One UI widget stack; long-press exposes `Modifica gruppo` and the group editor, but no accessibility-addressable resize handles, so no destructive home-layout mutation was attempted.

## Complete configuration space

The complete setting space is the cross-product of:

- Theme: SamsungGlass, AdaptiveGlass, LightGlass, DarkGlass, AlbumColor.
- Artwork size: Compact, Balanced, Large.
- Boolean mask: ShowSource, ShowArtist, KeepLastSong, InstantControls, AnimatedFeedback.
- Widget size profile: S0 2x2 mini, S1 compact 2x2/2x3, S2 default 3x2, S3 wide, S4 tall, S5 full page.
- Media state: active playing, active paused, no active session with cached song, no active session without cache, permission required.
- Artwork state: real artwork, missing artwork.
- Capability state: previous enabled/disabled, next enabled/disabled, play enabled/disabled.

Total setting-only combinations: 5 themes x 3 artwork sizes x 32 boolean masks = 480.
Total core visual combinations before media/capability states: 480 x 6 size profiles = 2,880.

## Boolean masks to test visually for each theme/artwork pair

Legend: Source, Artist, KeepLast, Instant, Feedback. These remain manual visual QA items; the automated layout matrix already covers all masks for layout invariants.

- [ ] 00000: all off
- [ ] 00001: Feedback
- [ ] 00010: Instant
- [ ] 00011: Instant, Feedback
- [ ] 00100: KeepLast
- [ ] 00101: KeepLast, Feedback
- [ ] 00110: KeepLast, Instant
- [ ] 00111: KeepLast, Instant, Feedback
- [ ] 01000: Artist
- [ ] 01001: Artist, Feedback
- [ ] 01010: Artist, Instant
- [ ] 01011: Artist, Instant, Feedback
- [ ] 01100: Artist, KeepLast
- [ ] 01101: Artist, KeepLast, Feedback
- [ ] 01110: Artist, KeepLast, Instant
- [ ] 01111: Artist, KeepLast, Instant, Feedback
- [ ] 10000: Source
- [ ] 10001: Source, Feedback
- [ ] 10010: Source, Instant
- [ ] 10011: Source, Instant, Feedback
- [ ] 10100: Source, KeepLast
- [ ] 10101: Source, KeepLast, Feedback
- [ ] 10110: Source, KeepLast, Instant
- [ ] 10111: Source, KeepLast, Instant, Feedback
- [ ] 11000: Source, Artist
- [ ] 11001: Source, Artist, Feedback
- [ ] 11010: Source, Artist, Instant
- [ ] 11011: Source, Artist, Instant, Feedback
- [ ] 11100: Source, Artist, KeepLast
- [ ] 11101: Source, Artist, KeepLast, Feedback
- [ ] 11110: Source, Artist, KeepLast, Instant
- [ ] 11111: Source, Artist, KeepLast, Instant, Feedback

## Size profiles

- [x] S0 2x2 mini: about 110-170dp wide, 110-150dp tall. Automated layout invariants pass; manual launcher visual QA still needed.
- [x] S1 compact: about 170-240dp wide or 150-180dp tall. Automated layout invariants pass; manual launcher visual QA still needed.
- [x] S2 default 3x2: about 250-330dp wide, 170-230dp tall. Automated layout invariants pass; manual launcher visual QA still needed.
- [x] S3 wide: 330-520dp wide, 170-260dp tall. Automated layout invariants pass; manual launcher visual QA still needed.
- [x] S4 tall: 250-420dp wide, 260-420dp tall. Automated layout invariants pass; manual launcher visual QA still needed.
- [x] S5 full page: 420dp+ wide or 420dp+ tall. Automated layout invariants pass; manual launcher visual QA still needed.

## Theme/artwork matrix for visual QA

The automated layout matrix covers every theme/artwork pair; these remain open for visual color/style inspection on a real launcher.

- [ ] SamsungGlass x Compact
- [ ] SamsungGlass x Balanced
- [ ] SamsungGlass x Large
- [ ] AdaptiveGlass x Compact
- [ ] AdaptiveGlass x Balanced
- [ ] AdaptiveGlass x Large
- [ ] LightGlass x Compact
- [ ] LightGlass x Balanced
- [ ] LightGlass x Large
- [ ] DarkGlass x Compact
- [ ] DarkGlass x Balanced
- [ ] DarkGlass x Large
- [ ] AlbumColor x Compact
- [ ] AlbumColor x Balanced
- [ ] AlbumColor x Large

## Media state matrix for visual QA

- [ ] Active playing with artwork.
- [ ] Active playing without artwork.
- [ ] Active paused with artwork.
- [ ] Active paused without artwork.
- [ ] Permission required.
- [ ] No active session, keep-last-song on, cached song available.
- [ ] No active session, keep-last-song off.
- [ ] Previous disabled, next enabled.
- [ ] Previous enabled, next disabled.
- [ ] Previous disabled, next disabled.
- [ ] Play/pause disabled.

## Lifecycle and update regression matrix

- [x] Duplicate playing-state callbacks collapse to one visual signature in JVM tests.
- [x] A real seek invalidates the signature and triggers a render.
- [x] Play/pause state changes invalidate the signature and update the confirmed glyph.
- [x] Late asynchronous reads cannot commit after a newer generation.
- [x] Sequential red-track, loading-artwork, and blue-artwork full updates remain internally coherent after both `reapply` and launcher-style reinflation.
- [x] Last-target cache is readable without decoding or exposing hidden last-song metadata.
- [x] Identical playback checkpoints do not rewrite metadata or artwork on device.
- [x] Feedback disabled keeps play/pause on a static non-animated layer on device.
- [x] A pinned release widget survived 20 no-op refresh broadcasts without receiving a replacement `RemoteViews` payload.
- [x] With Spotify force-stopped, the pinned widget kept the last-song presentation stable and did not open Pampa/settings during the refresh path.
- [x] With Spotify force-stopped, tapping the pinned root reopened Spotify instead of Pampa/settings.
- [x] With Feedback enabled, a real next-track transition kept the old card fully opaque until the new artwork, palette, text, and confirmed glyph were ready, then replaced the frame atomically with no whole-card flash.
- [ ] Pin the debug widget, play Spotify, and observe at least 20 metadata/playback callbacks with no whole-card flash.
- [x] Force-stop Spotify, tap artwork/root, and confirm Spotify reopens instead of Pampa/settings.
- [ ] Disable KeepLastSong, force-stop Spotify, and repeat the root-tap target check.
- [ ] Disable Feedback, change track, and confirm artwork/palette update immediately without crossfade.
- [x] Enable Feedback, change track, and confirm artwork/palette replace atomically while lightweight control feedback remains animated.
- [ ] Toggle play/pause repeatedly and confirm one glyph morph per confirmed state, with no optimistic double-flip.
- [ ] Resize S0 through S5 and confirm no blank frame during background bitmap replacement.

## Notes while implementing

- If a setting is visually impossible in S0/S1 because of physical widget space, the setting should gracefully collapse instead of overflowing.
- AdaptiveGlass exists in the enum and DataStore; the configuration UI must expose it.
- Disabled controls should not rely on view opacity; use opaque muted colors instead.
- The full-page layout should spend extra space on larger artwork, stronger typography, progress/time context, and useful actions such as refresh/open media app.
- RemoteViews are launcher-sensitive. JVM/build verification can prove compilation and code paths, but final S0-S5 resize behavior still needs device/launcher QA.
