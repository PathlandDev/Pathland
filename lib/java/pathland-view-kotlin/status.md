# pathland-view-kotlin — implementation status

**Last updated:** November 3, 2026

The idiomatic Kotlin authoring surface (`dev.pathland.view.dsl`). Phase 3 is
complete: every production view/modifier is Kotlin-owned over
`pathland-view-core`; the Java DSL is a test-scope parity oracle only.

## Phase 3

- **Core-owned in Kotlin**: all primitives, shapes, stacks, grids, scroll/lazy
  containers, `ForEach`, `Button`, text/value controls, media, navigation,
  `Label`, `ViewThatFits`/`Fit`, `ProxyView`, and the complete modifier
  vocabulary (padding/frame/fonts/colors/borders/shadows/effects/transforms/
  interaction/accessibility/environment/styles/window metadata).
- **Dependency boundary**: main depends on `pathland-view-core` only; the Java
  DSL is test-scope for byte-for-byte parity comparisons.
- Tests compare the Kotlin-owned views and modifiers against the Java DSL.

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
  `.children(...)`. A custom `KotlinView` constructed inline in a block
  auto-registers; a view stored as a field is added explicitly with `+field`
  inside `body()` (it does not leak into the enclosing scope). An explicit
  `view.add()` / `+view` is available for Java-constructed views (e.g.
  `mediaController.attach()`).
- **Signals**: `signal`, `computed`, `constant`, `effect`, `untracked`,
  `Signal<T>.value`, `WritableSignal<T>.value`, `Signal<T>.map`, `readonly`.
- **Modifier sugar**: `.padding`, `.frame`, `.offset`, `.position`, `.fixedSize`,
  layout priority/z-index/aspect ratio, the full text-formatting surface,
  foreground/background/border/corner radius/shadow, visual effects, transforms,
  visibility/disabled/hit-testing, accessibility, tap/pointer events,
  environment binding, styles, window metadata, and navigation intents. A child
  modifier replaces its auto-registered leaf in the enclosing builder, so
  `VStack { Text("x").fontSize(20f) }` keeps the modifier. The Java
  `.modifiers(...)` operation remains available as an escape hatch.
- **Persisted state**: `KotlinView` base class + `val count by state(0)`
  (auto-keyed by property name) or `state(0, "key")`; a `mutableState(0)` signal
  form for two-way controls. Bound to the session's `PersistentState` before the
  first `body()` render — no kapt and no annotation processor.
- **Reactive bodies**: plain `if`/`when`/`for` in `body()`; `ForEach` over a static
  list or `Signal<List<T>>` with row-scoped child collection.
- **Navigation**: `navigator { route(...) }`, `NavigationStack`, `NavigationLink`,
  `isActive`, and `.navigate`/`.push`/`.replace` intents.
- **Media**: `Video(controller)`; audio remains view-less (`controller.attach()`
  can be added with `+`).
- **Tests**: byte-identical Kotlin-vs-Java frame comparison, auto-collection,
  child-modifier application, modifier property parity, fine-grained signal
  deltas, persisted/stateful bodies, reactive `ForEach`, two-way `TextField`,
  navigation swaps and link intents.

## Not implemented / gaps

- The Kotlin modifier config types (`Padding`, `Frame`, `Border`, `Shadow`,
  `ClipShape`, `Font`) are Kotlin ownership types; code migrating from the Java
  DSL should use the Kotlin `Modifiers` sugar instead of Java config classes.
- A Kotlin-native annotation processor is intentionally not used. `state(...)`
  works on `KotlinView` subclasses; a plain `View` implementation should use
  signals directly or extend `KotlinView`.
- No Kotlin Multiplatform target yet — the module is JVM-only over
  `pathland-view-core`.
- `SecureField` is Kotlin-owned (`TEXT_FIELD` + `IS_SECURE`).
