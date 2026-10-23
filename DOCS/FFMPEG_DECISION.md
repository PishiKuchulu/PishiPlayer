# FFmpeg integration decision — pending

Do not claim FFmpeg support until an actual native decoder pipeline is integrated and tested. Before selecting a distribution, decide:

- ABI targets and minimum Android API.
- Exact FFmpeg version, enabled/disabled components and reproducible build source.
- LGPL/GPL configuration and distribution obligations; include notices/source offer as applicable.
- APK/AAB size budget, split APK/ABI strategy and update/security policy.
- Whether software decoding runs in-process, how native crashes are isolated, and which formats are in scope.
- Whether transcoding is local, external-server based, or out of scope.

Software decoding of 4K AV1/HEVC Main10 on old TV hardware may be too slow regardless of library choice. Capability probing and fallback must not promise impossible performance.
