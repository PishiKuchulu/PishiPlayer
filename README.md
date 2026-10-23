# Universal Player — 0.4.1

Independent modular Android / Android TV local media player. This release turns the
0.3.0 foundation into a wired-up player (0.4.1 adds SRT/VTT markup cleanup — see
CHANGELOG.md). Every module in the build is used by the app,
the subtitle core is integrated into the real playback path, and TV remote routing is
centralized. It is **not** a finished universal-codec player: playback still relies on
the device's platform decoders through Media3, FFmpeg is not integrated, and no
physical-device playback verification has been performed yet. See `BUILD_STATUS.md`
for the exact PASS / FAIL / BLOCKED / NOT TESTED breakdown and `PROGRESS.md` for the
stage-by-stage record.

## Modules (all wired into the app)

- `app-android`: Compose player screen, SAF media/subtitle pickers, external-subtitle
  overlay, subtitle settings, resume prompt, device diagnostics dialog.
- `player-api`: platform-neutral playback contracts and media models.
- `player-android`: Media3 adapter — decoder fallback enabled, error state, decoder
  name / decode-path reporting, track enumeration and selection (including subtitles-off).
- `subtitle-core`: BOM-aware subtitle byte decoding, explicit charset override,
  Persian-first Windows-1256 fallback for non-UTF-8 bytes, SRT/WebVTT/ASS/SSA parsing.
  Pure JVM, fully unit-tested.
- `subtitle-android`: rendering-capability contract; `ComposeOverlayRenderingPlan`
  declares exactly what the app's overlay renderer can and cannot do.
- `device-capabilities`: enumerates advertised Android decoders (CodecCapabilities-based);
  shown in the app's Device dialog. Inventory only — not a performance guarantee.
- `tv-input`: `RemoteActionMapper` — the single key-routing table for the video surface
  (bounded seek steps; dedicated Play and Pause keys are not toggles).
- `playback-storage`: bookmark store — in-memory (tests/demo) and file-backed
  implementations; the app persists resume points to internal storage.
- `player-ui-android`: playback control-state model used by the control bar.

## Build

Requires JDK 17 and Android SDK Platform 35. The Gradle wrapper (8.11.1) is included:

```
./gradlew :app-android:assembleDebug
./gradlew :player-api:test :subtitle-core:test :playback-storage:test :tv-input:testDebugUnitTest
```

A GitHub Actions workflow (`.github/workflows/android-ci.yml`) runs the same tasks.
Kotlin/API facts in this tree were additionally verified by compiling every module
against the official API 35 `android.jar` and the real published dependency artifacts
(Media3 1.6.1, Compose 1.7.8, …) with the Kotlin 2.0.21 compiler and the Compose
compiler plugin; all 35 JVM unit tests pass. The full AGP build itself is verified
by CI, not by that type-check — see `BUILD_STATUS.md`.

## Current playback capabilities

- Select local audio/video files through Android's Storage Access Framework.
- Media3-backed playback with play/pause, ±10/±30-second seek, timeline, duration,
  playback-speed selection (0.5×–2×), and real error states with messages.
- Enumerate/select audio, text, and video tracks; a "Subtitles: Off" entry disables
  the text renderer.
- External subtitle files are decoded and parsed in-app by subtitle-core (UTF-8 /
  UTF-16 BOM, strict UTF-8, Windows-1256 fallback, or a manual encoding override:
  UTF-8, UTF-16, Windows-1256, Windows-1252) and rendered by a Compose overlay with
  persisted settings: font family/size, text color, bold, background opacity,
  subtitle delay, enable/disable, reset. Changing the encoding re-decodes the
  already-loaded file immediately.
- Embedded subtitle tracks render through Media3's built-in subtitle view.
- The status line and Device dialog report the active video/audio decoder names,
  the inferred decode path (hardware/software), and the full device decoder inventory.
- Resume: playback position is saved per file; reopening a file offers a resume prompt.
- Android TV remotes: focus-based navigation (buttons activate, slider adjusts,
  focus moves); when the video surface has focus, OK toggles playback and ←/→ seek;
  dedicated media keys work globally; Back closes panels before exiting.

## Important limits

- **FFmpeg is not integrated.** Software decoding beyond the platform's own software
  decoders does not exist yet; see `DOCS/FFMPEG_DECISION.md`.
- Hardware decoder inventory is a capability hint only. Actual playback depends on
  codec profile/level, bit depth, resolution, frame rate, HDR, firmware, and thermals.
- The external-subtitle overlay renders one cue at a time with platform text shaping
  (Persian/Arabic shaping and bidi come from Android's text stack). It does **not**
  reproduce ASS styling/effects, per-cue positioning, karaoke, or text outline; the
  settings UI only offers controls the renderer actually honors.
- SRT/WebVTT inline formatting tags are stripped and HTML entities (named, decimal,
  hex — including `&lrm;`/`&rlm;`) are decoded by subtitle-core; WebVTT timestamp tags
  are removed. A `<` that does not start a real tag (e.g. `5 < 6`, `<3`) is preserved.
- Persian/Arabic rendering correctness is covered by parser/decoder golden tests at
  the text level only. **On-device glyph-level verification is still outstanding.**
- Tizen and webOS would require separate applications and playback adapters; nothing
  in this tree runs on them.
- No network playback UI exists, so the app declares no INTERNET permission. Media
  is opened from local storage via SAF only.
- Track IDs are session-scoped (Media3 group indices); they are not stable across
  app restarts.

## Security

No external endpoints, API keys, or service-specific headers are included. Only SAF
user-selected files are read. Subtitle files are size-capped at 16 MiB before parsing,
and malformed subtitle bytes surface as errors instead of crashes.

## Next milestones

1. Run the CI workflow (first full AGP build + lint) and fix anything it surfaces.
2. Physical-device matrix: older Android TV + modern TV + phone; codec/subtitle corpus.
3. On-device Persian/Arabic glyph verification for the overlay renderer.
4. FFmpeg/software-decoding decision (see `DOCS/FFMPEG_DECISION.md`).
5. Richer ASS rendering evaluation (per-cue positioning) behind the rendering plan.
