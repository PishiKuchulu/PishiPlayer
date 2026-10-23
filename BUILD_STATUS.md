# Delivery status — 0.5.0

Verification performed in the build sandbox for this release. The sandbox cannot run
Gradle (its daemon dies immediately — a documented environment limit, reproduced
2026-10-23 with the official Gradle 8.11.1 distribution: "Could not receive a message
from the daemon"), and plain-Java HTTPS is broken by the sandbox egress proxy, so the
full AGP build is delegated to the included GitHub Actions workflow. Everything that
could be executed locally was executed; labels follow the rule that a source
inspection is not compilation and compilation is not device testing.

## Results

| Check | Result | Evidence |
|---|---|---|
| JVM modules compile (player-api, subtitle-core, playback-storage, tv-input) | PASS | Kotlin 2.0.21 compiler, exit 0 |
| JVM unit tests — 54 tests | PASS | JUnit 4.13.2 run: `OK (54 tests)`. Suites: SeekPolicy (2), EngineFallbackPolicy (9, added in 0.5.0), SubtitleDecoder (5), SubtitleParser (5, incl. the fixed ASS fixture), SubtitleText (3), SubtitleCorpus (9), SubtitleMarkup (10, added in 0.4.1), Bookmark (1), FileBookmarkStore (4), RemoteActionMapper (6) |
| device-capabilities compiles | PASS | Compiled against the official API 35 android.jar after the CodecCapabilities fix |
| player-android compiles | PASS | Compiled against real published Media3 1.6.1 artifacts |
| player-mpv compiles (new in 0.5.0) | PASS | Compiled against the API 35 android.jar and the real `dev.jdtech.mpv:libmpv:0.4.1` AAR classes.jar (downloaded from Maven Central, API surface verified with javap before use) |
| subtitle-android, player-ui-android compile | PASS | Compiled against real published artifacts |
| app-android compiles (with Compose compiler plugin) | PASS | Kotlin embeddable compiler 2.0.21 + Compose plugin 2.0.21, real Compose 1.7.8 / Material3 1.3.1 / activity-compose 1.10.1 artifacts, API 35 android.jar. One benign warning (@OptIn on UnstableApi, pre-existing pattern) |
| Gradle wrapper present and genuine | PASS (files) | gradle-wrapper.jar extracted byte-for-byte from the official Gradle 8.11.1 distribution; gradlew/gradlew.bat from the official gradle/gradle v8.11.1 tag; properties already pinned 8.11.1 |
| `./gradlew` execution in sandbox | BLOCKED | Sandbox-only: wrapper download blocked by broken Java HTTPS through the egress proxy; Gradle daemon dies in this environment. CI runs the wrapper on a normal runner |
| Full AGP build (`assembleDebug`), lint | PASS (CI) | 0.5.0: GitHub Actions run 39951854126 on commit c759c5b (2026-10-23) — every step green, including assembleDebug with the libmpv AAR resolved from Maven Central, the minSdk-26 manifest merge, and ABI-split packaging. Artifacts: `universalplayer-debug-apk` (125,261,192 B — the bundle of all split APKs plus the universal APK, which carries every ABI's natives) and `lint-report` (32,941 B). First 0.5.0 attempt (run 39951112761) failed at `:app-android:compileDebugKotlin`: libmpv had been declared `implementation` in player-mpv while `MPVLib.EventObserver` is a public supertype of the engine — fixed by switching the scope to `api`. (0.4.1's green run: 39945170242.) Still not run in this sandbox (Gradle cannot execute here) |
| Physical-device playback / TV remote / Persian glyph rendering | NOT TESTED | No device available in this environment. The software engine adds its own open checklist (DOCS/SOFTWARE_ENGINE.md): content:// via fdclose, surface timing on engine switch, 1080p HEVC/AV1 software smoothness on A53-class TVs |

## What changed in 0.5.0 (summary; details in CHANGELOG.md and DOCS/SOFTWARE_ENGINE.md)

- Software engine added (`:player-mpv`, libmpv 0.4.1 = mpv 0.39.0 + FFmpeg 7.1 +
  dav1d): forced software decoding for files the hardware cannot decode — the
  Android TV use case Farzad set for the project. Routing policy is pure and
  unit-tested (9 tests); the app falls back once per file on decoder-class
  errors, resuming at the reached position, and offers Auto/Hardware/Software
  in an Engine dialog.
- minSdk raised 23 → 26 (libmpv AAR floor); ABI splits enabled.
- Licensing: the bundled FFmpeg is GPLv3 — binary distribution obligations
  recorded in DOCS/THIRD_PARTY_LICENSES.md.
- Sandbox verification: all 10 modules type-check (player-mpv against the
  real AAR classes.jar; the app with the embeddable compiler + Compose plugin
  pair), 54/54 JVM tests pass. AGP build verification for this tree rests on
  CI, as before; device verification is still outstanding.

## What changed in 0.4.0 (summary; details in CHANGELOG.md and PROGRESS.md)

- Both 0.3.0 build breakers fixed at root cause (CodecCapabilities API misuse;
  malformed `ui-viewbinding` dependency coordinate removed).
- The failing shipped test was a fixture bug (double backslash inside a Kotlin raw
  string); the fixture was fixed, the parser was not distorted. Suite went 16 → 35 tests.
- Dead modules eliminated by wiring, not deletion: subtitle-core now decodes/parses
  external subtitles in the app; RemoteActionMapper is the single key-routing path;
  DecoderCapabilityScanner powers the Device dialog; FileBookmarkStore persists
  resume points; PlaybackControlState drives the control bar; subtitle-android's
  rendering plan gates the settings UI and is now an app dependency.
- TV input defect fixed: DPAD keys are no longer consumed globally; focused controls
  receive them. Dedicated Play/Pause keys are distinct actions. Seek steps are bounded.
- Engine: real ERROR state with code+message, decoder name/decode-path reporting via
  AnalyticsListener, decoder fallback enabled, subtitles-off support, image/metadata
  track groups no longer mislabeled as video, track polling removed from the UI loop.
- Manifest: launcher icon + TV banner added, landscape lock removed (rotation handled
  via configChanges), INTERNET permission removed (local SAF playback only).

## Known remaining limits

See README "Important limits". Headlines: no FFmpeg/software-decode integration, no
device verification yet, overlay renderer does not reproduce ASS styling/positioning.
(0.4.1 closed the SRT-tag and WebVTT entity/timestamp-tag items.)
