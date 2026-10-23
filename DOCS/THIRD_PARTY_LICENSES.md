# Third-party dependency inventory — 0.4.1

All runtime dependencies are Apache License 2.0 unless noted. This is an inventory
of what the build declares; the authoritative license texts ship with each artifact.

| Dependency | Version | License | Used by |
|---|---|---|---|
| AndroidX Media3 (exoplayer, exoplayer-hls, exoplayer-dash, ui, common) | 1.6.1 | Apache-2.0 | player-android, app-android, subtitle-android |
| AndroidX Compose UI / Foundation / Material3 | 1.7.8 / 1.7.8 / 1.3.1 | Apache-2.0 | app-android, player-ui-android |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | app-android |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 | app-android |
| AndroidX Lifecycle Runtime KTX | 2.8.7 | Apache-2.0 | app-android |
| Kotlin stdlib / Gradle plugin | 2.0.21 | Apache-2.0 | all modules |
| Android Gradle Plugin | 8.9.2 | Apache-2.0 (build-time) | build |
| JUnit | 4.13.2 | EPL-1.0 (test-only) | tests |

Not bundled: FFmpeg or any native codec library (see FFMPEG_DECISION.md), fonts
(system fonts only in 0.4.0), analytics, or networking libraries.

The project itself has no LICENSE file yet — the project license is the owner's
decision and is intentionally left open in this delivery.
