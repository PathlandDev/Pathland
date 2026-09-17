# @pathland/dom-renderer (lib/typescript) — implementation status

**Last updated:** September 4, 2026

The **Pathland DOM renderer** — the web client. A vanilla-TypeScript hydration
client (no runtime dependencies) that is the single source of the client,
replacing the two duplicated `app.js` files in the demos. Protocol contract:
`spec/`.

## Implemented

- **Single source, unified**: `src/` is the one client; `npm run build` emits
  `dist/pathland-dom-renderer.js` (IIFE, minified via esbuild), and
  `npm run copy-to-demos` copies it into both the Quarkus and Spring Boot demos'
  static resource dirs. Both demos serve `/pathland-dom-renderer.js`; the old
  duplicated `app.js` is removed.
- **Hydration**: builds the `data-pathland-id → element` registry from the SSR
  DOM.
- **Logging** (`src/log.ts`, `src/describe.ts`): a tiny **zero-dependency**
  logger (levels + `[pathland:ns]` namespaces, default `info`; opt into
  `debug` with `window.__PATHLAND_LOG_LEVEL="debug"` or
  `?pathland-log=debug`). Meaningful **receive/emit** logging of opcodes and
  events — `→ send EVENT POINTER_UP(target=4, …)`, `← recv frame=7 (2 ops):
  STYLE SET_PROPERTY(ROUTE="/users", …)`, plus lifecycle (connect/reconnect,
  `pushState`, `popstate`, environment) — with a full per-opcode trace at
  `debug` (`src/describe.ts`).
- **PLPL decode** (`src/plpl.ts`): bounds-checked batch parse — magic/version
  validation, truncated-opcode/string rejection, length-prefixed string reads.
- **Full delta application** (`src/apply.ts`):
  - **STYLE commands**: `SET_PROPERTY`, `SET_TEXT`, `SET_DATE` (date/time/
    datetime-local), **`SET_DESIGN_TOKEN`** (→ CSS variables, `src/tokens.ts`).
  - **TREE commands**: `CREATE_NODE`/`DELETE_NODE`/`INSERT_CHILD`/`REMOVE_CHILD`/
    `MOVE_CHILD` (`u32::MAX` append handled) — the element-shell factory
    (`src/elements.ts`) covers every primitive/control component.
  - **META commands**: `RESET` clears the DOM + registry; `ENVIRONMENT` accepted.
- **Full property applier** (`src/classes.ts`, table-driven): the complete
  spec/MODIFIERS.md + control/semantic catalog — layout (spacing/alignment/
  margins/offset/position/frame min-ideal-max/fixed-size/layout-priority/
  aspect-ratio/content-mode), text (align/line-limit/truncation/font-size/
  weight/style/design/width/kerning/tracking/baseline/line-spacing/text-case/
  underline/strikethrough), styling (color/background/border width-color-radius-
  edges/z-index/clips-to-bounds/padding*/tint), effects (shadow/blur/saturation/
  contrast/brightness/grayscale/hue-rotation/color-invert/rotation/scale/
  allows-hit-testing), semantic (role/state/enabled/selected/value/min-max/label/
  prompt), **structural control variants** (is-secure text↔password,
  toggle-style switch↔checkbox↔button, date-picker-mode date↔time↔datetime-local,
  picker-style/control-size as data tokens, step-value, progress/indeterminate),
  binding (action-id/binding-id as `data-*`), image-source, shape-kind.
- **Value types**: `U8`/`U32`/`I32`/`F32`/`STRING`/`ENUM`/`COLOR`/`DESIGN_TOKEN`
  handled (`enumCode`/`f32`/`isOn` decoders in classes.ts; token values in
  tokens.ts).
