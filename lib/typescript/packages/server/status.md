# @pathland/server — status

**Last updated:** October 22, 2026

## Implemented

- `PathlandApp`, `PathlandConnection`.
- `PathlandSession`: mount-frame capture, preloaded state, SSR rendering,
  live deltas after `connect`, environment application, event dispatch, resync,
  and teardown.
- `PathlandRegistry`: 1:1 sessions, separate `uiId`/`wid`, lazy session creation
  on the first message, per-session async queue, throwaway SSR sessions, resync
  and close.
- `DeltaBatcher`: frame coalescing, string-offset rebasing, timer/byte flush,
  and monotonic per-session sequences.
- WebSocket attach: environment/resync/event routing and `META::PING` →
  `META::PONG`.
- Express adapter: static assets, SSR for HTML GETs, and WebSocket attach.
- NestJS adapter: `@PathlandView()` metadata + `PathlandInterceptor`.
- Tests cover SSR, live environment/session creation, text deltas, resync,
  PING/PONG, sequence monotonicity, and persisted-state preload.

## Not implemented / gaps

- Redis state store and session TTL/idle cleanup (deferred).
- Proxy views and multi-app mounts are not ported from the Java server core.
- NestJS WebSocket integration is via `attachPathlandWebSocket`; no Nest
  gateway wrapper yet.
