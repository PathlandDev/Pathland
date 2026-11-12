# Pathland Project - AI Agent Guide

This document provides essential context for AI agents (or human contributors) working on the **Pathland** protocol. It captures the project's direction, architectural decisions, and implementation details to ensure consistency.

**Last Updated**: November 12, 2026

## Architecture decisions and document responsibilities

- **Current contracts:** `spec/` defines protocol and authoring requirements.
- **Rationale and history:** [docs/decisions/README.md](./docs/decisions/README.md)
  indexes the architectural decision records (ADRs).
- **Implementation coverage:** each project's `status.md` remains authoritative.
- **Agent workflow:** this guide retains essential guardrails and build/change
  instructions; consult the relevant spec and ADR before changing architecture.

For a new significant decision, propose an ADR using
`docs/decisions/template.md`; do not accept it on the maintainer's behalf.
When a decision changes, supersede its ADR and update the current spec and links
in the same change. ADRs explain requirements; they must not hide additional
mandatory behavior outside the specs. Do not infer historical rationale from code.

Follow [architecture traceability and drift](./docs/decisions/traceability.md):
add or update precise spec references when touching non-obvious governed code,
link tests to the same requirement, and do not rewrite a contract merely to
match drifted code. Record unresolved findings with evidence and update affected
`status.md` gaps. Run `python3 scripts/check-references.py` after reference changes.

For substantial architecture-sensitive tasks, use the repository-local skills
in `.agents/skills/`: `pathland-orient` before coding, `pathland-decision` for
ADR proposals, `pathland-review` for architectural review, and `pathland-maintain`
for drift/reference audits. See [skill usage and boundaries](./docs/decisions/skills.md).
Orientation, review, and maintenance are read/propose-only; they do not authorize
repairs or decision acceptance. New ADRs remain Proposed until maintainer approval.

---

## Architecture: Rust Opcode Engine

> The canonical core is a **Rust engine** that emits the application's
> **declarative view structure** — VStack, HStack, Text, spacing, padding,
> alignment — as a fixed **16-byte opcode ring buffer** into shared linear
> memory, consumed by host renderers. View components and modifiers are written
> in Rust with a SwiftUI-like API, and in Kotlin with the hand-written
> **`dev.pathland.view.dsl` DSL** (reusable JVM library, Java 17+); both drive the
> engine through the flat **native C ABI** (`pathland-view-native`) over a
> zero-copy shared ring on desktop. Java hosts mount Kotlin views through
> `PathlandApp`.

### The 16-byte Opcode Engine

- Every opcode is exactly **16 bytes**: `Category (1B) | Command ID (1B) | Flags (2B) | A (4B) | B (4B) | C (4B)`, `#[repr(C, packed)]`, little-endian.
- A 64-byte cache line holds exactly **4 opcodes** → L1-cache-friendly.
- Opcodes live in a **ring buffer** in linear memory; the guest writes at `writeCursor`, the host reads from `readCursor`; frame boundaries via a monotonic `frameCount` in the header block.
- **Both directions are binary**: a second **event ring** (host → guest) carries `EVENT`-category opcodes. The renderer writes raw inputs at `eventWriteCursor`; the guest drains them at `eventReadCursor`. Events are 16-byte opcodes through the engine, never a side-channel callback.
- **Variable-length data** (strings, token paths) lives in a bump **arena**; opcodes reference entries by offset (`[u32 byteLength][bytes...]`), so every opcode stays 16 bytes. A second host-owned **event arena** mirrors this in the host → guest direction (shared-memory event strings, e.g. `TEXT_CHANGED`); over the network, host → guest strings ride the batch's string section.
- **Window metadata** (`META::SET_WINDOW_METADATA`, field family `TITLE`/`DESCRIPTION`/`LOCALE`) carries the hosting surface's presentation metadata; renderers map it to their own chrome (browser `<title>`/meta description/`<html lang>`, GTK/Qt window title). It is root-scoped and signal-backed, so a route title is a computed over the router. Web-only artifacts (canonical/robots/sitemap) stay host-owned.
- Spec: **`spec/OPCODE.md`** (primary) + **`spec/CONFORMANCE.md`** (golden vectors). Companion catalogs: **`spec/PRIMITIVES.md`** (views), **`spec/MODIFIERS.md`** (modifiers/properties), **`spec/EVENTS.md`** (events), **`spec/LAYOUT.md`** (frame & layout allocation contract) — draft IDs are allocated there spec-first.

### Declarative, not positioned (NON-NEGOTIABLE)

> **The engine describes WHAT the UI is — VStack, HStack, Text, spacing,
> padding, alignment — never WHERE it is. It does not compute layout and does
> not emit rects.**

- The engine emits **declarative structure** (`TREE` create/delete/insert/remove/move) + **constraint properties** (`PARAMETER` spacing/padding/alignment/FILL/HUG hints).
- **Native elements everywhere**: each platform's renderer maps the opcode stream onto that platform's native elements — GTK4 widgets on desktop, DOM elements in the browser, HTML server-side — and those native elements lay themselves out. Never a generic canvas unless a platform has no native equivalent.
- **Reactive emission**: emission is diff-based. Only the nodes that changed emit opcodes (property diffs via `SET_PROPERTY`/`SET_TEXT`, structural diffs via `TREE` opcodes). An unchanged tree emits **zero** opcodes. Signals in the application mark the tree dirty; the engine reconciles.
- **Zero dynamic heap allocations** during steady-state emission (proven by test).

### no_std / wasm32 direction guard (NON-NEGOTIABLE)

Rationale: [ADR-0007](./docs/decisions/0007-portable-protocol-core.md).

`pathland-core` and `pathland-view` must remain `#![no_std]` + `alloc` and
compile for `wasm32-unknown-unknown`. This is the guarantee that the protocol
core can still target a browser / embedded / remote projection later (goal #15,
Quarkus SSR + WebSocket). The desktop path (`pathland-view-native`) is the only
consumer today, but the core must not become desktop-only. Verify with
`lib/rust/check-wasm.sh` (i.e. `cargo build -p pathland-core -p pathland-view
--target wasm32-unknown-unknown`).

