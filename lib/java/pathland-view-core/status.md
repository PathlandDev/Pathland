# pathland-view-core (JVM engine) — implementation status

**Last updated:** November 1, 2026

The shared, framework-agnostic JVM engine and runtime for the Pathland protocol.
It owns the `View` SPI and shared authoring vocabulary, the retained-tree
diff emitter, signals/computed/effects, the wire codec, router, cross-platform
state, and JNA ring interop. The Java DSL (`pathland-view`) and the Kotlin DSL
(`pathland-view-kotlin`) are authoring layers over this module; server and
renderer modules depend on core only.

## Implemented

- **Protocol** (`dev.pathland.view.core.protocol`): `Components`, `Properties`,
  `Commands`, `Categories`, `ValueTypes`, `Opcode`, `ValueEncoder`, `WireValue`,
  `TokenPath`.
- **Signals** (`dev.pathland.view.core.signal`): `signal`/`computed`/`effect`/
  `untracked`, synchronous glitch-free flush, equality suppression, error
  caching/retry, circular-dependency detection, and a thread-safe global
  `Scheduler`.
- **State** (`dev.pathland.view.core.state`): `State`/`PersistentState`,
  untyped `StateStore`, in-memory store and provider.
- **Transport** (`dev.pathland.view.core.transport`): `ProtocolFrame`,
  `FrameCodec`, `Event`, `EnvironmentData`, `OpcodeSink`, `FrameOpcodeSink`.
- **Router** (`dev.pathland.view.core.router`): `Route`, `RouteTable`, `Router`,
  `Navigation`, `NavigationContainer`, `NavigationIntent`, `Params`, `Chrome`,
  `NavOp`. `NavigationLink` stays in the Java DSL because it composes `Button`.
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
- **Test coverage**: the Java DSL module's test suite exercises the engine
  end-to-end (emitter/reconcile, signals, state, router, transport, JNA ring),
  plus the server/HTML/desktop consumers.

## Package boundary

- `pathland-view-core` is one Maven module for now: `core`/`engine` are logical
  subpackages inside it, with a few deliberate intra-module references
  (`Color`/`MediaController`/`Modified` materialize `PathlandNode`). Splitting
  `core` and `engine` into separate artifacts requires a node SPI first.
- Production runtimes/renderers (`pathland-server-core`,
  `pathland-render-html`, `pathland-state-redis`, `pathland-test-kit`) depend on
  core, not on the Java DSL. Tests that author views through the DSL add
  `pathland-view` in test scope. The app-facing starters do depend on
  `pathland-view` because application code authors its root `View` with the DSL.
