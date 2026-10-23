# Third-party dependency inventory — 0.5.0

All runtime dependencies are Apache License 2.0 unless noted. This is an inventory
of what the build declares; the authoritative license texts ship with each artifact.

| Dependency | Version | License | Used by |
|---|---|---|---|
| AndroidX Media3 (exoplayer, exoplayer-hls, exoplayer-dash, ui, common) | 1.6.1 | Apache-2.0 | player-android, app-android, subtitle-android |
| AndroidX Compose UI / Foundation / Material3 | 1.7.8 / 1.7.8 / 1.3.1 | Apache-2.0 | app-android, player-ui-android |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | app-android |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 | app-android |
| AndroidX Lifecycle Runtime KTX | 2.8.7 | Apache-2.0 | app-android |
| libmpv Android wrapper (`dev.jdtech.mpv:libmpv`) | 0.4.1 | MIT (wrapper) | player-mpv |
| — bundled mpv | 0.39.0 | GPLv2+ | player-mpv (native) |
| — bundled FFmpeg | 7.1 | **GPLv3** (built `--enable-gpl --enable-version3`) | player-mpv (native) |
| — bundled dav1d / libass / libplacebo | 1.4.3 / 0.17.3 / 7.349.0 | BSD-2 / ISC / LGPL-2.1+ | player-mpv (native) |
| Kotlin stdlib / Gradle plugin | 2.0.21 | Apache-2.0 | all modules |
| Android Gradle Plugin | 8.9.2 | Apache-2.0 (build-time) | build |
| JUnit | 4.13.2 | EPL-1.0 (test-only) | tests |

**GPL consequence (since 0.5.0):** because the software engine bundles a GPLv3
FFmpeg build, a distributed APK that includes `:player-mpv` carries GPLv3
obligations for the combined work (source availability for the corresponding
source, license notices). The project's own source is published in a public
repository, which is the mechanism relied on here; see DOCS/SOFTWARE_ENGINE.md.
The libmpv AAR ships no LICENSE file of its own.

Not bundled: fonts (system fonts only), analytics, or networking libraries
beyond the platform stack.

The project itself has no LICENSE file yet — the project license is the owner's
decision and is intentionally left open in this delivery. The GPL note above
applies to binary distribution regardless of that choice.
