# Pathland Project - AI Agent Guide

This document provides essential context for AI agents (or human contributors) working on the **Pathland** protocol. It captures the project's direction, architectural decisions, and implementation details to ensure consistency.

**Last Updated**: August 21, 2026

---

## Architecture: Rust Opcode Engine

> The canonical core is a **Rust engine** that emits the application's
> **declarative view structure** — VStack, HStack, Text, spacing, padding,
> alignment — as a fixed **16-byte opcode ring buffer** into shared linear
> memory, consumed by host renderers. View components and modifiers are written
> in Rust with a SwiftUI-like API, and in Java with the hand-written
> **`com.pathland.view` DSL** (reusable library, Java 17+); both drive the engine
> through the flat **native C ABI** (`pathland-view-native`) over a zero-copy shared
> ring on desktop.

### The 16-byte Opcode Engine

- Every opcode is exactly **16 bytes**: `Category (1B) | Command ID (1B) | Flags (2B) | A (4B) | B (4B) | C (4B)`, `#[repr(C, packed)]`, little-endian.
- A 64-byte cache line holds exactly **4 opcodes** → L1-cache-friendly.
- Opcodes live in a **ring buffer** in linear memory; the guest writes at `writeCursor`, the host reads from `readCursor`; frame boundaries via a monotonic `frameCount` in the header block.
- **Both directions are binary**: a second **event ring** (host → guest) carries `EVENT`-category opcodes. The renderer writes raw inputs at `eventWriteCursor`; the guest drains them at `eventReadCursor`. Events are 16-byte opcodes through the engine, never a side-channel callback.
- **Variable-length data** (strings, token paths) lives in a bump **arena**; opcodes reference entries by offset (`[u32 byteLength][bytes...]`), so every opcode stays 16 bytes. A second host-owned **event arena** mirrors this in the host → guest direction (shared-memory event strings, e.g. `TEXT_CHANGED`); over the network, host → guest strings ride the batch's string section.
- Spec: **`spec/OPCODE.md`** (primary) + **`spec/CONFORMANCE.md`** (golden vectors). Companion catalogs: **`spec/PRIMITIVES.md`** (views), **`spec/MODIFIERS.md`** (modifiers/properties), **`spec/EVENTS.md`** (events) — draft IDs are allocated there spec-first.

### Declarative, not positioned (NON-NEGOTIABLE)

> **The engine describes WHAT the UI is — VStack, HStack, Text, spacing,
> padding, alignment — never WHERE it is. It does not compute layout and does
> not emit rects.**

- The engine emits **declarative structure** (`TREE` create/delete/insert/remove/move) + **constraint properties** (`STYLE` spacing/padding/alignment/FILL/HUG hints).
- **Native elements everywhere**: each platform's renderer maps the opcode stream onto that platform's native elements — GTK4 widgets on desktop, DOM elements in the browser, HTML server-side — and those native elements lay themselves out. Never a generic canvas unless a platform has no native equivalent.
- **Reactive emission**: emission is diff-based. Only the nodes that changed emit opcodes (property diffs via `SET_PROPERTY`/`SET_TEXT`, structural diffs via `TREE` opcodes). An unchanged tree emits **zero** opcodes. Signals in the application mark the tree dirty; the engine reconciles.
- **Zero dynamic heap allocations** during steady-state emission (proven by test).

### no_std / wasm32 direction guard (NON-NEGOTIABLE)

`pathland-core` and `pathland-view` must remain `#![no_std]` + `alloc` and
compile for `wasm32-unknown-unknown`. This is the guarantee that the protocol
core can still target a browser / embedded / remote projection later (goal #15,
Quarkus SSR + WebSocket). The desktop path (`pathland-view-native`) is the only
consumer today, but the core must not become desktop-only. Verify with
`lib/rust/check-wasm.sh` (i.e. `cargo build -p pathland-core -p pathland-view
--target wasm32-unknown-unknown`).

### Reactive signals (Angular-style)

