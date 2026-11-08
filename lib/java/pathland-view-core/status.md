# pathland-view-core (JVM engine + DSL) — implementation status

**Last updated:** November 8, 2026

The shared, framework-agnostic JVM engine, runtime and authoring DSL for the
Pathland protocol, written in Kotlin. It owns the `View` SPI and shared
authoring vocabulary, the retained-tree diff emitter,
signals/computed/effects, router, cross-platform state, and the
`dev.pathland.view.dsl` SwiftUI-shaped builders/modifiers. The opcode/wire layer
(`dev.pathland.core.protocol`, `.transport`, `.text`, `.markdown`) lives in the
pure-Java `pathland-core` module, which this module depends on. Server and
renderer modules depend on this module; Java hosts consume the Kotlin-authored
views through JVM interop. JNA ring interop lives in the opt-in
`pathland-native-core` module, so this module (and every server/native-image
build) carries no JNA.

## Implemented

- **Opcode/wire layer**: moved to the `pathland-core` module
  (`dev.pathland.core.protocol`, `.transport`, `.text`, `.markdown`). The DSL
  still re-exports the same behaviour through `Text(...)` and the emitter.
- **Signals** (`dev.pathland.view.core.signal`): `signal`/`state`/`computed`/
  `effect`/`untracked`, synchronous glitch-free flush, equality suppression, error
  caching/retry, circular-dependency detection, and a thread-safe global
  `Scheduler`. `Signals.state` binds to the ambient session `PersistentState`
  during a component render.
- **Topic subscriptions** (`dev.pathland.view.core.subscription`): a strongly
  typed `Topic<T, S>` and the DSL `topic(key, subject, initial)` (also
  `Signals.topic`). Scoped topics name an entity subject; `Topic.global(name)`
  declares an unscoped `Topic<T, GlobalSubject>` with the two-argument
  `topic(key, initial)` form. Both return a normal `Signal<T>` backed by a
  session-local mirror; the server runtime registers it with the app's hub and
  applies published values on the session actor. `SessionContext` (thread-local,
  installed by `PathlandSession` around every actor task) also exposes
  `unsubscribe` and `submit`. Resolution is lazy when a root view's field
  initializer runs before the context is installed.
- **State** (`dev.pathland.view.core.state`): `PersistentState`, untyped
  `StateStore`, in-memory store and provider. Kotlin `Component` state delegates
  bind directly before `body()`; the Java `State` field/binder machinery and
  annotation processor are removed.
- **Router** (`dev.pathland.view.core.router`): `Route`, `RouteTable`, `Router`,
  `Navigation`, `NavigationContainer`, `NavigationIntent`, `Params`, `Chrome`,
  `NavOp`. `NavigationLink` and the navigation helpers live in the Kotlin DSL.
- **JNA interop**: moved to `pathland-native-core` (`dev.pathland.nativecore`),
  so server runtimes do not pull JNA.
- **Emitter** (`dev.pathland.view.engine`): `Emitter`, `PathlandNode`, `ViewSlot`,
  `RenderResult`, `InputDispatcher`, `ReactiveValues`, `DateInput`, `MediaInput`.
  Implements mount/id assignment, diff-based reactive emission, node-level binding
  effects, structural reconciliation, fit/media/navigation routing, and the
  zero-opcode unchanged recompute contract. A replaced child is relinked to its
  real parent (not the tree root), so a `SIZE_THAT_FITS` swap cannot detach a
  pinned root `MEDIA_CONTROLLER` or drop its media routing. Registered `MediaController`s are
  projected as pinned, non-visual root children; `unregisterMediaController`
  removes them without touching the app tree.
- **Shared SPI/values** (`dev.pathland.view.core`): `View`, `ViewModifier`,
  `Configurable`, `ChildrenView`, `Modified`, `ViewBuilder`,
  `Environment*`, `Platform`, `Theme*`, `MediaController`, `WindowMetadata`,
  `ProxyConfig`, `Color`, `Length`, shared enums, style protocols, and modifier
  wrappers.
- **Shared engine traces**: the Kotlin DSL tests run the mount/text/property
  scenarios against the Rust-emitted PLPL fixtures under `spec/engine-traces/`,
  byte-for-byte. The canonical order lives in `spec/ENGINE.md`.
