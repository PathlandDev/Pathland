# pathland-view-core (JVM engine) — implementation status

**Last updated:** November 3, 2026

The shared, framework-agnostic JVM engine and runtime for the Pathland protocol,
now written entirely in Kotlin. It owns the `View` SPI and shared authoring
vocabulary, the retained-tree diff emitter, signals/computed/effects, the wire
codec, router, cross-platform state, and JNA ring interop. `pathland-view-kotlin`
is the sole authoring layer; server and renderer modules depend on core only.
Java hosts consume the same API through Kotlin/JVM interop.

## Implemented

- **Protocol** (`dev.pathland.view.core.protocol`): `Components`, `Properties`,
  `Commands`, `Categories`, `ValueTypes`, `Opcode`, `ValueEncoder`, `WireValue`,
  `TokenPath`.
- **Signals** (`dev.pathland.view.core.signal`): `signal`/`computed`/`effect`/
  `untracked`, synchronous glitch-free flush, equality suppression, error
  caching/retry, circular-dependency detection, and a thread-safe global
  `Scheduler`.
- **State** (`dev.pathland.view.core.state`): `PersistentState`, untyped
  `StateStore`, in-memory store and provider. Kotlin `Component` state delegates
  bind directly before `body()`; the Java `State` field/binder machinery and
  annotation processor are removed.
- **Transport** (`dev.pathland.view.core.transport`): `ProtocolFrame`,
  `FrameCodec`, `Event`, `EnvironmentData`, `OpcodeSink`, `FrameOpcodeSink`.
- **Router** (`dev.pathland.view.core.router`): `Route`, `RouteTable`, `Router`,
  `Navigation`, `NavigationContainer`, `NavigationIntent`, `Params`, `Chrome`,
  `NavOp`. `NavigationLink` and the navigation helpers live in the Kotlin DSL.
- **JNA interop** (`dev.pathland.view.core.ffm`): lazy `PathlandCore` binding and
  `RingOpcodeSink` over `libpathland_core`.
- **Emitter** (`dev.pathland.view.engine`): `Emitter`, `PathlandNode`, `ViewSlot`,
  `RenderResult`, `InputDispatcher`, `ReactiveValues`, `DateInput`, `MediaInput`.
  Implements mount/id assignment, diff-based reactive emission, node-level binding
  effects, structural reconciliation, fit/media/navigation routing, and the
  zero-opcode unchanged recompute contract.
- **Shared SPI/values** (`dev.pathland.view.core`): `View`, `ViewModifier`,
  `Configurable`, `ChildrenView`, `Modified`, `ViewBuilder`, `Configurators`,
  `Environment*`, `Platform`, `Theme*`, `MediaController`, `WindowMetadata`,
  `ProxyConfig`, `Color`, `Length`, shared enums, style protocols, and modifier
  wrappers.
- **Protocol manifest validation** (`ProtocolManifestTest`): the hand-written
  protocol constants (`Components`, `Properties`, `Commands`, `Categories`,
  `ValueTypes`) are checked against `spec/protocol.tsv` — including no extra
  public numeric constants and the full `ValueTypes.forProperty` mapping. No
  Java source is generated.
- **Shared engine traces**: the Kotlin DSL tests run the mount/text/property
  scenarios against the Rust-emitted PLPL fixtures under `spec/engine-traces/`,
  byte-for-byte. The canonical order lives in `spec/ENGINE.md`.
- **Tests**: `pathland-view-core` owns the signal, state, frame-codec, and
  protocol-manifest tests; Kotlin DSL tests exercise the emitter end-to-end, and
  the server/HTML/desktop consumers cover their integration paths.

## Package boundary

- `pathland-view-core` is one Maven module for now: `core`/`engine` are logical
  subpackages inside it, with a few deliberate intra-module references
  (`Color`/`MediaController`/`Modified` materialize `PathlandNode`). Splitting
  `core` and `engine` into separate artifacts requires a node SPI first.
- Production runtimes/renderers (`pathland-server-core`,
  `pathland-render-html`, `pathland-state-redis`, `pathland-test-kit`) depend on
  core. Java-module tests add `pathland-view-kotlin` in test scope and mount
  Kotlin-authored fixture views; production starters remain DSL-neutral.
