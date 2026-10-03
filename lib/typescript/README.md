# @pathland/dom-renderer

The **Pathland DOM renderer** — the web client. A small, vanilla-TypeScript
hydration client (zero runtime dependencies) that:

- **Hydrates** the server-rendered DOM by `data-pathland-id`.
- **Decodes** self-contained `PLPL` opcode batches (bounds-checked) and applies
  them in place: **`STYLE` deltas** (the full property catalog — layout, text,
  styling, effects, semantic/control variants, plus `SET_DESIGN_TOKEN` →
  CSS variables) and **`TREE` deltas** (create/delete/insert/remove/move —
  runtime structural updates without a full reload).
- **Reports raw inputs** back over WebSocket as `EVENT` batches — the full event
  catalog (pointer down/move/up, key down/up, value/text/date changed, focus/
  editing, submit, scroll, wheel) — guest → host (flags `0x0000`), gated by the
  SSR `EVENT_LISTENERS` mask.
- **Negotiates** the protocol version (mismatch → reload) and **reconnects**
  with exponential backoff.

Styling is **Option A**: the protocol stays renderer-agnostic; the SSR HTML
carries Tailwind classes (server-compiled), and runtime style deltas are applied
as inline style (overriding the current value), with literal colors inline and
semantic design tokens as CSS variables. The DOM renderer owns the bounded
property → style/class map (`src/classes.ts`).

## Layout

```
src/
  constants.ts   protocol constants (mirror spec/)
  plpl.ts        bounds-checked batch parse + string decode
  format.ts      f32/ARGB/date/float formatting
  elements.ts    component → DOM shell factory (TREE create)
  classes.ts     Option A style/state appliers (inline style + ARIA + CSS vars)
  apply.ts       delta application (STYLE + TREE) over the id → Node registry
  events.ts      guest → host EVENT encoders
  transport.ts   WebSocket connect/reconnect/version negotiation
  index.ts       bootstrap (hydrate + connect + listeners)
```

## Build & test

```bash
npm ci
npm test          # vitest (happy-dom): codec round-trips, TREE/STYLE apply, transport
npm run build     # tsc --noEmit + esbuild -> dist/pathland-dom-renderer.js (IIFE, minified)
npm run copy-to-demos   # copy the bundle into both Java demos' static dirs
```

## The bundle

`dist/pathland-dom-renderer.js` is served by the Spring Boot and Quarkus demos
as `/pathland-dom-renderer.js` (copied into each demo's static resources by
`npm run copy-to-demos`).

## Transport resilience

`src/transport.ts` reconnects with exponential backoff, keeps the connection
alive with a `META::PING`/`PONG` heartbeat + no-activity watchdog, and recovers
from lost deltas: a **sequence gap** (a batch whose per-stream `sequence` breaks
the chain) makes the client skip the gapped batch and request a full snapshot
(`META::RESYNC`). See `spec/OPCODE.md` §Transport heartbeat and §Sequence gap
detection.