`pathland-core::signal` provides writable signals that live in the engine. A
node binds its text (`Node::text_binding`) or a property
(`Node::property_bindings`) to a `SignalId`; during emit the engine resolves the
binding and records the dependency, and `Engine::set_signal` re-emits **only the
bound nodes** (`SET_TEXT` / `SET_PROPERTY`) — no full tree walk. Signal values
are one of `F32`/`U32`/`Bool`/`Enum`/`Str` (packed to the wire's `u32` value or
arena string). Foreign hosts reach signals via the flat C ABI
(`pathland_native_signal_create_*` / `signal_set_*` / `node_bind_text` /
`node_bind_property`) and the Java `Signal` type. Binding then calling `emit`
records the dependency; a subsequent `set` is a partial emit. (Computed/derived
signals + effects are the planned next step.)

### Rust Workspace (`lib/rust/`)

```
# ── The three elements ────────────────────────────────────────────────────
crates/pathland-view/     # RETAINED UI — SwiftUI-style view DSL: VStack/HStack/Text/Button +
                          #   decoupled modifiers (spacing/padding/fontSize/color/background)
                          #   + vstack!/hstack! macros (no_std)
crates/pathland-core/     # PROTOCOL — 16-byte opcode, ring buffer, arena, constants, events,
                          #   memory layout, Guest/Host/Frame surface (no_std, zero-alloc).
                          #   Pure protocol; the emitter moved out to pathland-engine.
crates/pathland-engine/   # EMITTER — retained tree types (Node/Component) + the diff-based
                          #   reactive emitter + signals.
crates/pathland-core-transport/ # TRANSPORT — shared-memory ring owner (RingTransport) +
                          #   network batch encode/decode + batching policy (std)
crates/pathland-render-gtk/ # RENDERER — host reader (RenderTree) + maps opcode frames onto
                          #   native GTK widgets incrementally; the only crate that touches
                          #   GTK/glib/pango. Exposes pathland_gtk_run for Java (JNA) hosts.
crates/pathland-render-html/ # RENDERER — maps opcode frames onto declarative HTML (flex
#   stacks, spans, buttons) as a pure function of the stream; the
                           #   server-side/remote-projection target (Goal #15).
# ── Web client (DOM renderer, lib/typescript) ──────────────────────────────
lib/typescript/                        # DOM RENDERER — @pathland/dom-renderer: a small
                           #   vanilla-TypeScript hydration client (no runtime deps):
                           #   hydrates the SSR HTML by data-pathland-id, decodes
                           #   self-contained PLPL batches, applies STYLE + TREE deltas
                           #   in place, sends raw-input events over /ws. The server
                           #   NEVER replays the full tree on connect (the client already
                           #   has it from the HTML); a full snapshot is sent only on an
                           #   explicit META::RESYNC (client requests it after reconnect).
                           #   Built to dist/pathland-dom-renderer.js and copied into both demos.
# ── Retained-UI projection (host/driver surface) ──────────────────────────
crates/pathland-view-native/ # NATIVE C-ABI shim + NativeHost: flat world over a zero-copy
                          #   shared-memory ring, for Swift/Java/C#/other native hosts
                          #   (cdylib name stays "pathland_native")
crates/pathland-core-capi/ # MINIMAL RING C ABI — the SPSC ring + 16-byte opcode engine as
                          #   libpathland_core: create/destroy, pathland_ring_buffer_push,
                          #   arena alloc, frame boundaries, zero-copy read, event drain/send.
                          #   Consumed by the Java DSL via JNA (com.pathland.view.ffm).
# ── Demo ───────────────────────────────────────────────────────────────────
crates/pathland-render-gtk-demo/ # Rust GTK4 demo: authors the DSL, emits into the ring, renders
                          #   through pathland-render-gtk. Uses NO GTK APIs directly.
```

### Java libraries (`lib/java/`, framework-agnostic)

The reusable Java library set (`org.pathland`, Maven reactor, Java 17+) — the same
hand-written DSL serves Spring Boot, Quarkus, and desktop apps:

```
lib/java/
  pathland-view/        # com.pathland.view — SwiftUI-like DSL (View/VStack/Text/Button/
                        #   ButtonStyle via a thread-local Environment), Angular-style
                        #   signals/computed/effects (com.pathland.view.signal), fine-grained
                        #   emitter with node-level binding effects (com.pathland.view.emit),
                        #   wire codec (com.pathland.view.transport), lazy JNA ring interop
                        #   (com.pathland.view.ffm), cross-platform state
                        #   (com.pathland.view.state: StateStore/PersistentState/State)
  pathland-view-processor/ # JSR 269 annotation processor: generates <View>_StateBinder
                        #   for State fields (auto-keyed by field name)
  pathland-render-html/ # com.pathland.render.html — JNA shim over the Rust HTML
                        #   renderer (libpathland_render_html): one renderer, every language.
                        #   The jar embeds the Rust cdylib (META-INF/native/) so it's
                        #   self-contained. The dylib is built by CI's rust job and handed
                        #   to java via an artifact; locally, `cargo build -p
                        #   pathland-render-html` must run before `mvn test` (the jar-embed
                        #   step fails loudly if it's missing).
  pathland-state-redis/ # com.pathland.state.redis — Lettuce RedisStateStore
  pathland-server-core/ # com.pathland.server — transport-agnostic server runtime:
                        #   PathlandApp (root-view factory), PathlandConnection (send seam),
                        #   PathlandSession (per-session SSR + live deltas + environment),
                        #   PathlandRegistry (1:1 session lifecycle, actor thread), StateStores
  pathland-spring-boot-starter/ # com.pathland.spring — Spring Boot auto-config: add the dep +
                        #   a PathlandApp bean → SSR at any path + /ws deltas + state
  pathland-quarkus-starter/ # com.pathland.quarkus — Quarkus CDI integration: add the dep +
                        #   a PathlandApp bean → SSR at any path + /ws deltas + state
  pathland-demo-views/  # com.pathland.demo — shared demo views (CounterView/CounterControls/
                        #   NameField) declaring State fields; consumed by both demos
  pathland-quarkus-demo/# Quarkus SSR + WebSocket demo (com.pathland.demo.quarkus)
  pathland-spring-boot-demo/ # Spring Boot SSR + WebSocket demo (com.pathland.demo.spring)
