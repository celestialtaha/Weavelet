# Weavelet Roadmap 2026

Last updated: March 2026  
Product: Offline-first Wear OS music player (standalone watch experience)

Status legend:
- `DONE` Completed and shipped
- `ACTIVE` In progress / hardening
- `NEXT` Planned for the next cycle
- `LATER` Backlog

---

## 1) Product Goals (2026)

### User goals
- Start playback from watch in under 3 taps.
- Keep playback stable during long sessions.
- Make core controls usable on small round screens without clipping.

### Engineering goals
- Keep release pipeline deterministic and tag-driven.
- Improve reliability and performance before adding heavy new features.
- Grow test coverage for playback, library paging, and state transitions.

### Success metrics
- Crash-free sessions (player screens) > 99.5%.
- Release lead time (tag to assets available) < 15 minutes.
- Median time-to-first-play after app open < 3 seconds (warm library).

---

## 2) Current Delivery Snapshot

## 2.1 Core playback & library
- `DONE` Play/pause/next/previous controls
- `DONE` Shuffle and repeat (off/all/one)
- `DONE` Crown-based volume control with in-app HUD
- `DONE` Library scan + pagination + lazy album art loading + metadata caching
- `DONE` Sorting via settings
- `ACTIVE` Search (title/artist/album implemented; advanced filters pending)
- `LATER` Queue management and playlists

## 2.2 Wear UI/UX
- `DONE` Material 3 migration for Wear UI
- `DONE` Adaptive round-screen player redesign
- `DONE` Full-screen art backdrop + edge progress
- `DONE` Home spacing improvements
- `DONE` About page redesign + runtime version display
- `NEXT` Dedicated seek mode (rotary seek vs volume mode)

## 2.3 Stability/performance/refactor
- `DONE` MediaController lifecycle hardening
- `DONE` Playback state sync and progress tracking improvements
- `DONE` Recomposition and media decoding optimization pass
- `DONE` Modularized app structure (`core:data`, `core:player`, `core:ui`)
- `DONE` Gradle 9 / AGP 9 migration compatibility
- `ACTIVE` Additional battery-focused profiling and tuning

## 2.4 OSS/release engineering
- `DONE` Apache-2.0 licensing
- `DONE` CI workflow on push/PR
- `DONE` Tag-based APK release workflow + checksums
- `DONE` Optional signing path via repo secrets
- `ACTIVE` Workflow hardening for changing GitHub Actions runtime baseline
- `NEXT` Add issue/PR templates + `CONTRIBUTING.md`

## 2.5 Play Store and Wear quality compliance
- `ACTIVE` Keep target SDK policy aligned with current Wear Play requirements
- `NEXT` Add explicit QA checklist pass for Wear app quality requirements (touch targets, font scaling, edge clipping, swipe-to-dismiss)
- `NEXT` Add release checklist for Play listing assets (Wear screenshots, feature mentions, tile/complication mentions when applicable)
- `LATER` Automate quality gate checks where possible (lint + screenshot sanity + manifest checks)

---

## 3) 2026 Trend Adoption Matrix (Wear OS + design)

| Trend / Direction | Why it matters | Weavelet action | Status |
|---|---|---|---|
| Round-first, glanceable Wear UI patterns | Better readability and faster wrist interactions | Keep compact controls, edge-aware indicators, high-contrast overlays | `DONE` + `ACTIVE` |
| Compose for Wear + Material 3 component consistency | Faster iteration + platform-consistent UX | Continue replacing custom primitives where platform components fit | `ACTIVE` |
| Richer motion and depth (including glass-like surfaces) | Modern look, clearer focus hierarchy | Add an **experimental** “glass” visual mode for non-critical overlays only, gated by contrast checks | `NEXT` |
| Release automation for open-source apps | Better trust and easier installs for users | Keep tag-driven releases with APK assets + checksums | `DONE` |
| Reliability-first media apps (Media3 lifecycle rigor) | Prevent playback dropouts and state drift | Expand regression tests around controller/session lifecycle | `NEXT` |
| Voice/assistant-driven quick actions on wearables | Faster no-touch interactions | Evaluate App Actions for “play artist/album” entry points | `LATER` |

Notes:
- “Glass” styling should remain optional and accessibility-safe (contrast, blur cost, and battery impact validated on device).

---

## 4) Feature Roadmap (Prioritized)

## P0: Reliability and release confidence
1. `ACTIVE` Finalize release workflow hardening (including signing edge cases).
2. `NEXT` Add smoke tests for playback lifecycle and library loading.
3. `NEXT` Add failure telemetry hooks/logging for release troubleshooting.
4. `NEXT` Add formal release checklist for Wear app quality + Play listing compliance.

## P1: Core playback upgrades
1. `NEXT` Add 15/30s seek controls.
2. `NEXT` Add sleep timer.
3. `LATER` Add equalizer/preset support (device capability dependent).

## P1: Library and queue
1. `NEXT` Improve search (tokenization + better matching).
2. `NEXT` Add queue screen with clear/reorder basics.
3. `LATER` Playlist MVP (create/rename/delete + add/remove track).

## P2: Wear-native enhancements
1. `NEXT` Rotary mode switch (volume vs seek) with clear UI affordance.
2. `LATER` Expand tile actions (resume, quick controls, recent tracks).
3. `LATER` More complication variants and richer state text.

## P2: Visual polish track
1. `NEXT` Build optional “glass-lite” UI experiment for selected surfaces.
2. `NEXT` Validate contrast, battery, and readability on small/large round watches.
3. `LATER` Theme tokens for deeper user customization.

---

## 5) Delivery Plan (2026)

### Q2 2026
- Release pipeline stability + signing reliability.
- Seek shortcuts and sleep timer.
- Search quality improvements.

### Q3 2026
- Queue management MVP.
- Playback regression tests + CI quality gates.
- Tile/complication incremental upgrades.

### Q4 2026
- Playlist MVP.
- Optional glass-style visual mode (if accessibility/perf targets pass).
- Broader accessibility and customization pass.

---

## 6) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| GitHub Actions runtime/tooling changes | Release failures | Keep workflows pinned, monitor deprecations, test tag flow monthly |
| Wear device variability (rotary deltas, screen sizes) | UI/interaction inconsistency | Keep adaptive thresholds/layouts and test on multiple watch profiles |
| Visual trend overreach (glass effects) | Readability/battery regressions | Gate behind accessibility/perf criteria and keep optional |
| Feature creep (playlists + queue + advanced audio together) | Delayed releases | Keep strict P0/P1/P2 sequencing and small deliverables |

---

## 7) Definition of Done (for roadmap items)

An item is considered `DONE` only when:
1. Implemented in app code.
2. Verified on-device (or emulator where appropriate).
3. Build passes in CI.
4. User-facing behavior is documented/releasable.