- **Design tokens** (`src/tokens.ts`, spec/TOKENS.md): `SET_DESIGN_TOKEN`
  overrides applied via a managed `<style data-pathland-tokens>` element —
  **base (light) tokens as `:root` rules, `dark.*` overrides inside
  `@media (prefers-color-scheme: dark)`** (so the browser resolves the scheme
  natively and inline-style cascade can't break light mode). Canonical
  `--pl-<path>` variable names (the `dark.` scheme prefix is stripped), F32
  length tokens emitted with `px`, and **`DESIGN_TOKEN` property references**
  resolved to `var(--pl-…)`, with the generative `space.<N>` family resolving
  to `calc(var(--pl-space-base) * N)` (`applyTokenRefProperty` in classes.ts).
  **`STRING`-valued overrides** (e.g. `font.body.family`) resolve the value
  string from the batch's string section and emit it single-quoted.
- **Full event encoders** (`src/events.ts`): `POINTER_DOWN` (x/y + secondary),
  `POINTER_MOVE` (x/y + hover enter/leave), `POINTER_UP` (x/y + secondary),
  `KEY_DOWN`/`KEY_UP` (keyCode + modifiers + repeat), `VALUE_CHANGED` (f32 or
  raw bits), `TEXT_CHANGED` (string section), `DATE_CHANGED`, `FOCUS_CHANGED`,
  `EDITING_CHANGED`, `SUBMIT`, `SCROLL`, `WHEEL`, **`NAVIGATE`** (`encodeNavigate`
  URL in the string section + `NAVIGATE_URL` flag; `encodeNavigateBack` no URL),
  and **`META::ENVIRONMENT`** (`encodeEnvironment(width, height, route)`) — guest →
  host (flags `0x0000`).
- **Platform environment** (spec/OPCODE.md §Environment fields): the client sends
  `encodeEnvironment` as its **first** WS message (`transport.ts` `onOpen`, before
  any resync) so the server session seeds its router from the `ROUTE` field, and
  re-sends it on `window.resize` to **enrich** the environment after connect
  (viewport + future fields ride the same message).
- **URL mirroring + back/forward** (spec DSL.md §4.5): a slot's `ROUTE`
  (STRING) property updates `data-pathland-route` and fires the `onRoute` hook
  (`src/index.ts` wires it to `history.pushState` — never called on hydrate, so
  no duplicate history entry); `popstate` reports `location.href` as a
  `NAVIGATE` event so the app routes. `pushState` never fires `popstate`, so
  server-originated navigation cannot loop. **Swap transitions**: a child
  inserted into a `data-pathland-transition` slot is animated in (fade/slide/
  scale keyframes injected once into a `<style data-pathland-transitions>`),
  a renderer-side animation of its own output cache — never app state.
- **Renderer-provided default back button** (spec DSL.md §4.5): the web has no
  native navigation container, so the DOM renderer draws its own back
  affordance for a `PlatformDefault` nav slot at depth > 1 — a `.pathland-nav-back`
  button injected above the destination when `NAV_DEPTH` (`data-pathland-depth`)
  exceeds 1 and removed at depth 1 / for `Custom` chrome
  (`data-pathland-nav-chrome="custom"`, `NAV_CHROME=1`). The button is excluded
  from reconcile indexing (`insertAt` skips the class) so TREE deltas never
  displace it; clicking it fires `onNavigateBack`, wired in `src/index.ts` to
  `encodeNavigateBack()` (a `NAVIGATE` back request — the app pops). Hydrated
  once at boot from the SSR HTML via `updateNavBackButtons`.
- **`EVENT_LISTENERS` gating**: `src/index.ts` reads the SSR `data-event-listeners`
  mask (mirroring the Rust renderer's event attrs) and emits an event only when
  the node opted into it; absent attribute = permissive (no gating info). Pointer
  events (down/move/up) resolve to the **nearest listening ancestor**
  (`eventNodeOf`): a gesture declared on a container (e.g. a tap on a `VStack` row
  whose child `Text` is clicked) is attributed to the node that declared the mask,
  and `POINTER_UP` on `click` is sent for **any** listening element — not just
  `<button>` — so `onTapGesture` works on non-button nodes (matching the GTK
  renderer's gesture-on-any-widget behavior; also repairs composite-label buttons).
- **Hydration-aware TREE reconciliation** (`src/apply.ts`): `CREATE_NODE` reuses an
  existing hydrated element (id already in the registry) and `INSERT_CHILD` skips
  when the child is already in the parent's container — so a resync'd full
  snapshot reconciles against the SSR DOM without clobbering or duplicating.
- **Runtime shells mirror the Rust renderer** (`src/elements.ts`): runtime-created
  nodes carry the same `pathland-*` classes/structure as SSR (a reinstated
  `BUTTON` gets `pathland-button`, `GAUGE` gets its `pathland-gauge` bar shell);
  a `ZStack` child is absolutely positioned (`position:absolute;inset:0`), and a
  `ProgressView` morphs between the determinate `<progress>` and the
  `pathland-spinner` div per `IS_INDETERMINATE`/`PROGRESS` — so a destination
  reinstated after a navigation swap keeps its styling. A table-driven **drift
  guard** (`test/elements.test.ts`) pins the canonical shells against the Rust
  renderer's SSR markup.
- **`META::RESYNC`** (`encodeResync`): the client requests a full snapshot after
  **reconnect** (never on first connect — the UI is already the SSR HTML).
- **Transport** (`src/transport.ts`): WebSocket connect, protocol-version
  negotiation (mismatch → reload), exponential-backoff reconnect, defensive
  per-batch try/catch.
- **Styling (Option A)**: the protocol stays renderer-agnostic; the SSR HTML
  carries Tailwind classes; runtime style deltas apply as inline style, literal
  colors inline, design tokens as CSS variables.
- **Tests** (`test/`, vitest + happy-dom): codec round-trips, TREE/STYLE/META
  application, design tokens (var mapping, dark scoping, px lengths, generative
  `space.N` refs), event byte layouts (incl. `NAVIGATE`), navigation (ROUTE →
  `onRoute`, transition animation, non-hinted slots), transport lifecycle
  (reconnect/resync), the logger (level gating, prefix), the opcode/event
  descriptors, the runtime-shell drift guard, and the ZStack/ProgressView
  behavior — 138 tests.

## Not implemented / gaps

- **`DESIGN_TOKEN` property references** cover the directly-mappable subset of
  the property catalog (colors, font size/weight/family, spacing/padding,
  corner radius, opacity, tint, border/shadow color, width/height). Compound
  accumulators that need concrete numbers (e.g. `BORDER_WIDTH`'s px suffix,
  shadow radius/x/y) remain literal-only.

- **`frameCount` gap detection** (P3): the client now resyncs on EVERY reconnect via
  `META::RESYNC`; P3's `frameCount` sequencing lets it resync only when a gap is
  actually detected.
- **Canonical keyCode table**: `KEY_DOWN`/`KEY_UP` use the DOM `event.keyCode`
  convention; the renderer-shared canonical set lives in the conformance
  vectors (grant WP2) — cross-check on landing.
- **Visual tokens** (`PICKER_STYLE`, `CONTROL_SIZE`, `MINIMUM_SCALE_FACTOR`,
  `COLOR_MULTIPLY`) are applied as `data-*` attributes; their *visual* variety is
  renderer-styling-owned (Tailwind/theme), not the protocol's job.
- The element factory's runtime shells are functional but not yet
  golden-tested against the Rust/Java renderers' SSR markup (drift guard TODO).
- `STATE`/`ROLE` map to ARIA attributes; exact bit semantics should be
  cross-checked against the conformance vectors when they land.

## Verified by

`npm test` (vitest, 87 tests) + `npm run typecheck` (strict TS). CI runs the
web-client job (typecheck + test + build + copy-to-demos drift check).