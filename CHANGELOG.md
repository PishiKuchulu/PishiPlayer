# Changelog

## 0.4.1 — 2026-10-23

Subtitle markup polish in subtitle-core (test-first: 7 of the 10 new tests failed
against the 0.4.0 parser; the full suite is now 45/45).

### Added

- SRT cue text: inline formatting tags (`<i>`, `<b>`, `<font …>`, `<u>`, …) are
  stripped. A tag must start with a letter after `<`/`</`, so plain text like
  `5 < 6` or `I <3 you` is preserved verbatim (unit-tested).
- SRT + WebVTT: HTML entities are decoded — named (`&amp;`, `&lt;`, `&gt;`,
  `&quot;`, `&apos;`, `&nbsp;`, `&lrm;`, `&rlm;`), decimal (`&#72;`), and hex
  (`&#x645;`). Unknown entities are left untouched. Tag stripping runs before
  entity decoding, so an escaped tag (`&lt;i&gt;`) correctly becomes literal
  `<i>` text.
- WebVTT: timestamp tags (`<00:00:01.500>`) are stripped; tag coverage is now
  generic (any alphabetic tag incl. `ruby`/`rt`), replacing the old fixed list.
- `<br>` in cue text becomes a space instead of vanishing.

### Notes

- ASS/SSA text is intentionally unchanged by this cleanup (no HTML entities in
  ASS; override blocks keep their own handling) — covered by a regression test.
- No API changes; no app-code changes. Verification: subtitle-core compiled with
  Kotlin 2.0.21 and the whole JVM suite (including the Android-dependent mapper
  tests) executed — 45/45 PASS. AGP/device status unchanged from 0.4.0.

## 0.4.0 — 2026-10-23

Completion pass over the 0.3.0 foundation, driven by an independent line-by-line
audit of the 0.3.0 archive (2026-10-23). Verification details: `BUILD_STATUS.md`.

### Fixed (0.3.0 defects)

- `device-capabilities` did not compile: `DecoderCapabilityScanner` called
  `MediaCodecInfo.isFeatureSupported` / `getMaxSupportedInstances`, which do not
  exist on that class. Both queries now go through `getCapabilitiesForType(mime)`
  (`MediaCodecInfo.CodecCapabilities`), with a guard against devices that throw
  for unsupported types.
- Removed the malformed dependency `androidx.compose.ui:ui:ui-viewbinding:1.7.8`
  from `app-android` (parsed as version "ui-viewbinding", which does not exist;
  the artifact was also unused).
- Fixed the failing shipped test `SubtitleParserTest.parsesAssTextAndRetainsStyleAndAlignmentMetadata`:
  the fixture wrote `\\N` inside a Kotlin raw string; real ASS files use `\N`.
  Fixture corrected; parser verified correct against real files and left unchanged.
- TV remote routing: the root key handler no longer consumes DPAD_CENTER/ENTER and
  DPAD_LEFT/RIGHT globally (focused buttons could never be activated, horizontal
  focus movement and slider adjustment were dead). Keys now route by focus; the
  video surface handles its own keys through `RemoteActionMapper`.
- `RemoteActionMapper`: dedicated MEDIA_PLAY and MEDIA_PAUSE keys are distinct
  Play/Pause actions (were both TogglePlayback); seek steps are bounded — first
  press 10s, each repeat one 5s interval step (was `(repeatCount + 1) × 10s`,
  unbounded). MEDIA_NEXT/PREVIOUS (±30s) and INFO added.
- `BackHandler` (imported but unused in 0.3.0) is now implemented: Back closes
  dialogs/panels first, then exits.
- Track groups of type image/metadata are no longer mislabeled as VIDEO.

### Added

- Gradle wrapper (Gradle 8.11.1): wrapper JAR from the official distribution,
  scripts from the official v8.11.1 tag.
- External subtitles integrated end-to-end via subtitle-core: in-app byte decoding
  (BOM, strict UTF-8, Windows-1256 fallback, manual override incl. Windows-1252),
  parsing, and a Compose overlay renderer with persisted settings (enable, font
  family/size, color, bold, background opacity, delay, encoding, reset). Encoding
  changes re-decode the loaded file in place. The loaded subtitle's charset, format,
  and cue count are shown in the UI. Media3's subtitle view is hidden while the
  overlay is active to avoid double rendering; embedded tracks still use Media3.
- Playback speed selection UI (0.5×–2×) wired to `PlaybackEngine.setPlaybackSpeed`.
- "Subtitles: Off" track entry backed by the new `TRACK_ID_SUBTITLES_OFF` engine
  contract; selecting a text track re-enables the renderer.
- Engine error state: snapshots now carry `PlaybackState.ERROR` with error code and
  message after a Media3 error (state existed in the API but was never emitted).
- Decoder reporting: `PlaybackSnapshot` gains `videoDecoderName`, `audioDecoderName`,
  and a populated `decodePath` (hardware/software heuristic from the decoder name,
  documented as a family indicator, not a performance guarantee). Surfaced in the
  status line and a new Device diagnostics dialog listing the full decoder inventory.
- Decoder fallback enabled in the renderers factory (retry with another decoder
  when the preferred one fails to initialize — important on older TVs).
- Resume: file-backed bookmark store (`FileBookmarkStore`, pure JVM, atomic-ish
  temp-file writes, malformed-line tolerant) persists per-file positions; reopening
  a file shows a resume prompt. Positions autosave every 5s during playback and on
  dispose/media switch.
- Launcher icon (5 densities) and Android TV banner; `configChanges` for rotation;
  INTERNET permission removed (local SAF playback only).
- Tests: 16 → 35. New: subtitle golden corpus (Arabic SRT, mixed-direction Persian,
  malformed blocks, WebVTT NOTE blocks, ASS commas, UTF-16BE BOM, Windows-1252
  override, content-based format detection), RemoteActionMapper tests,
  FileBookmarkStore tests.
- CI: `.github/workflows/android-ci.yml` (unit tests + assembleDebug + lint report).
- Docs: README and BUILD_STATUS rewritten for 0.4.0; `DOCS/COMPATIBILITY_MATRIX.md`
  and `DOCS/THIRD_PARTY_LICENSES.md` added; `PROGRESS.md` records the stage gates.

### Notes

- BUILD_STATUS.md in 0.3.0 claimed a "Windows-1256/Windows-1252 fallback"; the code
  implemented only Windows-1256. In 0.4.0, Windows-1252 is available as a manual
  encoding override (tested); the automatic fallback remains Persian-first 1256.
- No physical-device verification in this release; see BUILD_STATUS.md.

## 0.3.0-foundation

Initial modular foundation (see git history / 0.3.0 archive): 9 modules, Media3
playback screen, subtitle-core decoder/parser, capability/input/storage skeletons.
Shipped with two build-breaking defects and one failing test, all fixed in 0.4.0.