- **Tests**: `pathland-view-core` owns the signal/state and Kotlin DSL tests
  (which exercise the emitter end-to-end); `pathland-core` owns the
  protocol-manifest, frame-codec, and Markdown tests.

## Authoring DSL (`dev.pathland.view.dsl`)

- **SwiftUI-shaped builders**: `Text` (plain, `Signal<String>`, or
  `AttributedString` / `Signal<AttributedString>`), `Image`, `Icon`, `Color`,
  `Rectangle`, `Circle`, `Ellipse`, `Capsule`, `RoundedRectangle`, `Shape`,
  `Divider`, `Spacer`, `ProgressView`, `Gauge`, `Label`, `Group`.
- **Containers**: `VStack`, `HStack`, `ZStack`, `Grid`, `GridRow`, `ScrollView`,
  `LazyVStack`, `LazyHStack`, `LazyVGrid`, `LazyHGrid`, `ViewThatFits`/`Fit`,
  `ProxyView`, `ForEach`.
- **Controls**: `Button` (title or label subtree), `TextField`, `SecureField`,
  `TextEditor`, `Toggle`, `Slider`, `Stepper`, `DatePicker`, `Picker`, `Menu`,
  `ColorPicker`.
- **Automatic child collection**: `VStack { Text("a"); Text("b") }` needs no
  `.children(...)`. A custom `Component` constructed inline in a block
  auto-registers; a view stored as a field is added explicitly with `+field`.
- **Scope isolation** (`@PathlandDsl`): every view builder lambda takes a
  `PathlandScope` receiver, and `PathlandScope`, `Navigation.Builder`, and the
  modifier/style `Config` receivers carry the same `@DslMarker`. An outer
  router/config receiver therefore cannot be captured implicitly inside a
  nested view builder (e.g. `fallback(...)` inside `VStack { }` is a compile
  error). Child collection itself is unchanged — the thread-local `ViewScopes`
  stack still drives auto-registration.
- **Signals**: `signal`, `computed`, `constant`, `effect`, `untracked`,
  `topic(key, subject, initial)`, `Signal<T>.value`, `WritableSignal<T>.value`,
  `Signal<T>.map`, `readonly`.
- **Modifier sugar**: `.padding`, `.frame`, `.offset`, `.position`, `.fixedSize`,
  layout priority/z-index/aspect ratio, the full text-formatting surface,
  foreground/background/border/corner radius/shadow, visual effects, transforms,
  visibility/disabled/hit-testing, accessibility, tap/pointer events,
  environment binding, styles, window metadata, and navigation intents.
- **Persisted state**: `Component` base class + `val count by state(0)`
  (auto-keyed by property name) or `state(0, "key")`; a `mutableState(0)` signal
  form for two-way controls.
- **Reactive bodies**: plain `if`/`when`/`for` in `body()`; `ForEach` over a static
  list or `Signal<List<T>>` with row-scoped child collection.
- **Navigation**: `navigator { route(...) }`, `NavigationStack`, `NavigationLink`,
  `isActive`, and `.navigate`/`.push`/`.replace` intents.
- **Media**: `Video(controller)` stays where the app places it; audio remains
  view-less. `controller.register()` projects a controller at the root.
- **Naming**: the composite base is `Component` (renamed from `KotlinView` now
  that the Java DSL is gone); see #174.
- **Java hosting**: a Java Spring Boot / Quarkus app mounts a Kotlin view through
  the Java-friendly `PathlandApp` functional interface
  (`return () -> MusicPlayerView.make();`). Kotlin roots expose public
  constructors and/or `@JvmStatic make(...)` factories.

## Package boundary

- `pathland-core` is the pure-Java opcode/wire leaf: protocol, frame codec,
  events, opcode sink, attributed text, and Markdown.
- `pathland-view-core` is the Kotlin engine + DSL module: signal/state/router,
  the `View` SPI and value types, the retained-tree emitter, and
  `dev.pathland.view.dsl`. It depends on `pathland-core`.
- `core`/`engine`/`dsl` remain logical subpackages inside `pathland-view-core`,
  with a few deliberate intra-module references (`Color`/`MediaController`/
  `Modified` materialize `PathlandNode`). Splitting the engine out would still
  require a node SPI first.
- Production runtimes/renderers (`pathland-server-core`,
  `pathland-render-html`) depend on both modules. Framework starters remain
  DSL-neutral at compile time; they mount Kotlin-authored fixture views in test
  scope.
