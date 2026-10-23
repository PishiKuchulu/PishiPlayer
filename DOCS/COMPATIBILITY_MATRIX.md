# Compatibility matrix — 0.4.1

Honesty rule: a row is VERIFIED only when it was executed in the stated layer.
"Code-level" means the handling exists and is covered by unit tests or compilation;
it is NOT a playback result. All physical-device rows are NOT TESTED in 0.4.0.

## Subtitles (subtitle-core, pure JVM — executed)

| Case | Layer | Result |
|---|---|---|
| SRT Persian multiline | unit test | PASS |
| SRT Arabic | unit test | PASS |
| SRT mixed RTL/LTR with Latin words + digits | unit test | PASS (text preserved verbatim) |
| SRT malformed blocks (bad timestamp lines) | unit test | PASS (skipped, no invented cues) |
| SRT out-of-order / overlapping cues | unit test | PASS (sorted by start) |
| SRT inline HTML tags (`<i>`, `<font …>` etc.) | unit test | PASS (0.4.1: stripped; non-tag `<` text like `5 < 6`, `<3` preserved) |
| WebVTT tags (generic), cue identifiers, NOTE blocks | unit test | PASS |
| WebVTT entities (named/decimal/hex incl. `&lrm;`/`&rlm;`) and timestamp tags | unit test | PASS (0.4.1: decoded/stripped; unknown entities untouched) |
| ASS dialogue (style id, \an alignment, \N, commas in text) | unit test | PASS (parse level only — no ASS rendering) |
| ASS styling/effects/karaoke/positioning rendering | — | NOT IMPLEMENTED (documented; overlay renders plain text) |
| UTF-8 with/without BOM | unit test | PASS |
| UTF-16 LE/BE with BOM | unit test | PASS |
| Windows-1256 legacy bytes (auto fallback) | unit test | PASS |
| Windows-1252 (manual override) | unit test | PASS |
| Oversized file (>16 MiB) | unit test | PASS (rejected with error) |
| Persian/Arabic glyph shaping on a real device | device | NOT TESTED (platform text stack is expected to shape; unverified) |

## Containers / codecs (Media3 + platform decoders)

| Format family | Demux/decode path | Device result |
|---|---|---|
| MP4 (H.264/AAC), MKV, WebM, MOV/M4V, AVI, HLS, DASH | Media3 extractors + platform MediaCodec decoders | NOT TESTED on devices in 0.4.0 |
| HEVC / VP9 / AV1 / MPEG-4 video | platform decoder if present; app reports the chosen decoder + HW/SW path in the Device dialog | NOT TESTED; no software fallback bundled |
| AC-3 / E-AC-3 / DTS / FLAC / ALAC / Opus / Vorbis audio | platform decoder if present; passthrough not configured | NOT TESTED |
| ProRes | no platform decoder on typical Android devices | EXPECTED UNSUPPORTED (not a promise) |
| FFmpeg / software decoding | not integrated | See FFMPEG_DECISION.md |

## Remote control (code-level)

| Behavior | Result |
|---|---|
| Focused button activation with DPAD_CENTER/ENTER | implemented (keys are focus-owned); device NOT TESTED |
| Seek on video surface, bounded repeat steps | unit-tested mapper + implementation; device NOT TESTED |
| Dedicated Play/Pause keys distinct from toggle | unit test PASS |
| Back closes panels before exit | implemented; device NOT TESTED |
