# PROGRESS — Universal Player completion (0.3.0 → 0.4.0)

Handover contract: a fresh session must be able to continue from this file plus the
source alone. Status labels: PASS (executed), FAIL (executed, failed), BLOCKED
(could not execute, reason given), NOT TESTED, PARTIAL.

## Baseline (2026-10-23, before changes)

- Independent audit of the 0.3.0 archive: 44 files, 825 Kotlin LOC.
- Established facts: (1) device-capabilities does not compile (MediaCodecInfo API
  misuse, javap-verified on API 35 android.jar); (2) malformed dependency coordinate
  `androidx.compose.ui:ui:ui-viewbinding:1.7.8` (version does not exist in Google
  Maven metadata); (3) no Gradle wrapper files; (4) shipped suite 15/16 — ASS test
  fails on a fixture bug, parser probe-verified correct on real files; (5) five
  modules dead (subtitle-core, tv-input, device-capabilities, playback-storage,
  player-ui-android unused; subtitle-android not even an app dependency);
  (6) TV key routing consumes DPAD globally → focused buttons unusable by remote.
- Git checkpoint: baseline commit of the archive as delivered.

## Gate A — build & architecture stabilization: PASS (sandbox scope)

- DecoderCapabilityScanner → CodecCapabilities API. Compiled vs API 35 android.jar: PASS.
- Malformed dependency removed: PASS (metadata-verified nonexistent; unused).
- ASS test fixture fixed (raw-string backslash). Full JVM suite re-run: PASS.
- Gradle wrapper reconstructed from official artifacts (jar byte-extracted from the
  Gradle 8.11.1 distribution; scripts from the gradle/gradle v8.11.1 tag).
  `./gradlew` execution in this sandbox: BLOCKED — Java HTTPS through the egress
  proxy fails (wrapper cannot download) and the Gradle daemon dies immediately in
  this environment (reproduced with the official distribution: "Could not receive a
  message from the daemon"). Wrapper execution is delegated to CI.
- Full AGP build + lint: NOT RUN HERE — CI workflow added (`.github/workflows/android-ci.yml`).

## Implementation stages: DONE

- Engine (player-android): ERROR state + code/message, decoder name/decode-path
  reporting (AnalyticsListener), decoder fallback, subtitles-off contract,
  track-type classification fix. Compiled vs real Media3 1.6.1 artifacts: PASS.
- TV input (tv-input + app): RemoteActionMapper redesigned (Play/Pause separated,
  bounded seek steps, chapter keys, INFO) and used as the single routing path for
  the video surface + global media keys; DPAD is focus-owned; BackHandler
  implemented; track polling removed from the UI loop (listener-driven).
  Mapper covered by 6 new JVM tests: PASS.
- Subtitles (subtitle-core + subtitle-android + app): external files decoded/parsed
  in-app and rendered by a Compose overlay; persisted settings (font, size, color,
  bold, background, delay, encoding override with live re-decode, reset); settings
  surface gated by ComposeOverlayRenderingPlan (no fake outline/position controls);
  Media3 subtitle view hidden while the overlay is active. Corpus tests (9 new): PASS.
- Capabilities (device-capabilities + app): Device dialog shows active decoders,
  decode path, and the full inventory with HW/SW/secure flags.
- Storage (playback-storage + app): FileBookmarkStore (pure JVM, temp-file writes,
  malformed-line tolerant) + resume prompt + autosave. 4 new tests: PASS.
- Manifest/resources: icon (5 densities), TV banner, configChanges instead of the
  landscape lock, INTERNET permission removed, version 0.4.0 (versionCode 2).

## Final verification (2026-10-23)

- All module sources compile: JVM modules with Kotlin 2.0.21; Android modules against
  the official API 35 android.jar and the real published dependency artifacts
  (Media3 1.6.1, Compose 1.7.8, Material3 1.3.1, activity-compose 1.10.1, …);
  app-android additionally with the Compose compiler plugin 2.0.21: PASS (1 benign
  pre-existing @OptIn warning). The type-check caught one real bug during the pass
  (cross-module smart-cast on `PlaybackBookmark.durationMs`) — fixed.
- Unit tests: 35/35 PASS (JUnit 4.13.2, executed locally).
- AGP assembleDebug / lint / device playback: NOT TESTED here (see Gate A blockers);
  CI + a physical device are the next verifiers.

## 0.4.1 — subtitle markup polish (2026-10-23): DONE

- Test-first: SubtitleMarkupTest (10 tests) written before the change; 7 failed
  against the 0.4.0 parser, the 3 preservation tests (non-tag `<`, unknown
  entities, ASS untouched) passed, as designed.
- SubtitleParser: shared `cleanInlineMarkup` for SRT + WebVTT (strip real tags —
  letter after `<`/`</` — then decode entities; `<br>` → space; VTT timestamp
  tags stripped). Entities: named set incl. `&lrm;`/`&rlm;`, decimal, hex;
  unknown entities untouched. ASS path untouched.
- Full suite re-run: 45/45 PASS. Docs/version bumped to 0.4.1 (versionCode 3).

## Next steps for whoever continues

1. Push to GitHub, watch `android-ci.yml` (first true AGP build + lint), fix fallout.
2. Device matrix on a real older Android TV + a modern TV + a phone: playback corpus
   (DOCS/COMPATIBILITY_MATRIX.md), remote behavior checklist, Persian glyph check.
3. FFmpeg decision per DOCS/FFMPEG_DECISION.md (licensing/ABI/size gates) before any
   software-decoding promise.
4. Optional: bundle Vazirmatn (OFL) as the default Persian subtitle font after a
   device shaping check; keep the system-font choices as fallback.
5. Optional: ASS per-cue positioning in the overlay, behind the rendering plan.
