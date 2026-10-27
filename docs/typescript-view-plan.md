# Pathland TypeScript View + Server — Implementation Plan

**Status:** Implemented (phases 1–6); phase 7 is the next follow-up
**Scope:** TypeScript-only, server-driven UI
**Last Updated:** October 22, 2026

---

## Goal

Build a TypeScript authoring and server runtime for Pathland:

- Angular-like developer experience: class views, decorators, callable signals,
  computed/effects, forced-once `body(props)`, fluent modifiers.
- The application runs on the server. The server owns the retained tree,
  signals, state, and session lifecycle.
- The browser hydrates SSR HTML and applies deltas. It does **not** run the
  view engine in this phase.
- SSR is rendered by the existing Rust `pathland-render-html` crate compiled to
  WebAssembly.
- The browser client is the existing `lib/typescript` `@pathland/dom-renderer`.
- Rust remains the source of truth for wire constants/conformance and the HTML
  renderer. Rust is not the runtime engine.

## Locked decisions

| Decision | Choice |
|---|---|
| Language | TypeScript only |
| AssemblyScript | Out of scope; no `view-as`, no closure transform, no dual-target |
| DX | Angular-like: class views, `@view`, callable signals, `body(props)`, modifiers |
| View restrictions | None from AS: optional props, closures, unions, and full TS are allowed |
| Execution model | Server-owned app; browser is a renderer |
| Browser runtime | Deferred; same TS engine must stay worker-compatible later |
| SSR | Rust `pathland-render-html` via a small wasm wrapper |
| Client | Reuse `lib/typescript` `@pathland/dom-renderer` |
| Server host | Node first; framework-agnostic core; Bun/Deno later |
| State | In-memory now; Redis interface documented and deferred |
| Context | Small `AppContext` (state/platform/route); no full Angular DI |
| Structural reactivity | Later; the reference view is static |
| Wire format | Existing 16-byte opcodes + PLPL batches, protocol v1 |

### Why not the alternatives

- **AssemblyScript / wasm app runtime:** rejected for this phase. It forces an
  AS-native DSL and a closure transform, sacrificing the Angular-like DX.
- **Rust engine runtime:** rejected. A TS app on Node would need per-call FFI;
  a non-Node host would need an embedded JS runtime. The TS engine is the future
  browser/worker runtime anyway.
- **A separate TS HTML serializer:** rejected as the primary path. Reuse the
  Rust HTML renderer for parity with the existing renderers.

## Non-goals

- No AssemblyScript compiler, transform, or runtime.
- No TS/AS byte-identity work.
- No SharedArrayBuffer ring, transferable double buffering, or worker DOM
  patcher.
- No browser-side application runtime in this phase.
- No Rust `pathland-engine` runtime dependency.
- No full Angular DI container, router, or zone.js.
- No structural/conditional rendering in phase 1; plan it as a follow-up.

---

## Architecture

```text
TypeScript view + engine (server)
        │
        ├── PLPL snapshot / delta batches
        │        ├── Rust pathland-render-html (WASM) → SSR HTML
        │        └── WebSocket → existing @pathland/dom-renderer
        │
        └── raw EVENT batches ← WebSocket ← browser
```

### Server flow

1. **SSR request**
   - Create a throwaway session with a random state scope.
   - Instantiate the app, connect state, mount the root view once.
   - Emit a full snapshot batch.
   - Render it with the Rust HTML wasm renderer.
   - Inject `data-pathland-base` and the content-hashed client bundle.
   - Close the throwaway session.
2. **WebSocket connect**
   - Client URL: `<base>/ws?wid=<window-id>`.
   - Register a pending session. The first message is `META::ENVIRONMENT`.
   - Create the live session with the `wid` state scope.
   - Do not re-send the mount frame; the client already has the SSR HTML.
3. **Deltas**
   - Signal changes emit only the affected opcodes.
   - Send monotonic PLPL batches when the connection is open.
   - `META::RESYNC` re-emits the full tree.
   - `META::PING` → `META::PONG`.
4. **Events**
   - Client sends raw EVENT batches.
   - The server updates bound signals; the engine emits deltas.
5. **Persistence**
   - Load `StateStore` values before mount.
   - Save on `State` changes.
   - Memory store now; Redis later.

---

## Repository layout

