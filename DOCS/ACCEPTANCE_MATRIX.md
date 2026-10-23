# Acceptance matrix — not yet passed

| Area | Required cases | Status |
|---|---|---|
| Containers | MP4, MKV, MOV, M4V, WebM, AVI, TS, M2TS, HLS, DASH | Not run |
| Video codecs | AVC, HEVC Main/Main10, VP9, AV1, MPEG-2/4; ProRes where practical; VVC research-only | Not run |
| Audio | AAC, ALAC, AC-3, E-AC-3, DTS/DTS-HD, FLAC, Opus, TrueHD | Not run |
| Persian subtitles | UTF-8 BOM/no BOM, UTF-16, Windows-1256, mixed RTL/LTR, digits, punctuation, multiline | Not run |
| Subtitle formats | SRT, WebVTT, ASS/SSA, TTML; bitmap formats separately evaluated | Not run |
| Remote | D-pad, Enter/OK, Back, media keys, Bluetooth remote, long press/repeat, focus | Not run |
| Performance | low-power older Android TV + modern Android TV; sustained playback and thermal behavior | Not run |
| Resume | process death, force stop behavior, duration/position edge cases | Not run |
| Platform | Android TV launcher and non-touch operation | Not run |

For each codec sample, record device/model/year, Android version, MIME, profile/level, bit depth, resolution, fps, HDR, decoder chosen, dropped frames, audio passthrough and result. Mark **HW**, **SW**, **transcode**, or **unsupported** separately.
