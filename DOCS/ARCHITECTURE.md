# Architecture and module contracts

```text
app-android
  ├── player-ui-android ── player-api
  ├── player-android ───── player-api
  ├── subtitle-android ─── subtitle-core
  ├── device-capabilities
  ├── tv-input ─────────── player-api
  └── playback-storage

Future clients: Tizen / webOS each get their own UI + native/browser playback adapter.
```

## Rules

1. `player-api` must not depend on Android, Media3, Compose, or FFmpeg.
2. Decoder selection is an adapter decision. Advertised capability is not proof of real-time performance.
3. UI invokes one controller route per input event. Activity, MediaSession and Compose must not independently process the same seek.
4. HTTP headers are explicit per source and must not leak across hosts.
5. Subtitle parsing/normalization is separate from rendering. Never mutate ASS/SSA syntax with text normalization.
6. Optional native decoders are isolated by ABI and must document licenses, enabled codecs, source/binary provenance, and update process.
7. A platform port is complete only after build, controls, codec matrix, subtitle corpus and device tests pass.