```text
lib/typescript/
├── package.json          # @pathland/dom-renderer + pnpm workspace root scripts
├── pnpm-workspace.yaml
├── pnpm-lock.yaml
├── tsconfig.base.json
├── src/                  # existing browser DOM client
├── packages/
│   ├── protocol/         @pathland/protocol
│   ├── signals/          @pathland/signals
│   ├── state/            @pathland/state
│   ├── view/             @pathland/view
│   ├── engine/           @pathland/engine
│   ├── ssr/              @pathland/ssr
│   └── server/           @pathland/server
└── examples/
    ├── profile-card/
    └── music-player/

lib/rust/crates/pathland-render-html-wasm/   # new wasm wrapper
```

The existing `lib/typescript` package is both the browser client and the root
of the TypeScript server workspace. The browser client itself is not a
workspace member (it is the root package).

### Package dependency direction

```text
protocol
signals
  └── state
        └── view
              └── engine
                    └── server
                          ├── lib/typescript/examples/profile-card
                          └── lib/typescript/examples/music-player

protocol
  └── ssr
```

No circular dependencies. `engine` imports neither DOM nor Node APIs.

---

## Package contracts

### `@pathland/protocol`

```text
lib/typescript/packages/protocol/src/
  constants.ts   # generated from Rust pathland-ts-codegen
  batch.ts       # PLPL BatchWriter / BatchReader
  events.ts      # EVENT + META decode
  index.ts
```

```ts
export interface Opcode {
  category: number;
  command: number;
  flags: number;
  a: number;
  b: number;
  c: number;
}

export class BatchWriter {
  begin(): void;
  push(category: number, command: number, flags: number, a: number, b: number, c: number): void;
  string(value: string): number;  // arena offset
  end(sequence: number): Uint8Array;
}

export interface EventOpcode extends Opcode {
  text?: string;
}

export function decodeEvents(bytes: Uint8Array): EventOpcode[];
```

- Reuse the PLPL header/opcode/string layout already parsed by
  `lib/typescript/src/plpl.ts`.
- Use the existing implementation packing:
  `B = (valueType << 16) | propertyId`.
- F32 values are stored as raw IEEE-754 bits.
- Strings are length-prefixed UTF-8 in the batch string section.

### `@pathland/signals`

```text
lib/typescript/packages/signals/src/
  signal.ts
  computed.ts
  effect.ts
  input.ts
  index.ts
```

```ts
export interface ReadonlySignal<T> {
  (): T;
  get(): T;
}

export interface WritableSignal<T> extends ReadonlySignal<T> {
  set(value: T): void;
  update(fn: (current: T) => T): void;
}

export type MaybeSignal<T> = T | ReadonlySignal<T>;

export class InputSignal {
  static from<T>(
    value: MaybeSignal<T> | null | undefined,
    fallback?: T,
  ): ReadonlySignal<T>;
}
```

- Callable signals, Angular-style.
- Lazy memoized `computed`, synchronous `effect` flush, equality suppression,
  error caching, cleanup handles.
- `InputSignal.from` wraps raw values in a constant signal; existing signals
  pass through.
- The reference view's optional `status` and closure syntax are fully allowed.

### `@pathland/state`

```text
lib/typescript/packages/state/src/
  store.ts
  state.ts
  index.ts
```

```ts
export interface StateStore {
  load(scope: string): Promise<Record<string, unknown>> | Record<string, unknown>;
  save(scope: string, key: string, value: unknown): void | Promise<void>;
  delete(scope: string, key: string): void | Promise<void>;
}

export class MemoryStateStore implements StateStore {}

export class State<T> {
  readonly key: string;
  readonly signal: WritableSignal<T>;
}

export class PersistentState {
  connect(target: object): void; // binds State<T> fields by property name
  state<T>(key: string, initial: T): WritableSignal<T>;
}
```

- `State<T>` fields are auto-connected by property name when
  `PersistentState.connect` runs before mount.