```

- Test: `cd lib/rust && cargo test` (runs all crates).
- Build/test the Java libraries: `cd lib/java && mvn install`.
- Run the GTK demo (native, zero-copy shared ring): `cd lib/rust && cargo run -p pathland-render-gtk-demo`.
- Run the Quarkus demo (SSR + WebSocket deltas, dev mode):
  `cd lib/java/pathland-quarkus-demo && mvn quarkus:dev` (or `mvn package && java -jar target/quarkus-app/quarkus-run.jar`).
  Needs JDK 17+ (the whole stack runs on every LTS from 17) and Quarkus ≥ 3.18.
- Run the Spring Boot demo (SSR + WebSocket deltas):
  `cd lib/java/pathland-spring-boot-demo && mvn package && java -jar target/pathland-spring-boot-demo-0.1.0.jar`.
  Needs JDK 17+ (the whole stack runs on every LTS from 17) and Spring Boot ≥ 3.5.

### Native C-ABI shim (`pathland-view-native` + `pathland-core-capi`)

The **desktop/cross-language zero-copy path**: `pathland-view-native` exposes the
flat world (the flat `builder`/`engine` surface) as `pathland_native_*` C
functions with a **shared-memory ring** the host driver reads in place. Any
language that links the dylib (Swift/Java/C# via FFI) calls the flat surface —
no per-component wrappers — and consumes opcodes zero-copy.

`pathland-core-capi` (`libpathland_core`) is the **minimal ring C ABI** for the
Java DSL: `pathland_core_create`/`destroy`, `pathland_ring_buffer_push` (one
raw 16-byte opcode), `pathland_core_arena_alloc`, frame boundaries, zero-copy
`ring_ptr`/`ring_len`, and event drain/send. The Java DSL
(`com.pathland.view.ffm`) binds it with **JNA** (lazily loaded, zero JNI) and
owns the retained tree + diff emission itself.

### GTK4 Renderer (`pathland-render-gtk`)

The desktop renderer: a single crate that owns **all** GTK/glib/pango usage and
maps opcode frames **incrementally** onto native GTK widgets (a retained
`id → gtk::Widget` map). Both the Rust demo and the Java demo render through
it — the demos **never call GTK or Swing directly**:

- Rust demo links `pathland-render-gtk` as an `rlib` and calls `GtkRenderer::run(...)`
  with a wake closure.
- Java demo loads `pathland-render-gtk` as a `cdylib` via JNA and calls
  `pathland_gtk_run(host, on_event)`; native inputs round-trip as `EVENT`
  opcodes through the shared ring. The renderer **writes** the events and
  **wakes** the host (no payload); the host drains the event ring itself via
  `pathland_native_drain_events` (Rust: `NativeHost::drain_events` /
  `RingTransport::drain_events`). Events are never delivered through a
  side-channel callback — they flow through the opcode engine.

The renderer maps native inputs onto the raw `Event` surface via GTK4
controllers (`Button::clicked` → pointer up, `EventControllerMotion` →
pointer move/enter/leave), emitting events through the same `Pump::send_input`
path.

`pathland_gtk::run` passes an explicit empty argv to GTK (the process command
line belongs to the embedding host — e.g. the JVM's `-cp`/`-D` flags — and must
not be parsed as GTK options).

### Native Java DSL (`com.pathland.view`, in `lib/java/pathland-view`)

The **hand-written, zero-dependency Java 17+ DSL** (no codegen). It is the
SwiftUI-like authoring surface shared by desktop, Spring Boot, and Quarkus
apps. Components and modifiers:

- `View` is an **open interface** — the library ships `Text`/`Image`/`Color`/
  `Rectangle`, `VStack`/`HStack`/`ZStack`, `Spacer`, `Button(View label, Runnable)` +
  `Button(String, Runnable)`, `TextField(placeholder, WritableSignal<String>)`, and
  application code (Quarkus/Spring/desktop) defines its own views by implementing `View`.
- **SwiftUI-style `body()`**: composite views declare `View body()` (their subtree);
  the library's primitives override `render(Environment)` to materialize a
  `PathlandNode`. `body()` is evaluated **once at mount** — reactive values come from
  signals (`View.text(Signal)`, `Signals.computed`), not from body re-evaluation, so
  the emitter keeps its fine-grained `SET_TEXT`/`SET_PROPERTY` deltas.
- **Constructor props** (structural/layout: `alignment`, `spacing`) are
  constructor args, never chainable; **modifiers** (`padding`, `color`,
  `background`, `fontSize`, `fontWeight`, `frame`, `opacity`, `border`,
  `visible`, …) chain on any view. No standalone width/height — use the
  compound `frame(width, height, alignment)` modifier. Typed enums
  (`Alignment`/`TextAlignment`/`FontWeight`/`Truncation`), not raw ints.
- **Environment scoping via a `ThreadLocal`** (Java 17+): `.buttonStyle(ButtonStyle)`
  injects a style down the whole child tree; `ButtonStyle.makeBody(Configuration)`
  with built-in `PlainButtonStyle` / `BorderedButtonStyle`. Custom wrappers via
  `ViewModifier.body(View)`. The `Environment` also carries the session's
  `PersistentState`.
- **Reactivity (Angular parity)** in `com.pathland.view.signal`:
  `Signals.signal/computed/effect/untracked` — lazy+memoized computeds,
  glitch-free synchronous flush, equality suppression, error caching, write
  discipline (`allowWrites()`), circular-dependency detection.
- **Persisted view state** (`com.pathland.view.state`): a `State<T>` field (e.g.
  `State<Integer> count = new State<>(0);`) is auto-wired to the session's
  `PersistentState` (keyed by field name, or `new State<>(0, "key")` for an explicit
  key) by the `pathland-view-processor` annotation processor, which detects `State`
  fields by type and generates a `<View>_StateBinder` per view class. Each view
  connects its own `State` when it renders (`View.render` calls `Environment.state()
  → PersistentState.connect(view)`), so nested views can be instantiated inline in
  `body()` and need not be declared as fields. `PersistentState.signal(name, initial)`
  auto-loads the persisted value and auto-saves on change (key `name:scope`), and
  `StateStore` is untyped (one store serves all signal types).
- **Fine-grained emitter** (`com.pathland.view.emit`): mounts a view tree once,
  assigns stable ids, and registers a **node-level binding effect** per reactive
  text/property — a signal change re-emits only that node's `SET_TEXT` /
  `SET_PROPERTY` (unchanged signals emit zero opcodes). Sinks: `FrameOpcodeSink`
  (self-contained frames for SSR/WebSocket) and `RingOpcodeSink` (JNA into
  `libpathland_core`). The wire codec (`com.pathland.view.transport`) mirrors
  `pathland_core_transport` (`PLPL` batch format, self-contained frames + events).

Consistency with `pathland_core::constants` is enforced by the emitter/HTML
tests (golden byte layout + round-trips), replacing the old codegen assert pass.

- **Single signal value convention**: a view or modifier holds **one `Signal<T>`
  field** for a reactive value — never a raw field *plus* a signal field for the
  same value. Static values are authored through the raw `of(T)` overload, which
  wraps them in `Signals.constant(...)` (e.g. `Text.of("Hello")` ≡
  `Text.of(Signals.constant("Hello"))`); the emitter treats a `ConstantSignal` as
  a plain, non-reactive property (no binding/effect), so statics carry zero
  binding overhead. Controls' config fields for *distinct* properties
  (`min`/`max`/`step`/`placeholder`/`mode`/`style`) stay raw.

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
`STYLE` properties), **`spec/EVENTS.md`** (core events + listener bits). The
tables below are a quick recap of the protocol surface; the companion files
carry the authoritative ID allocations.

> **Implementation status lives in each project's `status.md`**, not in the
> specs: `lib/rust/crates/pathland-core/status.md`,
> `lib/rust/crates/pathland-render-gtk/status.md`,
> `lib/rust/crates/pathland-render-html/status.md`,
> `lib/java/pathland-view/status.md`, `lib/java/pathland-render-html/status.md`,
> and the other Rust crates' `status.md` files.

### Opcode Format

Every opcode is exactly 16 bytes:

```
[Category: u8][Command: u8][Flags: u16][A: u32][B: u32][C: u32]
```

See `spec/OPCODE.md` for the full format, ring buffer, arena, and frame lifecycle.

### Categories

| Category | Value | Direction | Description |
|----------|-------|-----------|-------------|
| TREE | 0x01 | Guest → Host | Node create/delete/insert/remove/move |
| STYLE | 0x02 | Guest → Host | Constraint properties (spacing, padding, …), design tokens |
| EVENT | 0x03 | Host → Guest | Raw inputs: pointer down/move/up, key down/up |
| META | 0x04 | Both | Reset, environment |

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
| **Utility** | COMMENT | 0x7F |

### Key Property IDs

**Stack**: SPACING=0x0001, ALIGNMENT=0x0002, CONTENT_MARGINS=0x0005  
**Text**: TEXT=0x000A, TEXT_ALIGNMENT=0x000C, LINE_LIMIT=0x000B, TRUNCATION_MODE=0x000D  
**Style**: COLOR=0x100A, FONT_SIZE=0x1007, FONT_WEIGHT=0x1008, FONT_FAMILY=0x1009, BACKGROUND_COLOR=0x1001, BORDER_WIDTH=0x1003, BORDER_COLOR=0x1004, BORDER_RADIUS=0x1005, OPACITY=0x100D, VISIBLE=0x100E, Z_INDEX=0x100F, CLIPS_TO_BOUNDS=0x1010, PADDING=0x1011, PADDING_TOP/RIGHT/BOTTOM/LEFT=0x1012-0x1015  
**Special**: WIDTH=0x100B (use -1=FILL, -2=HUG_CONTENT), HEIGHT=0x100C  
**Semantic**: ROLE=0x2001, STATE=0x2002, ENABLED=0x2003, SELECTED=0x2004, EVENT_LISTENERS=0x2005 (u32 bitmask — which raw events a node wants reported), VALUE=0x2006, MIN_VALUE=0x2007, MAX_VALUE=0x2008, LABEL=0x200A, PROMPT=0x200B  
**Binding/action**: ACTION_ID=0x2016, BINDING_ID=0x2017, TOGGLE_STYLE=0x2018 (Switch=0, Checkbox=1, Button=2)

### Colors

- Literal colors are **sRGB** with D65 white point, packed as `0xAARRGGBB`
- A color property may instead carry a `DESIGN_TOKEN` reference to a token path
- Semantic token resolution and theme adaptation are **renderer-owned**

### Design Token System

**Core Principle**: Application defines intent, overrides, and the theme.
Renderer defines visual appearance, behavior, defaults, and interaction states.

- The **application owns the theme**: token overrides (base = **light**, `dark.`-prefixed = **dark**) and token references in style properties.
- The **renderer owns** default token values (light + dark), token resolution, effective color-scheme detection (platform-derived), interaction states (hover, pressed, focus, disabled), and theme adaptation
- `SET_DESIGN_TOKEN` identifies tokens by **dot-separated string paths** (e.g. `color.primary`, `space.2`) with the standard value-type encoding — there is no separate numeric tokenId system. A `dark.` prefix selects the dark variant (`dark.color.primary`)
- The protocol MUST NEVER describe hover styles, click styles, visual transitions, conditional styling, or layout tuning for interaction states

Full details: [Design Token System](./spec/OPCODE.md#design-token-system) and the canonical catalog / color-scheme contract in [spec/TOKENS.md](./spec/TOKENS.md).

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
3. Verify backward compatibility; update `pathland-core`/`pathland-render-gtk` + tests
4. Update the affected project(s)' `status.md`

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

**Complete**: 16-byte opcode engine (`pathland-core`), SwiftUI-style view DSL with decoupled chainable modifiers (`pathland-view`), declarative diff-based reactive emission (`pathland-core`), **reactive signals** (`pathland-core::signal` — writable `Signal` values bound to node text/properties, so a signal change re-emits only the bound nodes, reachable from Java via the `pathland_native_signal_*` / `node_bind_*` C ABI), host reader to a native-element description (`pathland-render-gtk`), opcode transport with network-batch encode/decode + time/size batching policy (`pathland-core-transport`), golden conformance vectors (incl. raw-input EVENT and network-batch bytes), typed raw-input event encode/decode, `no_std` builds, zero-alloc steady-state emission, the **bidirectional event ring** (host → guest EVENT opcodes in shared memory, drained by the host via a generic `pathland_native_drain_events` C ABI — the renderer only writes + wakes), the **GTK4 renderer** (`pathland-render-gtk`) with Rust demos that render through it without touching GTK directly, the **native Java library set** — `com.pathland.view` (open `View` interface + SwiftUI-like DSL, **Angular-parity signals/computed/effects** with synchronous flush, fine-grained emitter with **node-level binding effects**, `PLPL` wire codec, lazy **JNA ring interop** into `libpathland_core`, cross-platform `StateStore` + **`State`/`PersistentState`** auto-wired by the `pathland-view-processor` annotation processor), `com.pathland.render.html` (JNA shim over the Rust HTML renderer), `com.pathland.state.redis` (Lettuce `RedisStateStore`) — and the **Quarkus** (`pathland-quarkus-demo`) **and Spring Boot** (`pathland-spring-boot-demo`) **SSR + WebSocket demos**, both consuming the same shared views (`pathland-demo-views`) with per-session `State` fields (Redis/in-memory). **`EVENT_LISTENERS`** (any element emits raw events via a u32 bitmask property) and **`onTapGesture`** (Rust + Java DSL modifier + app-side `TapRecognizer` over the raw pointer events). **Generic input routing** — the emitter returns tap-action / text-input / value-input registries (`RenderResult`), so the host forwards raw `EVENT` batches straight into the bound `State` signals and never reaches into a view's internals. **Tests green across the workspace.**

**In progress**: —

**Planned / deferred**: value-control (slider/switch) editing + rendering, image rendering, cross-platform state backends beyond Redis + in-memory (File/SQLite for desktop, LocalStorage for browser/WASM, NVS Flash for ESP32 — the `StateStore` contract is already platform-neutral).

---

## Quick Start

1. Read this file and `spec/OPCODE.md`
2. Run Rust tests: `cd lib/rust && cargo test` (runs all crates)
3. Build/test the Java libraries: `cd lib/java && mvn install`
4. Make small changes, verify they work

---

## Pathland Mindset

**Always remember**:
1. Protocol is King
2. Renderers are Dumb (execute commands only)
3. Applications are Smart (generate commands)
4. Only Changes Matter (transmit mutations / reactive emission)
5. Binary is Beautiful (efficient, deterministic)

**When in doubt**: Check `spec/OPCODE.md`, the conformance vectors in `spec/CONFORMANCE.md`, and the companion catalogs (`spec/PRIMITIVES.md`, `spec/MODIFIERS.md`, `spec/EVENTS.md`), and the reference implementation (`pathland-core`). Ask: "Does this maintain stateless renderers?" "Does this only transmit actual changes?" "Does this describe WHAT the UI is, never WHERE?" "Is this representable as a 16-byte opcode in any language?"