### Reactive signals (Angular-style)

`pathland-core::signal` provides writable signals that live in the engine. A
node binds its text (`Node::text_binding`), a property
(`Node::property_bindings`) or a **string property** (`Node::string_bindings`,
e.g. a media controller's `MEDIA_SOURCE`) to a `SignalId`; during emit the engine
resolves the binding and records the dependency, and `Engine::set_signal`
re-emits **only the bound nodes** (`SET_TEXT` / `SET_PROPERTY`) — no full tree
walk. Signal values
are one of `F32`/`U32`/`Bool`/`Enum`/`Str` (packed to the wire's `u32` value or
arena string). Foreign hosts reach signals via the flat C ABI
(`pathland_native_signal_create_*` / `signal_set_*` / `node_bind_text` /
`node_bind_property`) and the Kotlin/JVM `Signal` type. Binding then calling `emit`
records the dependency; a subsequent `set` is a partial emit. (Computed/derived
signals + effects are the planned next step.)

### Rust Workspace (`lib/rust/`)

```
# ── The three elements ────────────────────────────────────────────────────
crates/pathland-view/     # RETAINED UI — SwiftUI-style view DSL: VStack/HStack/Text/Button +
                          #   decoupled modifiers (spacing/padding/fontSize/color/background)
                          #   + Video (a MediaController's surface) + attach() (no_std)
crates/pathland-core/     # PROTOCOL — 16-byte opcode, ring buffer, arena, constants, events,
                          #   memory layout, Guest/Host/Frame surface (no_std, zero-alloc).
                          #   Pure protocol; the emitter moved out to pathland-engine.
crates/pathland-engine/   # EMITTER — retained tree types (Node/Component) + the diff-based
                          #   reactive emitter + signals + MediaController (spec/MEDIA.md).
crates/pathland-core-transport/ # TRANSPORT — shared-memory ring owner (RingTransport) +
                          #   network batch encode/decode + batching policy (std)
crates/pathland-render-core/ # RENDERER CORE (shared, toolkit-free) — opcode-frame decode into a
                          #   native-element description (RenderTree/HostNode), design-token
                          #   tables + resolution, concrete Tier-1 defaults. No GTK/Qt: every
                          #   renderer shares one tested decode path.
crates/pathland-render-gtk/ # RENDERER — maps opcode frames onto native GTK widgets
                          #   incrementally; the only crate that touches GTK/glib/pango.
                          #   Fixed-size images scale to the logical-points box (gdk-pixbuf,
                          #   CONTENT_MODE fit/fill). App-driven media via a direct GStreamer
                          #   playbin (GTK4's own media backend is compiled out of some
                          #   builds). Exposes pathland_gtk_run[_ring]/
                          #   pathland_gtk_set_asset_root for Java (JNA) hosts.
crates/pathland-render-qt/ # RENDERER (hybrid Rust + C++) — maps opcode frames onto a Qt Quick
                          #   scene graph (Row/Column/Text + Qt Quick Controls). Rust owns the
                          #   shared decode core + a sent-state delta diff + the Pump/C ABI;
                          #   a thin C++ layer (src/qt/, built via cc + moc) owns the Qt shell
                          #   and applies the delta command batch to QML items (incl. media via
                          #   QtMultimedia). The crate needs Qt6 dev libs (Homebrew: qtbase
                          #   qtdeclarative qtmultimedia) to build/test; its headless tests use
                          #   QT_QPA_PLATFORM=offscreen.
crates/pathland-markdown/ # PARSER — shared inline Markdown (`no_std` + alloc) into
                          #   `pathland_core::AttributedString` (bold/italic/code/strike/links).
crates/pathland-markdown-capi/ # C ABI (cdylib `libpathland_markdown`) for non-JVM hosts
                          #   (the JVM uses the pure-Java parser in pathland-core).
crates/pathland-render-html/ # RENDERER — maps opcode frames onto declarative HTML (flex
                           #   stacks, spans, buttons) as a pure function of the stream; the
                           #   canonical cross-language implementation. The JVM uses the
                           #   pure-Java port in lib/java/pathland-render-html.
# ── Web client (DOM renderer, lib/typescript) ──────────────────────────────
lib/typescript/                        # DOM RENDERER — @pathland/dom-renderer: a small
                           #   vanilla-TypeScript hydration client (no runtime deps):
                           #   hydrates the SSR HTML by data-pathland-id, decodes
                           #   self-contained PLPL batches, applies PARAMETER + TREE deltas
                           #   in place, sends raw-input events over /ws. The server
                           #   NEVER replays the full tree on connect (the client already
                           #   has it from the HTML); a full snapshot is sent only on an
                           #   explicit META::RESYNC (client requests it after a reconnect
                           #   or a same-tab reload — the per-window id lives only in
                           #   sessionStorage + the WS URL, never in the page URL, so SSR
                           #   renders defaults and the client re-syncs its saved state).
                           #   Built to dist/pathland-dom-renderer.js and shipped inside the
                           #   Spring Boot starter + Quarkus extension jars (auto-served).
# ── Retained-UI projection (host/driver surface) ──────────────────────────
crates/pathland-view-native/ # NATIVE C-ABI shim + NativeHost: flat world over a zero-copy
                          #   shared-memory ring, for Swift/Java/other native hosts
                          #   (cdylib name stays "pathland_native")
crates/pathland-core-capi/ # MINIMAL RING C ABI — the SPSC ring + 16-byte opcode engine as
                          #   libpathland_core: create/destroy, pathland_ring_buffer_push,
                          #   arena alloc, frame boundaries, zero-copy read, event drain/send.
                          #   Consumed by the Kotlin DSL via JNA (dev.pathland.nativecore).
# ── Demo ───────────────────────────────────────────────────────────────────
crates/pathland-render-gtk-demo/ # Rust GTK4 demo: authors the DSL, emits into the ring, renders
                          #   through pathland-render-gtk. Uses NO GTK APIs directly.
crates/pathland-render-qt-demo/ # Rust Qt Quick demo: authors the DSL, emits into the ring, renders
                          #   through pathland-render-qt. Uses NO Qt APIs directly.
```

### JVM libraries (`lib/java/`, Kotlin-first)

Module-boundary rationale: [ADR-0008](./docs/decisions/0008-pure-jvm-server-libraries.md).

The reusable JVM library set (`dev.pathland`, Maven reactor, Java 17+) pairs a
pure-Java opcode/wire core with a Kotlin engine and DSL; the Kotlin DSL serves
Spring Boot, Quarkus, and desktop apps:

```
lib/java/
  pathland-core/        # dev.pathland.core(.protocol/.transport/.text/.markdown) — the
                        #   pure-Java opcode/wire core: 16-byte opcode protocol, PLPL frame
                        #   codec, Event/EnvironmentData, OpcodeSink + FrameOpcodeSink,
                        #   AttributedString model, and the pure-Java inline-Markdown
                        #   parser. No Kotlin source, no JNA, no native dependency.
  pathland-view-core/   # dev.pathland.view.core(.signal/.state/.router) +
                        #   dev.pathland.view.engine + dev.pathland.view.dsl — the shared
                        #   Kotlin JVM engine AND the sole authoring DSL: View SPI/value
                        #   vocabulary, Environment/Theme/MediaController,
                        #   signals/computed/effects, scoped/global topic subscriptions
                        #   (`Topic<T,S>` + `topic(...)`), fine-grained retained-tree diff
                        #   emitter, router, cross-platform state, and the SwiftUI-shaped
                        #   builders/modifiers (`Text(...)` parses inline Markdown by
                        #   default; `markdown = false` opts out). Depends on pathland-core.
                        #   Server runtimes consume this only (no JNA).
  pathland-native-core/ # dev.pathland.nativecore — opt-in JNA interop for native desktop
                        #   hosts: the lazy PathlandCore binding to libpathland_core + the
                        #   zero-copy RingOpcodeSink. Kept separate so server runtimes and
                        #   GraalVM native-image builds never carry JNA.
  pathland-render-html/ # dev.pathland.render.html — the PURE-JAVA HTML renderer:
                        #   a behavioural port of the Rust crate (no JNA, no cdylib), so a
                        #   Maven dependency runs on every platform and under GraalVM
                        #   native-image. `GoldenConformanceTest` keeps its output
                        #   byte-identical to the canonical Rust SSR fixtures.
  pathland-server-core/ # dev.pathland.server — transport-agnostic server runtime:
                        #   PathlandApp (root-view factory), PathlandConnection (send seam),
                        #   PathlandSession (per-session SSR + live deltas + environment),
                        #   PathlandRegistry (1:1 session lifecycle, actor thread),
                        #   PathlandSignalHub (topic fan-out / external UI updates),
                        #   PathlandTelemetry (observability seam; NOOP default). Also ships
                        #   dev.pathland.observability (Micrometer + opt-in OTel adapters)
                        #   and dev.pathland.server.test (WsSession/MultiSessionProbe WS
                        #   harness). No Redis/Lettuce (in-memory state by default).
  pathland-state-redis/ # dev.pathland.state.redis — opt-in Redis StateStore (Lettuce) +
                        #   RedisStateStores.redisOrFallback + its native-image metadata.
                        #   Kept out of server-core so in-memory native builds (the
                        #   Quarkus demo) never carry Lettuce.
  pathland-demo-views-kotlin/ # Kotlin-owned demo views: app-driven MusicPlayerView and its
                        #   library/sidebar/player-bar subviews, the AdView origin app, plus
                        #   DemoTheme and the single embedded copy of the audio/cover/lyric
                        #   assets (shared by the desktop and web hosts)
  pathland-spring-boot-starter/ # com.pathland.spring — Spring Boot auto-config: add the dep +
                        #   a PathlandApp bean → SSR at any path + /ws deltas + state
  pathland-quarkus/     # Quarkus extension (runtime): dev.pathland.quarkus →
                        #   SSR + /ws + sessions/state + the Kotlin DSL/quarkus-kotlin.
  pathland-quarkus-deployment/ # build-time module for pathland-quarkus (incl. Kotlin DSL)
  pathland-desktop-demo/ # dev.pathland.demo.{gtk,qt} — JNA desktop demo: Kotlin
                        #   MusicPlayerView under the GTK4 or Qt Quick renderer (shared
                        #   headless RingEventReader)
  pathland-quarkus-demo/# Quarkus SSR + WebSocket demo (com.pathland.demo.quarkus; Java host +
                        #   Kotlin views, plus a Kotlin-only view mounted at /kotlin)
  pathland-spring-boot-demo/ # Spring Boot SSR + WebSocket demo (com.pathland.demo.spring)
```

- Test: `cd lib/rust && cargo test` (runs all crates).
- Build/test the JVM libraries: `cd lib/java && mvn install`. The JVM path is
  pure JVM (the opcode/wire core lives in `pathland-core`, the engine in
  `pathland-view-core`; the HTML renderer is a port), so no Rust build is required.
  `mvn -P jvm-only test` runs the libraries and server starters without the
  demo modules. Rust is only needed for the native desktop demos
  (`scripts/lib.sh` builds their dylibs) and for regenerating shared artifacts
  (protocol manifest, engine traces, HTML golden fixtures, TS constants);
  `mvn -P refresh-ide validate` refreshes the design assets and DOM bundle.
- Run the GTK demo (native, zero-copy shared ring): `cd lib/rust && cargo run -p pathland-render-gtk-demo`.
- Run the Qt Quick demo (native, zero-copy shared ring): `cd lib/rust && cargo run -p pathland-render-qt-demo`
  (needs Qt6 dev libs — Homebrew: `brew install qtbase qtdeclarative`; on macOS point `QMAKE`
  at `$(brew --prefix qtbase)/bin/qmake` if it isn't on PATH, see `scripts/run-rust-qt-demo.sh`).
- Run the Kotlin Qt Quick demo (JNA, shared ring, Kotlin MusicPlayerView + app-driven media):
  `scripts/run-java-qt-demo.sh` (macOS hands Qt the main thread via `-XstartOnFirstThread`).
- Run the Java GTK4 demo (JNA, shared ring): `scripts/run-java-gtk-demo.sh`.
- Run the Quarkus Java demo (SSR + WebSocket deltas, dev mode):
  `cd lib/java/pathland-quarkus-demo && mvn quarkus:dev` (or `mvn package && java -jar target/quarkus-app/quarkus-run.jar`).
  Needs JDK 17+ (the whole stack runs on every LTS from 17) and Quarkus ≥ 3.18.
- Build/run the Quarkus demo as a native executable (GraalVM/Mandrel 25+):
  `docker buildx build --platform linux/amd64 -f lib/java/pathland-quarkus-demo/Dockerfile -t pathland-quarkus-demo .`
  then `docker run --rm -p 8080:8080 pathland-quarkus-demo`. The Dockerfile uses the
  UBI9 Mandrel 25 builder image + `lib/java/mvnw`; no local GraalVM is required. The
  extension registers the renderer's `base.css` for native-image, and JNA is not on
  the server classpath (`pathland-native-core` is desktop-only).
- Run the Spring Boot demo (SSR + WebSocket deltas):
  `cd lib/java/pathland-spring-boot-demo && mvn package && java -jar target/pathland-spring-boot-demo-0.1.0.jar`.
  Needs JDK 17+ (the whole stack runs on every LTS from 17) and Spring Boot ≥ 3.5.
  Prefer the fat jar over an IDE run: an IntelliJ classpath can mix stale `~/.m2`
  jars with fresh `target/classes`, which has been observed to make the Rust SSR
  decode an empty tree (a valid but blank page). If running from IntelliJ:
  **Reload All Maven Projects → Build**, and only one instance at a time. The
  DOM bundle ships in the starter/extension jars and is content-hashed
  (`dom-renderer-<sha256>.js`, pointer in `_pathland/dom-renderer.current`) so a
  rebuilt bundle always busts the immutable asset cache by URL — restart the app
  (fresh HTML references the new
  name) and reload normally; no hard refresh is needed. After any Rust/TS or
  design change, refresh the layers the IDE cannot rebuild itself with
  `mvn -P refresh-ide validate` (root `pom.xml`: the design build/sync and
  `npm run build:all`), then Reload/Build → Run → reload.

### HTML base design (`design/`)

The HTML renderer's built-in stylesheet is generated from a **three-layer Style
Dictionary pipeline** plus hand-authored reset/component CSS (one built-in base
design; no app-facing base-design selection):

```
design/tokens/base/*.tokens.json      # internal palette + scales (not emitted)
design/tokens/semantic/*.tokens.json  # protocol catalog semantics (emitted)
design/tokens/component/*.tokens.json # per-component surface (emitted)
design/html/{reset,components}.css    # hand-authored CSS consuming var(--pl-*)
design/scripts/{build,dev,check}.mjs  # SD build, watcher/preview, validation
design/build/base.css                 # generated: reset + tokens + components
design/build/base.css.name            # content hash: base-<sha256-12>.css
```

- Tokens are flat DTCG entries (`$value`/`$type`/`$extensions.pathland`) because
  the catalog has both leaf and group paths (`control.border` vs
  `control.border.width`). Dark values live on `$extensions.pathland.dark`;
  durations/z-index/`em` icon sizes are marked internal.
- `npm --prefix design run build` generates
  `design/build/{tokens.css,manifest.json,base.css,base.css.name}`;
  `npm --prefix design run check` validates catalog coverage, layer direction,
  dark/forced-dark declarations, dimension/`isLengthToken` rules, and the CSS
  content hash.
- `scripts/sync-design-css.sh` copies the bundle + name to
  `lib/rust/crates/pathland-render-html/assets/` (committed; `--check` fails on
  drift). `css.rs` embeds both with `include_str!` (`builtin_css()` /
  `builtin_css_name()`); the Java port copies the same committed assets into
  its jar via Maven resources (`BaseCss`).
- The renderer's library default is inline (`render_document`); the linked mode
  (`render_document_linked`, C ABI `pathland_html_render_linked` +
  `pathland_html_base_css[_name]`, Java `renderLinked`/`baseCss`/`baseCssName`)
  emits `{base}/base-<hash>.css`. The Spring Boot and Quarkus starters use it
  and serve the renderer-owned bytes immutable (global + mounted bases); token
  overrides/fit rules stay inline.
- The Maven `refresh-ide` profile and CI run the design build + sync. Do not
  edit generated CSS by hand.

### Native C-ABI shim (`pathland-view-native` + `pathland-core-capi`)

The **desktop/cross-language zero-copy path**: `pathland-view-native` exposes the
flat world (the flat `builder`/`engine` surface) as `pathland_native_*` C
functions with a **shared-memory ring** the host driver reads in place. Any
language that links the dylib (Swift/Java via FFI) calls the flat surface —
no per-component wrappers — and consumes opcodes zero-copy.

`pathland-core-capi` (`libpathland_core`) is the **minimal ring C ABI** for the
Kotlin DSL: `pathland_core_create`/`destroy`, `pathland_ring_buffer_push` (one
raw 16-byte opcode), `pathland_core_arena_alloc`, frame boundaries, zero-copy
`ring_ptr`/`ring_len`, and event drain/send. The Kotlin DSL
(`dev.pathland.nativecore` in `pathland-native-core`) binds it with **JNA**
(lazily loaded, zero JNI) and owns the retained tree + diff emission itself.

### GTK4 Renderer (`pathland-render-gtk`)

The desktop renderer: a single crate that owns **all** GTK/glib/pango usage and
maps opcode frames **incrementally** onto native GTK widgets (a retained
`id → gtk::Widget` map). Both the Rust demo and the Java demo render through
it — the demos **never call GTK or Swing directly**:

- Rust demo links `pathland-render-gtk` as an `rlib` and calls `GtkRenderer::run(...)`
  with a wake closure.
- The Java/Kotlin desktop demo (`pathland-desktop-demo`) loads `pathland-render-gtk`
  as a `cdylib` via JNA and
  calls `pathland_gtk_run_ring(ring, on_event, width, height)` over the Kotlin view's
  `libpathland_core` ring; native inputs round-trip as `EVENT` opcodes through the shared
  ring. The renderer **writes** the events and **wakes** the host (no payload); the host
  drains the event ring itself via `pathland_core_drain_events`. Events are never
  delivered through a side-channel callback — they flow through the opcode engine.
  The demo mounts the Kotlin `MusicPlayerView` (the same shared view the web demos use) with the
  app-driven media contract: the renderer plays the tracks through a direct GStreamer
  `playbin` and reports `MEDIA_*` events back; the demo's web-style
  `/_pathland/assets/...` paths resolve against an extracted local asset root
  (`pathland_gtk_set_asset_root`).

The renderer maps native inputs onto the raw `Event` surface via GTK4
controllers (`Button::clicked` → pointer up, `EventControllerMotion` →
pointer move/enter/leave), emitting events through the same `Pump::send_input`
path.

`pathland_gtk::run` passes an explicit empty argv to GTK (the process command
line belongs to the embedding host — e.g. the JVM's `-cp`/`-D` flags — and must
not be parsed as GTK options).

### Kotlin JVM authoring DSL (`dev.pathland.view.dsl`, in `lib/java/pathland-view-core`)

The **sole JVM authoring surface**. It is the SwiftUI-like DSL shared by desktop,
Spring Boot, Quarkus, and web apps. Components and modifiers:

- `View` is an **open Kotlin interface** — the library ships `Text`/`Image`/`Color`/
  `Rectangle`, `VStack`/`HStack`/`ZStack`, `Spacer`, `Button`,
  `TextField(placeholder, WritableSignal<String>)`, `Video` (a
  `MediaController`'s surface), and application code defines its own views by
  extending `Component` or implementing `View`. **Media is a state object, not a
  view** (spec/MEDIA.md): `MediaController` owns the resource + transport as
  signals, a `Video` surface renders it, and **audio has no view** — the app
  binds its own buttons/sliders (`controller.attach()` materializes the
  controller's node once, outside any swapping branch).
- **SwiftUI-style `body()` / reactive components**: composite views declare
  `View body()`; a signal read there subscribes that component, and a later write
  re-renders only that component and diffs its retained subtree. A signal bound
  to a node stays fine-grained and does not re-run the body. Plain
  `if`/`when`/`for` works; `ForEach` is the reactive list component.
- **Builders and modifiers**: `VStack(spacing = 8f) { … }`, `Text("hi")`,
  `Button("Save") { … }`, `.padding`, `.frame`, `.fontSize`, `.background`,
  `.buttonStyle(BorderedButtonStyle)`, `.windowMetadata`, navigation intents, and
  the full modifier vocabulary. Styles are Kotlin values
  (`PlainButtonStyle`, `BorderedButtonStyle`, label styles, `NativeVideoStyle`).
- **Builder scopes**: view blocks take a `PathlandScope` receiver. `PathlandScope`,
  `Navigation.Builder`, and modifier/style `Config` receivers share the
  `@PathlandDsl` marker, so an outer router/config receiver cannot be captured
  implicitly inside a nested view block (compile-time guard only; the
  thread-local `ViewScopes` stack still collects children).
- **Persisted state**: `Component` provides `val count by state(0)` (auto-keyed
  by property name) or `state(0, "key")`, bound to the session's
  `PersistentState` before the first `body()` render — no annotation processor or
  kapt.
- **Java hosting**: Java Spring Boot/Quarkus apps remain first-class hosts. A
  Java `PathlandApp` bean returns a Kotlin `View`
  (`return () -> MusicPlayerView.make();`); no Kotlin compiler or authoring DSL is
  needed in the host module. Every reusable Kotlin root exposes a public
  constructor or `@JvmStatic make(...)` factory with Java-friendly types.
- **Consistency** with `pathland_core::constants` is enforced by the
  emitter/HTML tests (golden byte layout + round-trips).

### POC Goals (issues #12–#18)

| # | Goal | Status |
|---|------|--------|
| 12 | 16-byte opcode engine | **Complete** |
| 13 | Core components & modifiers in Rust | **Complete** |
| 18 | Opcode transport layer (+ native C-ABI shim, zero-copy ring) | **Complete** |
| 16 | Rust desktop app with GTK4 renderer (native shared ring) | **Complete** |
| 15 | Quarkus SSR + WebSocket demo | **Complete** |

---

## Project Vision

**Pathland is a cross-platform, cross-language UI protocol** that enables retained-mode UI development with multiple renderer backends. It is **protocol-first**, meaning the protocol specification is the source of truth, not any particular implementation.

**Primary Goal**: Write once, run anywhere - Enable UI code to be written once and deployed across web, mobile, desktop, and embedded platforms through different renderer implementations.

**Status**: The Rust opcode engine (16-byte opcode, ring buffer, arena, diff-based reactive emission) is implemented and tested (`cargo test`). See the Architecture section above.

---

## CRITICAL ARCHITECTURAL PRINCIPLES

### Principle 1: Renderer Statelessness (NON-NEGOTIABLE)

> **The renderer MUST NOT retain application state.**

- Renderers are **pure functions** of the opcode stream: same opcodes → same output
- A renderer MAY retain its **rendered-output tree** (id → native element + computed layout) for drawing, hit-testing, and event routing — this is a cache of its own output, not state
- The renderer MUST NOT retain component property values as business state, application control flow, or anything the application can change without emitting an opcode
- The application owns the canonical retained UI tree
- The engine emits **deltas only**; the renderer applies them to its native elements

**Violation of this principle is a bug, not a feature.**

### Principle 2: Command-Based Protocol (NON-NEGOTIABLE)

> **The protocol transmits tree mutations as a stream of commands, NOT as serialized trees.**

- Each frame is a **batch of delta commands** describing changes
- Only **actual changes** are transmitted (diff-based reactive emission)
- Protocol acts like a **VM instruction stream** / ABI
- Deterministic, linear decoding without schema lookup or reflection

### Principle 3: Binary Protocol (NON-NEGOTIABLE)

> **The protocol MUST be binary.**

- Open-source, cross-platform binary format
- Fixed **16-byte instructions** in a ring buffer shared with the host via linear memory
- Custom instruction-based bytecode, not schema-driven serialization
- **Both directions are binary**: tree mutations (app → renderer) AND raw-input events (renderer → app) share the same instruction format, so the wire is language- and platform-agnostic

---

## Protocol Quick Reference

The semantic surface is catalogued in the companion specs: **`spec/PRIMITIVES.md`**
(primitive views + component IDs), **`spec/MODIFIERS.md`** (core modifiers = the
`PARAMETER` properties), **`spec/EVENTS.md`** (core events + listener bits),
**`spec/LAYOUT.md`** (frame & layout allocation contract). The
tables below are a quick recap of the protocol surface; the companion files
carry the authoritative ID allocations.

> **Implementation status lives in each project's `status.md`**, not in the
> specs: `lib/rust/crates/pathland-core/status.md`,
> `lib/rust/crates/pathland-render-gtk/status.md`,
> `lib/rust/crates/pathland-render-html/status.md`,
> `lib/java/pathland-core/status.md`, `lib/java/pathland-view-core/status.md`,
> `lib/java/pathland-render-html/status.md`,
> `lib/java/pathland-server-core/status.md`,
> `lib/java/pathland-spring-boot-starter/status.md`,
> `lib/java/pathland-quarkus/status.md`,
> `lib/java/pathland-demo-views-kotlin/status.md`,
> `lib/java/pathland-desktop-demo/status.md`,
> and the other Rust crates' `status.md` files.

### Opcode Format

Every opcode is exactly 16 bytes:

```
[Category: u8][Command: u8][Flags: u16][A: u32][B: u32][C: u32]
```

See `spec/OPCODE.md` for the full format, ring buffer, arena, and frame lifecycle.

### Machine-readable protocol manifest

Rationale: [ADR-0009](./docs/decisions/0009-cross-language-conformance.md).

`spec/protocol.tsv` is the target-neutral ID manifest emitted from
`pathland_core::constants` by `pathland-protocol-manifest` (`cargo run -p
pathland-protocol-manifest`). It contains only `kind`, `group`, `name`, and
`value`; each language binding keeps its own representation and validates or
reads the manifest. There is no Rust→JVM source generation.

The JVM mirror is hand-written and checked by
`pathland-core/src/test/java/.../ProtocolManifestTest`: every manifest row
must match a Java constant, no extra public numeric constants may exist, and
`ValueTypes.forProperty` must match `property_value_type`. CI regenerates the
manifest from Rust and fails on drift.

Engine behavior beyond individual opcodes is specified in `spec/ENGINE.md` and
enforced by shared self-contained frame traces under `spec/engine-traces/`.
`pathland-engine-golden` emits them from the Rust engine and the JVM
`EngineTraceTest` compares its encoded frames byte-for-byte.

### Categories

| Category | Value | Direction | Description |
|----------|-------|-----------|-------------|
| TREE | 0x01 | Guest → Host | Node create/delete/insert/remove/move |
| PARAMETER | 0x02 | Guest → Host | Constraint properties (spacing, padding, …), design tokens |
| EVENT | 0x03 | Host → Guest | Raw inputs: pointer down/move/up, key down/up |
| META | 0x04 | Both | Reset, environment, window metadata |

### Component Types (u16)

Grouped ranges (see `spec/PRIMITIVES.md`):

| Group | Component | ID |
|-------|-----------|----|
| **Primitive Drawing (0x01–0x0F)** | TEXT | 0x01 |
| | IMAGE | 0x02 |
| | COLOR | 0x03 |
| | SHAPE | 0x04 |
| | DIVIDER | 0x05 |
| | SPACER | 0x06 |
| | PROGRESS_VIEW | 0x07 |
| | GAUGE | 0x08 |
| | VIDEO (a `MEDIA_CONTROLLER`'s surface) | 0x0A |
| **Layout & Container (0x10–0x1F)** | VSTACK | 0x10 |
| | HSTACK | 0x11 |
| | ZSTACK | 0x12 |
| | GRID | 0x13 |
| | SCROLLVIEW | 0x14 |
| | LAZY_VGRID | 0x15 |
| | LAZY_HGRID | 0x16 |
| | LAZY_VSTACK | 0x1B |
| | LAZY_HSTACK | 0x1C |
| **Semantic Control (0x20–0x2F)** | BUTTON | 0x20 |
| | TEXT_FIELD (secure via `IS_SECURE`) | 0x21 |
| | TEXT_EDITOR | 0x22 |
| | TOGGLE (`TOGGLE_STYLE`: Switch/Checkbox/Button) | 0x24 |
| | SLIDER | 0x25 |
| | STEPPER | 0x26 |
| | DATE_PICKER | 0x27 |
| | PICKER | 0x28 |
| | MENU | 0x29 |
| | COLOR_PICKER | 0x2A |
| **Media (0x30–0x3F)** | MEDIA_CONTROLLER (non-visual, owns playback) | 0x30 |
| **Utility** | COMMENT | 0x7F |

### Key Property IDs

**Stack**: SPACING=0x0001, ALIGNMENT=0x0002, CONTENT_MARGINS=0x0005  
**Text**: TEXT=0x000A, TEXT_ALIGNMENT=0x000C, LINE_LIMIT=0x000B, TRUNCATION_MODE=0x000D  
**Style**: COLOR=0x100A, FONT_SIZE=0x1007, FONT_WEIGHT=0x1008, FONT_FAMILY=0x1009, BACKGROUND_COLOR=0x1001, BORDER_WIDTH=0x1003, BORDER_COLOR=0x1004, BORDER_RADIUS=0x1005, OPACITY=0x100D, VISIBLE=0x100E, Z_INDEX=0x100F, CLIPS_TO_BOUNDS=0x1010, PADDING=0x1011, PADDING_TOP/RIGHT/BOTTOM/LEFT=0x1012-0x1015  
**Special**: WIDTH=0x100B (use -1=FILL, -2=HUG_CONTENT), HEIGHT=0x100C  
**Semantic**: ROLE=0x2001, STATE=0x2002, ENABLED=0x2003, SELECTED=0x2004, EVENT_LISTENERS=0x2005 (u32 bitmask — which raw events a node wants reported), VALUE=0x2006, MIN_VALUE=0x2007, MAX_VALUE=0x2008, LABEL=0x200A, PROMPT=0x200B  
**Binding/action**: ACTION_ID=0x2016, BINDING_ID=0x2017, TOGGLE_STYLE=0x2018 (Switch=0, Checkbox=1, Button=2)  
**Media** (spec/MEDIA.md): MEDIA_CONTROLLER_ID=0x103A (a `VIDEO`'s controller), MEDIA_SOURCE=0x103B, MEDIA_MUTED=0x103C, MEDIA_LOOP=0x103D, MEDIA_RATE=0x103E; PLAYBACK_STATE=0x1035 / MEDIA_POSITION=0x1036 / MEDIA_VOLUME=0x1037 live on the controller

### Colors

- Literal colors are **sRGB** with D65 white point, packed as `0xAARRGGBB`
- A color property may instead carry a `DESIGN_TOKEN` reference to a token path
- Semantic token resolution and theme adaptation are **renderer-owned**

### Design Token System

**Core Principle**: Application defines intent, overrides, and the theme.
Renderer defines visual appearance, behavior, defaults, and interaction states.

- The **application owns the theme**: token overrides (base = **light**, `dark.`-prefixed = **dark**) and token references in style properties. Token refs cover COLOR and non-color values (`Length` for spacing/padding/font size/radius/border/frame; `FontFamily.Token` for STRING) in the Rust and Kotlin DSLs.
- The **renderer owns** default token values (light + dark, the full Tier-1 catalog), token resolution (including explicit `dark.` = forced dark and parent fallback), effective color-scheme detection, interaction states (hover, pressed, focus, disabled), and theme adaptation
- `SET_DESIGN_TOKEN` identifies tokens by **dot-separated string paths** (e.g. `color.primary`, `space.2`) with the standard value-type encoding — there is no separate numeric tokenId system. A `dark.` prefix selects the dark variant (`dark.color.primary`); a `DESIGN_TOKEN` reference whose path starts with `dark.` forces the dark value in either scheme
- **Runtime theming**: `META::RESET_THEME` clears every app override; a replacement theme is reset + `SET_DESIGN_TOKEN` overrides in one frame. `META::SET_COLOR_SCHEME` forces `platform`/`light`/`dark`. Both are renderer state — no node is re-emitted. Java: `Emitter.replaceTheme`/`setColorScheme` + `PathlandSession.setTheme`/`setColorScheme`; Rust: `Engine::reset_theme`/`set_color_scheme`.
- An unresolvable token reference leaves the property **unset** (renderer default), never an error or stale value.
- The protocol MUST NEVER describe hover styles, click styles, visual transitions, conditional styling, or layout tuning for interaction states
- **Deferred**: applying per-component tokens to native GTK/Qt leaf control chrome; web parent-path fallback.

The HTML renderer's default token values are generated from `design/` (see
"HTML base design" above). Full details: [Design Token System](./spec/OPCODE.md#design-token-system)
and the canonical catalog / color-scheme contract in [spec/TOKENS.md](./spec/TOKENS.md).

---

## Implementation Guidelines

### Adding New Component
1. Add to `component_type` in `lib/rust/crates/pathland-core/src/constants.rs`
2. Document in `spec/OPCODE.md`
3. Implement the component type in `pathland-core` (if it affects the retained tree)
4. Implement native-element mapping in the target renderer (Goal 3–5)
5. Update the affected project(s)' `status.md`

### Adding New Property
1. Add to the appropriate property module in `constants.rs`
2. Document in `spec/OPCODE.md`
3. Handle the value type in `pathland-core`'s diff emission (if a stack/text/size property)
4. Implement in each renderer's property application
5. Update the affected project(s)' `status.md`

### Modifying Protocol
1. Update `spec/OPCODE.md` first (and `spec/CONFORMANCE.md` vectors)
2. Update `constants.rs` and `pathland-core` encoding/emitters
3. Run `cd lib/rust && cargo run -p pathland-protocol-manifest` and commit the
   updated `spec/protocol.tsv`
4. Update the hand-written JVM protocol constants (the Java manifest test fails
   with the exact missing/stale row otherwise)
5. If emission behavior changed, run
   `cargo run -p pathland-engine-golden -- --emit` and commit the trace fixtures
6. Verify backward compatibility; update `pathland-core`/renderers + tests
7. Update the affected project(s)' `status.md`

### Changing the Wire Format
- Bump the wire `version` field in `spec/OPCODE.md` and `pathland-core`'s header (currently 1). Pre-release format changes are acceptable while version stays 1.

### Keeping implementation status updated (MANDATORY)

**The agent MUST keep the per-project `status.md` files in sync with the code.**
Whenever you land a protocol or renderer change — a new component/property/
command/event, a new encoder/decoder, a new widget mapping, a new modifier or
renderer feature, a test that proves new surface — **update the affected
project's `status.md`** (Implemented / Not implemented / gaps) in the same
change. The specs (`spec/`) describe the protocol contract only and MUST NOT
carry implementation status; status lives in `status.md` next to each
implementing project.

---

## Common Pitfalls

### NEVER Do These
| Anti-Pattern | Why Wrong | Correct |
|--------------|-----------|---------|
| Renderer stores application state | Violates statelessness | Application owns the tree |
| Engine computes layout / emits rects | Violates declarative model | Emit structure + constraints; native elements lay out |
| Send full trees | Inefficient | Diff-based reactive emission |
| String property names | Larger payloads | Use numeric IDs |
| String component names | Larger payloads | Use numeric IDs |
| Generic RGB | Ambiguous | Explicitly sRGB |
| Hover styles in protocol | Renderer job | Define in theme |
| Heap allocation in steady-state emission | Slow | Preallocated snapshot slab + arena |

---

## Project Status

**Complete**: 16-byte opcode engine (`pathland-core`), SwiftUI-style view DSL with decoupled chainable modifiers (`pathland-view`), declarative diff-based reactive emission (`pathland-core`), **reactive signals** (`pathland-core::signal` — writable `Signal` values bound to node text/properties, so a signal change re-emits only the bound nodes, reachable from Java via the `pathland_native_signal_*` / `node_bind_*` C ABI), host reader to a native-element description (`pathland-render-gtk`), opcode transport with network-batch encode/decode + time/size batching policy (`pathland-core-transport`), golden conformance vectors (incl. raw-input EVENT and network-batch bytes), typed raw-input event encode/decode, `no_std` builds, zero-alloc steady-state emission, the **bidirectional event ring** (host → guest EVENT opcodes in shared memory, drained by the host via a generic `pathland_native_drain_events` C ABI — the renderer only writes + wakes), the **GTK4 renderer** (`pathland-render-gtk`) with Rust demos that render through it without touching GTK directly, the **native JVM library set** — `pathland-core` (pure-Java opcode/wire codec, attributed text, and Markdown) and `pathland-view-core` (Kotlin: open `View` interface + shared value/SPI vocabulary, **Angular-parity signals/computed/effects** with synchronous flush, fine-grained retained-tree emitter with **node-level binding effects**, router, cross-platform `StateStore` + **`PersistentState`**) and the opt-in `pathland-native-core` (`dev.pathland.nativecore` — lazy **JNA ring interop** into `libpathland_core`, kept off the server classpath) and the sole JVM authoring DSL `dev.pathland.view.dsl` (SwiftUI-shaped builders, modifier sugar, property-delegate state, navigation/media helpers, styles), `dev.pathland.render.html` (pure-Java port of the Rust HTML renderer; golden-fixture-checked byte-for-byte), the opt-in `pathland-state-redis` (`dev.pathland.state.redis` — Lettuce `RedisStateStore` + `RedisStateStores.redisOrFallback`) — and the **Quarkus demo** (`pathland-quarkus-demo`, a Java host mounting the Kotlin `MusicPlayerView`/`AdView`, plus a Kotlin-only view at `/kotlin`; builds a GraalVM/Mandrel native executable via its Dockerfile), the **desktop demo** (`pathland-desktop-demo`, GTK and Qt), and the **Spring Boot** (`pathland-spring-boot-demo`, a Java host mounting Kotlin views) **SSR + WebSocket demos** with per-window persisted `State` (Redis/in-memory — every browser window/tab gets its own UI model and state scope via a `sessionStorage` window id, so two windows never share a session). **`EVENT_LISTENERS`** (any element emits raw events via a u32 bitmask property) and **`onTapGesture`** (Rust + Kotlin DSL modifier + app-side `TapRecognizer` over the raw pointer events). **Generic input routing** — the emitter returns tap-action / text-input / value-input registries (`RenderResult`), so the host forwards raw `EVENT` batches straight into the bound signals and never reaches into a view's internals. **The media kit** (spec/MEDIA.md) — a non-visual `MEDIA_CONTROLLER` (0x30) state object owning a resource + transport as signals, referenced by a `VIDEO` (0x0A) surface and controlled by the app's own views; **audio has no view**. Implemented across spec, `pathland-core`, the GTK + HTML renderers, the TS DOM client, the Kotlin DSL/emitter, and the Rust DSL/engine (a `MediaController` + `Video` + `attach()`, with string-property signal bindings and a **pinned** controller node id so a `SIZE_THAT_FITS` band swap never restarts playback). **Attributed text** (`SET_ATTRIBUTED_TEXT`) — `AttributedString` model/codec, the shared `pathland-markdown` inline parser (+ `pathland-markdown-capi` for non-JVM hosts; the JVM parser is a pure-Java port), Rust/Kotlin DSL builders, GTK markup, SSR spans/anchors, and DOM-client application. **Built-in Markdown `Text`** — `Text("**hi**")` auto-parses in both DSLs (SwiftUI-like; `markdown = false` / `Text::plain` opt out); plain results keep the compact `SET_TEXT` path. **Topic subscriptions (scoped + global)** — a strongly typed `Topic<T,S>` + `topic(key, subject, initial)` for entity-scoped topics and `Topic.global` + `topic(key, initial)` for app-wide ones in the Kotlin DSL, a per-mount `PathlandSignalHub.publish(topic, subject, value)` / `publish(topic, value)`, and `PathlandSession.submit(...)`: external threads update only the sessions subscribed to that scope, with values applied on the session actor and coalesced latest-wins (docs/external-updates.md). **Tests green across the workspace.**

**In progress**: —

**Planned / deferred**: value-control (slider/switch) editing + rendering, image rendering, cross-platform state backends beyond Redis + in-memory (File/SQLite for desktop, LocalStorage for browser/WASM, NVS Flash for ESP32 — the `StateStore` contract is already platform-neutral).

---

## Quick Start

1. Read this file, `spec/OPCODE.md`, and `spec/ENGINE.md`
2. Run Rust tests: `cd lib/rust && cargo test` (runs all crates)
3. Build/test the JVM libraries: `cd lib/java && mvn install`. The JVM path is
   pure JVM (engine/protocol + HTML renderer + inline-Markdown parser); no Rust
   build is required. Use `mvn -P jvm-only test` for the library/starter subset
   without the demo modules.
4. Make small changes, verify they work

---

## Pathland Mindset

**Always remember**:
1. Protocol is King
2. Renderers are Dumb (execute commands only)
3. Applications are Smart (generate commands)
4. Only Changes Matter (transmit mutations / reactive emission)
5. Binary is Beautiful (efficient, deterministic)

**When in doubt**: Check `spec/OPCODE.md`, the conformance vectors in `spec/CONFORMANCE.md`, and the companion catalogs (`spec/PRIMITIVES.md`, `spec/MODIFIERS.md`, `spec/EVENTS.md`, `spec/LAYOUT.md`), and the reference implementation (`pathland-core`). Ask: "Does this maintain stateless renderers?" "Does this only transmit actual changes?" "Does this describe WHAT the UI is, never WHERE?" "Is this representable as a 16-byte opcode in any language?"