- The loaded value wins over the initializer.
- Writes save through the store; the key is scoped by the session.
- Redis is not implemented now; see [State and Redis](#state-and-redis).

### `@pathland/view`

```text
lib/typescript/packages/view/src/
  decorator.ts    # @view marker
  element.ts      # ViewElement model
  primitives.ts   # VStack/HStack/ZStack/Text/Image/TextField/Button
  modifiers.ts    # padding/cornerRadius/background/fontSize/foregroundStyle
  context.ts      # AppContext + useAppContext()
  index.ts
```

```ts
export function view<T extends object>(target: T): T;

export type ViewChildren = ViewElement[] | (() => ViewElement[]);

export function VStack(props: VStackProps, children: ViewChildren): ViewElement;
export function HStack(props: HStackProps, children: ViewChildren): ViewElement;
export function Text(props: TextProps): ViewElement;
export function Image(props: ImageProps): ViewElement;
export function TextField(props: TextFieldProps): ViewElement;
export function Button(props: ButtonProps): ViewElement;

export interface Element {
  modifiers(...mods: ViewModifier[]): ViewElement;
}

export function useAppContext(): AppContext;
```

- `@view` is a legacy TypeScript decorator marker; mounting is explicit.
- `body(props)` is called once per session.
- Children functions are evaluated once during mount.
- `AppContext` is available synchronously during `body`/mount via a stack.
- `AppContext` contains `state`, `platform`, and `route`; no full injector.

### `@pathland/engine`

```text
lib/typescript/packages/engine/src/
  node.ts       # retained node types
  sink.ts       # FrameOpcodeSink backed by BatchWriter
  bindings.ts   # text/property bindings + effect cleanup
  emitter.ts    # mount / snapshot / delta / destroy
  input.ts      # RenderResult + InputDispatcher
  index.ts
```

```ts
export interface RenderResult {
  rootId: number;
  textInputs: Map<number, WritableSignal<string>>;
  valueInputs: Map<number, WritableSignal<number>>;
  tapActions: Map<number, () => void>;
}

export class Emitter {
  mount(root: ViewElement, ctx: AppContext): RenderResult;
  renderFull(): void;
  destroy(): void;
}

export class InputDispatcher {
  constructor(result: RenderResult, route: WritableSignal<string>);
  dispatch(events: EventOpcode[]): void;
}
```

Behavior:

- Deterministic node IDs in pre-order, starting at 1.
- Initial mount emits the full tree.
- Signal changes emit only `SET_TEXT` / `SET_PROPERTY`.
- An unchanged signal emits zero opcodes.
- `TextField` binds a `WritableSignal<string>` and registers in
  `RenderResult.textInputs`.
- `Button`/tap handlers register in `tapActions`.
- No structural re-evaluation in phase 1.
- Canonical emission order:
  1. `CREATE_NODE`
  2. config properties
  3. modifiers in source order
  4. text content
  5. each child subtree, then `INSERT_CHILD`

Property mapping for the reference:

| DSL | Protocol |
|---|---|
| `VStack` | `VSTACK` (0x10) |
| `HStack` | `HSTACK` (0x11) |
| `Text` | `TEXT` (0x01) |
| `Image` | `IMAGE` (0x02) |
| `TextField` | `TEXT_FIELD` (0x21) |
| `spacing` | `SPACING` 0x0001, F32 |
| `alignment` | `ALIGNMENT` 0x0002, F32 enum |
| `padding` | `PADDING` 0x1011, F32 |
| `cornerRadius` | `BORDER_RADIUS` 0x1005, F32 |
| `background` | `BACKGROUND_COLOR` 0x1001, COLOR |
| `fontSize` | `FONT_SIZE` 0x1007, F32 |
| `foregroundStyle` | `COLOR` 0x100A, COLOR |
| `Image.source` | `IMAGE_SOURCE` 0x1002, STRING |
| `Text.text` | `SET_TEXT`, arena ref |
| `TextField.placeholder` | `PROMPT` 0x200B, STRING |
| `TextField.text` | `SET_TEXT` + `BINDING_ID` 0x2017 (matches the Rust/Java engines and DOM client) |

### `@pathland/ssr`

```text
lib/typescript/packages/ssr/src/
  wasm.ts
  page.ts
  assets.ts
  index.ts
```

```ts
export interface SsrRenderer {
  renderDocument(batch: Uint8Array, rootId: number): string;
  renderFragment(batch: Uint8Array, rootId: number): string;
}

export async function loadSsrRenderer(
  wasm: Uint8Array | WebAssembly.Module,
): Promise<SsrRenderer>;

export interface PageOptions {
  base: string;
  bundleName: string;
  serviceWorker?: boolean;
  ssrScoped?: boolean;
}

export function renderPage(html: string, options: PageOptions): string;
```

- Load the wasm once at server startup.
- `renderPage` injects `data-pathland-base` and the client script, mirroring
  `PathlandSession.renderHtml` in the Java server core.
- Bundle names are content-hashed; resolve via
  `lib/typescript/scripts/copy-to-demos.mjs` semantics or a local manifest.

### `@pathland/server`

```text
lib/typescript/packages/server/src/
  app.ts
  connection.ts
  session.ts
  registry.ts
  batcher.ts
  queue.ts
  ws.ts
  express.ts
  nest.ts
  index.ts
```

```ts
export interface PathlandApp {
  newRoot(ctx: AppContext): ViewElement;
}

export interface PathlandConnection {
  send(bytes: Uint8Array): void;
  isOpen(): boolean;
}

export class PathlandSession {
  constructor(options: {
    sessionId: string;
    app: PathlandApp;
    store: StateStore;
    env: EnvironmentData;
    base: string;
    stateScope: string;
    ssrRenderer: SsrRenderer;
  });

  renderHtml(options?: {
    serviceWorker?: boolean;
    ssrScoped?: boolean;
  }): Promise<string>;

  connect(connection: PathlandConnection): void;
  applyEnvironment(env: EnvironmentData): void;
  dispatch(bytes: Uint8Array): void;
  resync(): void;
  close(): void;
}

export class PathlandRegistry {
  renderHtml(route: string, windowId?: string): Promise<string>;
  open(uiId: string, windowId: string, connection: PathlandConnection): void;
  environment(uiId: string, env: EnvironmentData): Promise<void>;
  dispatch(uiId: string, bytes: Uint8Array): void;
  resync(uiId: string): void;
  close(uiId: string): void;
}
```

Port the behavior of the Java server core:

- `lib/java/pathland-server-core/src/main/java/dev/pathland/server/PathlandSession.java`
- `PathlandRegistry.java`
- `PathlandAssets.java`
- `PathlandConnection.java`

Important semantics:

- The mount frame is emitted in the session constructor but not sent.
- Live deltas are sent only after `connect`.
- `resync()` re-emits the full tree.
- Sessions are created lazily on the first `META::ENVIRONMENT`.
- The SSR path uses a throwaway session with a random scope.
- The live session uses the client's `wid` scope.
- Per-session operations are serialized with a queue; signals remain
  single-threaded.

---

## Rust changes

Add:

```text
lib/rust/crates/pathland-render-html-wasm/
```

- Depends on `pathland-render-html` and `pathland-core-transport`.
- `publish = false`.
- `crate-type = ["cdylib"]`.
- Exports:

```rust
pathland_wasm_alloc(len: u32) -> *mut u8
pathland_wasm_free(ptr: *mut u8, len: u32)
pathland_wasm_render(
  batch: *const u8,
  len: u32,
  root: u32,
  fragment: u8,
  out_len: *mut u32,
) -> *const u8
```

- Reuse `decode_frame` and `HtmlRenderer::render_document/render_fragment`.
- Build for `wasm32-unknown-unknown`.
- Copy the output to `lib/typescript/packages/ssr/wasm/pathland_render_html.wasm`.
- Add a build script and CI step.
- Add a TS wasm smoke test.

Also extend `pathland-ts-codegen` to emit
`lib/typescript/packages/protocol/src/constants.ts`, and include it in the existing no-drift
CI check.

No wire-format changes.

---

## SSR and hydration

- The Rust renderer returns a complete HTML document.
- `renderPage` injects:
  - `data-pathland-base="<base>"` on `<html>`;
  - `<script src="<base>/<bundleName>" defer></script>` before `</body>`.
- Phase 1 omits the service worker; add it later.
- Reuse the existing client bundle:
  `lib/typescript/dist/pathland-dom-renderer.js`.
- Add a copy script that writes a content-hashed bundle and a
  `dom-renderer.current` manifest into the example's static asset directory.
- The server resolves the current bundle name from the manifest.
- Client behavior already provided by `lib/typescript`:
  - hydrate `data-pathland-id` nodes;
  - `wid` in `sessionStorage`, sent only on the WS URL;
  - `META::ENVIRONMENT` as the first WS message;
  - `META::RESYNC` on reload/reconnect;
  - heartbeat `META::PING`/`PONG`;
  - sequence-gap detection;
  - raw event batches.

The server must implement those META messages and the sequence numbering.

---

## State and Redis

Phase 1:

- `MemoryStateStore`.
- `PersistentState` loads before mount and saves on change.
- Scope: `"/" + wid` for the root app; mount-qualified for multi-app later.

Redis (deferred, documented now):

- Package: `@pathland/state-redis`.
- Keys: `pathland:<mount>:<wid>:<state-key>`.
- Values: JSON or a typed encoding.
- `StateStore.load` is awaited before the live session mount.
- The SSR throwaway session does not load persisted state unless a `wid` is
  supplied.
- Document TTL, serialization, and failure behavior.

---

## Testing

### Unit

- `protocol`: batch writer/reader round-trip, known bytes, event decode,
  generated constants no-drift.
- `signals`: callable reads, dependency tracking, equality, computed
  memoization, synchronous effects, cleanup.
- `state`: memory store, scope keys, load-before-mount, save-on-change.
- `engine`: node IDs, property order, text binding, zero-op unchanged update,
  snapshot vs delta.
- `ssr`: wasm load, document/fragment render, escaped text, `data-pathland-id`.

### Integration

- Express SSR response contains the expected HTML, base, and script.
- WS connect sends `META::ENVIRONMENT`; the session is created with the `wid`
  scope.
- `META::RESYNC` returns a full snapshot.
- `TEXT_CHANGED` updates the `TextField` signal and emits the expected delta.
- `META::PING` receives `META::PONG`.
- Sequence numbers are monotonic.

### End-to-end

- Playwright against `lib/typescript/examples/profile-card`:
  - load SSR HTML;
  - hydrate;
  - type into the `TextField`;
  - server updates the bound signal;
  - a `Text` echo updates via delta;
  - no full-tree re-emit on each keystroke.

### CI

- New `typescript-server` job:
  - pnpm install;
  - build/typecheck/test all packages;
  - build the Rust wasm wrapper;
  - build the existing `lib/typescript` client bundle;
  - run Playwright.
- Existing Rust job:
  - build/test the wasm wrapper natively;
  - upload the wasm artifact for the TS job.
- Generated constants no-drift check.

Commands (target):

```bash
cd lib/typescript
corepack pnpm install
corepack pnpm server:build:wasm      # not committed; CI downloads/builds it
corepack pnpm -r typecheck
corepack pnpm -r test
corepack pnpm server:build:client
corepack pnpm server:copy:client
corepack pnpm --filter @pathland/example-profile-card test:e2e
```

---

## Implementation phases

### Phase 1 — Foundation

- [x] Add `pnpm-workspace.yaml`, root `package.json`, `tsconfig.base.json`.
- [x] Create `@pathland/protocol`; extend `pathland-ts-codegen` and add the
      no-drift check.
- [x] Implement `@pathland/signals` with callable signals, computed, effect,
      `InputSignal`.
- [x] Unit tests for signals and the protocol codec.

### Phase 2 — View and engine

- [x] Implement `@pathland/view` elements, primitives, modifiers, decorator,
      `AppContext`.
- [x] Implement `@pathland/engine` retained tree, sink, bindings, emitter,
      `RenderResult`, `InputDispatcher`.
- [x] Mount the reference `UserProfileCard` and assert the snapshot opcodes.
- [x] Assert text signal updates emit only `SET_TEXT`.

### Phase 3 — State

- [x] Implement `@pathland/state` `StateStore`, `MemoryStateStore`,
      `State`, `PersistentState`.
- [x] Wire load-before-mount and save-on-change.
- [x] Tests for field auto-connection and scope isolation.

### Phase 4 — Server core and Express

- [x] Implement `PathlandApp`, `PathlandConnection`, `PathlandSession`,
      `PathlandRegistry`, `DeltaBatcher`, per-session queue.
- [x] Implement WS attach: environment, dispatch, resync, ping/pong.
- [x] Implement `pathlandExpress` with SSR and static asset serving.
- [x] Event routing for `TextField`.
- [x] Integration tests.

### Phase 5 — Rust SSR

- [x] Add `pathland-render-html-wasm` and its build/copy script (wasm output is not committed).
- [x] Implement `@pathland/ssr` wasm loader and page shell.
- [x] Replace the TS fallback (if any) with Rust HTML.
- [x] Add wasm smoke tests and HTML snapshots.

### Phase 6 — NestJS, demo, docs, CI

- [x] Add `@PathlandView` + `PathlandInterceptor`.
- [x] Build `lib/typescript/examples/profile-card` with the reference view and a query echo.
- [x] Copy/hash the client bundle; serve it from the example.
- [x] Playwright e2e.
- [x] Add `status.md` for each package.
- [x] Update `AGENTS.md`/README with the new workspace and commands.
- [x] Add `docs/state-redis.md` design.
- [x] Add the CI job.

### Later

- Redis `StateStore`.
- Service worker.
- Multi-app mounts.
- Router/navigation.
- Structural/conditional rendering.
- TS browser/worker runtime using the same engine.

---

## Acceptance criteria

1. `UserProfileCard.ts` compiles with only `public` → `export` changed.
2. `pnpm -r typecheck` and `pnpm -r test` pass.
3. SSR returns the reference card with:
   - `data-pathland-id` nodes;
   - avatar image;
   - username/status text;
   - search input;
   - background, radius, padding, font sizes, colors.
4. The browser hydrates the SSR DOM without duplicating nodes.
5. Typing in the search input updates the server-side writable signal.
6. The server emits a delta; the DOM updates without a full tree re-emit.
7. Reloading the same tab requests `META::RESYNC` and restores the session
   state.
8. `META::PING` gets `META::PONG`.
9. The Rust HTML renderer is used for SSR; no TS HTML serializer is required.
10. No AssemblyScript artifacts or dependencies.

---

## Reference view

The only required source change is the invalid top-level `public` modifier:

```diff
-public interface UserProfileCardProps {
+export interface UserProfileCardProps {
```

Full reference (unchanged otherwise):

```ts
import { view, InputSignal } from "@pathland/view";
import { Text, Image, HStack, VStack, TextField } from "@pathland/view/primitives";
import { padding, cornerRadius, background, fontSize, foregroundStyle } from "@pathland/view/modifiers";
import { MaybeSignal, WritableSignal } from "@pathland/signals";

export interface UserProfileCardProps {
  username: MaybeSignal<string>;
  status?: MaybeSignal<string>;
  searchQuery: WritableSignal<string>;
}

@view
export class UserProfileCard {
  body(props: UserProfileCardProps) {
    const usernameSignal = InputSignal.from(props.username);
    const statusSignal = InputSignal.from(props.status, "Offline");

    return VStack({ spacing: 12 }, () => [
      HStack({ spacing: 16 }, () => [
        Image({ source: "avatar.png" }).modifiers(
          cornerRadius(24),
          padding(4),
        ),

        VStack({ alignment: "leading", spacing: 4 }, () => [
          Text({ text: usernameSignal }).modifiers(
            fontSize(18),
            foregroundStyle("#111827"),
          ),

          Text({ text: statusSignal }).modifiers(
            fontSize(12),
            foregroundStyle("#10B981"),
          ),
        ]),
      ]),

      TextField({
        placeholder: "Search user...",
        text: props.searchQuery,
      }),
    ]).modifiers(
      background("#FFFFFF"),
      cornerRadius(12),
      padding(16),
    );
  }
}
```

---

## Risks and notes

- **Rust wasm wrapper:** keep the ABI small and test it from Node; avoid
  caching `memory.buffer` across renders.
- **Client protocol details:** `wid`, `RESYNC`, `PING`/`PONG`, and sequence
  numbers are already implemented by `lib/typescript`; port the Java server
  behavior exactly.
- **Duplicate PLPL codec:** the server and client each have a codec. Add
  cross-tests; consolidate later if practical.
- **Async state:** Redis will make session creation asynchronous. The
  per-session queue must hold environment/events until mount completes.
- **Session lifecycle:** add a TTL/idle cleanup to avoid leaking server-side
  trees and signals.
- **Static tree only:** conditional rendering and structural reconciliation
  are follow-ups. Warn against relying on state-dependent structure in SSR
  until RESYNC/reconcile is fully tested.
- **Determinism:** use `AppContext.platform.now()`/random when SSR and the live
  session must agree; avoid direct `Date.now()`/`Math.random()` in `body`.
- **No AS:** do not introduce `view-as`, `asconfig`, the closure transform, or
  AS-compatible view restrictions.
- **No Rust engine runtime:** do not add a native addon or per-opcode FFI.

---

## Phase 7 — Structural / conditional rendering (next)

Phase 1–6 keep the retained tree static after mount. The next phase adds
signal-selected structural slots:

- A slot declares a selector signal and a `() => ViewElement | null` content
  function.
- The engine reads the selector as a tracked dependency, re-evaluates the content
  untracked, and diffs the old/new subtree.
- Only `TREE` deltas are emitted (`CREATE_NODE`, `DELETE_NODE`, `INSERT_CHILD`,
  `REMOVE_CHILD`, `MOVE_CHILD`); an identical recompute emits zero opcodes.
- Bindings and input routing for entering/leaving nodes are registered/destroyed
  with the subtree.
- Nested structural slots and stable ids where possible.

The wasm wrapper's output-buffer rule (copy before the next render) is already an
invariant of `@pathland/ssr/wasm.ts` and is covered by a consecutive-render test.
