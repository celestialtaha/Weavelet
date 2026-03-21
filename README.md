# Weavelet 🎵

Weavelet is an offline-first music player for Wear OS smartwatches.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/celestialtaha/Weavelet)](https://github.com/celestialtaha/Weavelet/releases)
[![Android CI](https://github.com/celestialtaha/Weavelet/actions/workflows/ci.yml/badge.svg)](https://github.com/celestialtaha/Weavelet/actions/workflows/ci.yml)
[![GitHub issues](https://img.shields.io/github/issues/celestialtaha/Weavelet)](https://github.com/celestialtaha/Weavelet/issues)
[![GitHub forks](https://img.shields.io/github/forks/celestialtaha/Weavelet)](https://github.com/celestialtaha/Weavelet/network)
[![GitHub stars](https://img.shields.io/github/stars/celestialtaha/Weavelet)](https://github.com/celestialtaha/Weavelet/stargazers)

## Overview
Built for smartwatch-first usage, Weavelet focuses on quick local playback, responsive controls, and modern Wear-native UI patterns.

## Features
- Standalone Wear OS playback from local watch storage.
- Offline music library scanning and browsing.
- Playback controls: play/pause, next/previous, shuffle, repeat.
- Crown-based volume control with in-app indicator.
- Tile and Complication support for quick access.
- UI built with Compose for Wear and Material 3.

## Tech Stack
- Kotlin
- Jetpack Compose for Wear OS
- Wear Material 3
- Media3
- Horologist
- Coroutines
- Coil

## Screenshots
Screenshots are coming soon.

## Install (Users)
1. Open the [Releases page](https://github.com/celestialtaha/Weavelet/releases).
2. Download the latest APK asset.
3. Sideload it to your watch.

## Releases (Maintainers)
Releases are generated from tags matching `v*` (for example `v1.1.0`) via GitHub Actions.

Typical assets:
- `weavelet-debug.apk`
- `weavelet-release-signed.apk` (when signing secrets are configured)
- `weavelet-release-unsigned.apk` (fallback)
- `SHA256SUMS.txt`

Release flow:
1. Bump version in `app/build.gradle.kts`.
2. Push changes to `main`.
3. Tag and push:
```bash
git tag v1.1.0
git push origin v1.1.0
```
4. Workflow creates a GitHub Release and uploads APK assets.

## Development
```bash
git clone git@github.com:celestialtaha/Weavelet.git
cd Weavelet
./gradlew :app:assembleDebug
```

## Contributing
Contributions are welcome.

1. Fork the repository.
2. Create a feature or fix branch.
3. Commit your changes with clear messages.
4. Open a pull request.

For larger features, open an issue first to discuss scope.

## Roadmap
See [roadmap.md](roadmap.md).

## License
Licensed under Apache License 2.0. See [LICENSE](LICENSE).
