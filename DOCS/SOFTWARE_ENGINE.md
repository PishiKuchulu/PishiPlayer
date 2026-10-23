# Software engine (engine #2) — decision record

**Status: implemented in 0.5.0** (module `:player-mpv`). This document resolves
the checklist left open in `FFMPEG_DECISION.md`.

## Why

Universal Player targets Android TVs. Many older TVs have no hardware decoder
for modern codecs (HEVC, AV1, VP9 in newer profiles). The Media3 engine can
only use platform decoders, so those files simply fail. The software engine
exists so such a TV can still play them via FFmpeg software decoding.

## Chosen distribution

- **Artifact:** `dev.jdtech.mpv:libmpv:0.4.1` from Maven Central (wrapper by
  Jarne Demeulemeester, MIT; the same library Findroid ships). Verified by
  download: AAR 44,177,434 B, 4 ABIs, `classes.jar` API inspected directly.
- **Bundled natives:** mpv 0.39.0, FFmpeg 7.1, dav1d 1.4.3 (AV1 software
  decoding), libass 0.17.3, libplacebo 7.349.0. FFmpeg is a playback-only
  build (`--enable-mediacodec` present, but the engine sets `hwdec=no` —
  see Routing).
- **Why 0.4.1 and not 1.0.0:** 1.0.0 declares `minCompileSdk=36`; the project
  compiles against SDK 35. 0.4.1 has no such constraint. Revisit when the
  project moves to compileSdk 36.

## FFMPEG_DECISION checklist, answered

- **ABI targets / min API:** arm64-v8a + armeabi-v7a (TVs), x86_64 (emulator),
  via ABI splits plus a universal APK. Minimum API is **26** — the AAR's
  manifest floor; the app's minSdk was raised 23 → 26 in 0.5.0.
- **Version / build source:** pinned above; upstream build scripts live in
  `github.com/jarnedemeulemeester/libmpv-android`.
- **Licensing:** the wrapper is MIT, but mpv is GPLv2+ and this FFmpeg build
  is configured `--enable-gpl --enable-version3`, so the bundled native set
  must be treated as **GPLv3** for distribution. The app is distributed with
  its source in a public repository; notices are recorded in
  `DOCS/THIRD_PARTY_LICENSES.md`. The AAR itself ships no LICENSE file.
- **Size budget:** natives are ~22.0 MiB (arm64-v8a) / ~19.8 MiB (armeabi-v7a)
  uncompressed per ABI — ABI splits are mandatory, not optional; the
  universal APK carries every ABI (~45 MiB of natives for the two TV ABIs
  alone) and exists for direct sideloading convenience.
- **Crash isolation:** software decoding runs **in-process**; a native crash
  takes the player down with it. Accepted for this version; process
  isolation is deferred (mpv's embedding model makes it a larger change).
- **Scope:** playback only. No transcoding (local or server). Sources:
  `file://`, `http(s)://`, and `content://` via an OS file descriptor
  (`fdclose://`) because FFmpeg cannot open content URIs.

## Routing

`EngineFallbackPolicy` (player-api, unit-tested):

- **Auto (default):** Media3 (hardware) first. If it reports a decoder-class
  error (`ERROR_CODE_DECODER_*` / `ERROR_CODE_DECODING_*`), the app retries
  the same source once on the software engine, resuming at the reached
  position. I/O and parser errors never trigger the fallback.
- **Hardware:** Media3 only, never falls back.
- **Software:** mpv for everything.

The user switches preference in the **Engine** dialog; explicit Hardware /
Software choices re-route the current file immediately, Auto never yanks a
working fallback mid-file.

## Performance honesty

Software decoding of 1080p HEVC/VP9/AV1 is realistic on typical older TV SoCs
(quad Cortex-A53 class, multithreaded FFmpeg; dav1d for AV1). Software
decoding of 4K or Main10 content on such hardware is **not** realistic and is
not promised anywhere in the UI or docs; the engine reports decode path
honestly as SOFTWARE in the status line.

## Device-verification checklist (open)

- [ ] `fdclose://` playback of a SAF-picked `content://` file on a real TV.
- [ ] Surface attach timing when the fallback switch recreates the video view.
- [ ] mpv core destroy→create across repeated engine switches.
- [ ] 1080p HEVC software playback smoothness on a Cortex-A53-class TV.
- [ ] AV1 (dav1d) 1080p playback on the same class of hardware.
