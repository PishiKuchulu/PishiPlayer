# FFmpeg integration decision — RESOLVED (2026-10-23)

Resolved in favor of a libmpv-based software engine; see
`DOCS/SOFTWARE_ENGINE.md` for the full decision record (artifact, versions,
licensing, ABI/size strategy, routing, and the open device-verification
checklist).

The standing caution from the original pending note still applies and is
repeated in the decision record: software decoding of 4K AV1/HEVC Main10 on
old TV hardware may be too slow regardless of library choice — capability
reporting and fallback must never promise impossible performance.
