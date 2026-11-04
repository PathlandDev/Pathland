# pathland-view-kotlin — implementation status

**Last updated:** November 3, 2026

The sole JVM authoring surface (`dev.pathland.view.dsl`), Kotlin-owned over the
Kotlin `pathland-view-core`. The Java DSL, its processor, and its Java-only
Quarkus extension/starter alias are removed.

## Authoring surface

- **Kotlin-owned**: all primitives, shapes, stacks, grids, scroll/lazy
  containers, `ForEach`, `Button`, text/value controls, media, navigation,
  `Label`, `ViewThatFits`/`Fit`, `ProxyView`, styles (`PlainButtonStyle`,
  `BorderedButtonStyle`, label styles, `NativeVideoStyle`), and the complete
  modifier vocabulary (padding/frame/fonts/colors/borders/shadows/effects/
  transforms/interaction/accessibility/environment/styles/window metadata).
- **Dependency boundary**: main depends on `pathland-view-core` only.
- Tests are Kotlin-only; golden engine traces and opcode/property assertions
  replace the former Java-vs-Kotlin parity oracle.

## Implemented

- **SwiftUI-shaped builders**: `Text`, `Image`, `Icon`, `Color`, `Rectangle`,
  `Circle`, `Ellipse`, `Capsule`, `RoundedRectangle`, `Shape`, `Divider`, `Spacer`,
  `ProgressView`, `Gauge`, `Label`, `Group`.
- **Containers**: `VStack`, `HStack`, `ZStack`, `Grid`, `GridRow`, `ScrollView`,
  `LazyVStack`, `LazyHStack`, `LazyVGrid`, `LazyHGrid`, `ViewThatFits`/`Fit`,
  `ProxyView`.
- **Controls**: `Button` (title or label subtree), `TextField`, `SecureField`,
  `TextEditor`, `Toggle`, `Slider`, `Stepper`, `DatePicker`, `Picker`, `Menu`,
  `ColorPicker`.
- **Automatic child collection**: `VStack { Text("a"); Text("b") }` needs no
  `.children(...)`. A custom `Component` constructed inline in a block
  auto-registers; a view stored as a field is added explicitly with `+field`
  inside `body()` (it does not leak into the enclosing scope). An explicit
  `view.add()` / `+view` is available for Java-constructed or prebuilt views.
- **Signals**: `signal`, `computed`, `constant`, `effect`, `untracked`,
  `Signal<T>.value`, `WritableSignal<T>.value`, `Signal<T>.map`, `readonly`.
- **Modifier sugar**: `.padding`, `.frame`, `.offset`, `.position`, `.fixedSize`,
  layout priority/z-index/aspect ratio, the full text-formatting surface,
  foreground/background/border/corner radius/shadow, visual effects, transforms,
  visibility/disabled/hit-testing, accessibility, tap/pointer events,
  environment binding, styles, window metadata, and navigation intents. A child
  modifier replaces its auto-registered leaf in the enclosing builder, so
  `VStack { Text("x").fontSize(20f) }` keeps the modifier. The core
  `View.modifiers(...)` operation remains available as an escape hatch.
- **Persisted state**: `Component` base class + `val count by state(0)`
  (auto-keyed by property name) or `state(0, "key")`; a `mutableState(0)` signal
  form for two-way controls. Bound to the session's `PersistentState` before the
  first `body()` render — no kapt and no annotation processor.
- **`Signals.state`**: Components and services can create persisted signals with
  `Signals.state("key", initial)` while the framework's session state is active
  during render. Outside a state context it falls back to an in-memory signal.
- **Reactive bodies**: plain `if`/`when`/`for` in `body()`; `ForEach` over a static
  list or `Signal<List<T>>` with row-scoped child collection.
- **Navigation**: `navigator { route(...) }`, `NavigationStack`, `NavigationLink`,
  `isActive`, and `.navigate`/`.push`/`.replace` intents.
- **Media**: `Video(controller)` stays where the app places it; audio remains
  view-less. `controller.register()` projects a controller at the root (ordinary
  audio services call it while rendering).
- **Tests**: structure/opcode/property assertions, auto-collection,
  child-modifier application, modifier property parity, fine-grained signal
  deltas, persisted/stateful bodies, reactive `ForEach`, two-way `TextField`,
  navigation swaps and link intents, and media projection (audio root nodes,
  authored `VIDEO` placement, stable controller ids, and late nested videos).

## Naming

- The composite base is `Component` (renamed from `KotlinView` now that the Java
  DSL is gone). Whether to align the authoring type with SwiftUI's single `View`
  protocol is tracked by #174.

## Java hosting

- A Java Spring Boot / Quarkus app mounts a Kotlin view through the Java-friendly
  `PathlandApp` functional interface (`return () -> MusicPlayerView.make();`).
- Kotlin root views expose public constructors and/or `@JvmStatic make(...)`
  factories with Java-friendly parameter types.
- Tests in `pathland-spring-boot-starter` and `pathland-quarkus` prove a Java
  host renders a Kotlin fixture and receives live signal deltas.

## Not implemented / gaps

- No Kotlin Multiplatform target yet — the module is JVM-only over
  `pathland-view-core`.
- `SecureField` is Kotlin-owned (`TEXT_FIELD` + `IS_SECURE`).
