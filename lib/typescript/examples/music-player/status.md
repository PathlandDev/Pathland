# examples/music-player — status

**Last updated:** October 22, 2026

## Implemented

- Apple Music-style player authored with the TypeScript view DSL:
  - library list with selectable rows and reactive active-row highlighting;
  - now-playing sidebar with reactive album art/title/artist and up-next rows;
  - player bar with prev/play-pause/next, track info, progress, seek, and volume.
- App-owned persisted transport state (`player.track`, `player.playing`,
  `player.position`, `player.volume`, seek/volume commands).
- `MediaController` bound to the app signals; actual audio plays through the
  existing DOM client's media pipeline (`MEDIA_*` commands + reports).
- Reuses the shared Java demo's album art/audio assets under
  `/_pathland/assets/`.
- Playwright e2e: SSR content, track selection (source/cover/title deltas),
  play/pause icon toggling, and live range inputs.

## Not implemented / gaps

- Karaoke lyric pill and the sponsored proxy slot from the Java demo are not
  ported.
- Seek is live rather than seek-on-release; no `EDITING_CHANGED` deferral.
- The demo uses `MemoryStateStore` (no Redis persistence).
