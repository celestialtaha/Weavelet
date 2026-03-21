# Weavelet

Offline music player for Wear OS smartwatches.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/celestialtaha/Weavelet)](https://github.com/celestialtaha/Weavelet/releases)
[![GitHub issues](https://img.shields.io/github/issues/celestialtaha/Weavelet)](https://github.com/celestialtaha/Weavelet/issues)
[![Android CI](https://github.com/celestialtaha/Weavelet/actions/workflows/ci.yml/badge.svg)](https://github.com/celestialtaha/Weavelet/actions/workflows/ci.yml)

## Overview
Weavelet is a standalone Wear OS music app focused on local/offline playback, fast browsing, and modern Wear-native UI.

## Key Features
- Standalone Wear OS experience.
- Offline playback from watch storage.
- Playback controls: play/pause, previous/next, shuffle, repeat, crown-based volume.
- Efficient library browsing with lazy loading.
- Wear integrations: Tiles and Complications.
- Material 3 + Compose UI.

## Tech Stack
- Kotlin
- Jetpack Compose (Wear)
- Material 3 (Wear)
- Media3
- Horologist
- Coroutines
- Coil

## Releases
GitHub Releases are published from version tags (`v*`) and include APK assets.

- Release page: <https://github.com/celestialtaha/Weavelet/releases>
- Typical assets:
  - `weavelet-debug.apk` (always generated; installable)
  - `weavelet-release-signed.apk` (generated when signing secrets are configured)
  - `weavelet-release-unsigned.apk` (fallback if signing is not configured)
  - `SHA256SUMS.txt` (checksums)

To generate signed release APKs in GitHub Actions, configure these repository secrets:
- `ANDROID_SIGNING_KEY_BASE64`
- `ANDROID_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

### Create a release
1. Bump app version in `app/build.gradle.kts`.
2. Commit and push.
3. Create and push a tag, for example:
```bash
git tag v1.1.0
git push origin v1.1.0
```
4. GitHub Actions builds APKs and creates the release automatically.

## Development
```bash
git clone git@github.com:celestialtaha/Weavelet.git
cd Weavelet
./gradlew :app:assembleDebug
```

## Roadmap
See [roadmap.md](roadmap.md).

## Contributing
Contributions are welcome. Please open an issue first for larger feature work.

## License
This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
