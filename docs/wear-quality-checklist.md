# Wear Quality Checklist

Use this checklist before each tagged release. It focuses on the Wear OS app flows that are easiest to regress on small round screens.

## Navigation and State

- Start on Home, open Library, scroll, search, switch to Artists, open an artist, then play a track.
- Swipe or press Back from Player and confirm Library returns to the same context.
- Rotate/recreate the activity while on Library, Player, Settings, and About; confirm the active screen is retained.
- Confirm hardware/system Back and swipe-dismiss produce the same destination.
- Launch from the Tile while a track exists; confirm Player opens directly.

## Screen Fit

- Verify 192dp, 225dp, and larger round profiles.
- Confirm headers, edge buttons, progress indicators, and bottom actions do not clip.
- Confirm list rows and buttons remain readable with long track, artist, and album names.
- Confirm search clear, transport controls, settings rows, and edge buttons have comfortable touch targets.

## Playback

- Tap a track from the main list and from artist detail; playback should start immediately.
- Verify play/pause, next, previous, shuffle, repeat all, and repeat one.
- Background the app during playback, return, and confirm current track/progress state is still correct.
- Let one track transition to the next and confirm Library/Tile/Complication state updates.

## Library

- Test empty library, populated library, and permission-denied flow.
- Test refresh after adding/removing audio files.
- Test pagination by scrolling near the end of a larger library.
- Test search for title, artist, and album terms, including no-result states.

## Tile and Complication

- Add the Tile and confirm its preview/state is meaningful before playback.
- Start playback, pause, skip, and confirm Tile text/state refreshes.
- Enable and disable the complication setting; confirm the provider reflects current playback state only when enabled.

## Release Verification

Run these before tagging:

```bash
./gradlew --no-daemon --max-workers=2 :app:assembleDebug
./gradlew --no-daemon --max-workers=1 :app:assembleRelease
```

After the tag workflow completes, verify:

- Debug APK and release APK assets exist.
- `SHA256SUMS.txt` verifies all uploaded APKs.
- The GitHub Release notes are generated from the expected commits.
