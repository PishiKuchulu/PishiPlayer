# PishiMovie → Universal Player reuse map

## Reuse concepts, not application coupling

| Source area | Decision | Why / work required |
|---|---|---|
| `SubtitleSettings` and profiles | Adapt model concepts | Separate presentation settings from the renderer and prove every option affects output. |
| Vazirmatn font files | Candidate reuse | Check license/notice and font fallback behavior before packaging. |
| Track enumeration/selection | Reimplement behind `PlaybackEngine` | Keep IDs stable only within a playback session; test audio/text/video selection. |
| Playback progress/session | Adapt | Add persistence abstraction and lifecycle-safe flushes. |
| `MediaSourceFactory` | Rewrite/isolate | Remove PishiMovie Referer/User-Agent, define host-scoped headers, cache lifecycle and local SAF URI handling. |
| `SubtitleManager` | Do not copy unchanged | Existing notes identify limitations around RTL/complex shaping, encoding, line spacing and alignment. |
| `VideoPlayerActivity.onKeyDown` | Use as behavioral reference | Centralize input; avoid duplicate seek calls through Activity and MediaSession. |
| catalog, API, branding, navigation | Exclude | Not part of a local/universal player core. |

## Must verify from original source

- Exact PishiMovie source revision and license notices.
- Every subtitle setting: model → UI → persistence → renderer wiring.
- Media3 dependency versions and Android API minimum.
- Cache singleton/session ownership and release behavior.
- Stream-specific headers and redirect behavior.
