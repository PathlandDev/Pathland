# pathland-view-kotlin — implementation status

**Last updated:** October 30, 2026

The idiomatic Kotlin authoring surface (`dev.pathland.view.dsl`). A thin facade over
the Java `pathland-view` DSL, signals, emitter, and wire codec, so Kotlin-authored
trees emit byte-identical opcodes.

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
  environment binding, styles, window metadata, and navigation intents. The
  Java `.modifiers(...)` operation remains available as an escape hatch.
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
  modifier property parity, fine-grained signal deltas, persisted/stateful bodies,
  reactive `ForEach`, two-way `TextField`, navigation swaps and link intents.

## Not implemented / gaps

- Reactive **layout value** combinations that need several signals at once are
  available through the Java config builders (`Grid.make { ... }`); the Kotlin
  named-argument overloads cover raw values plus the common single-signal cases.
- A Kotlin-native annotation processor is intentionally not used. `state(...)`
  works on `KotlinView` subclasses; a plain `View` implementation should use
  signals directly or extend `KotlinView`.
- No Kotlin Multiplatform target yet — the module is JVM-only over the Java
  library.
- `SecureField` is implemented in the Kotlin facade (`TEXT_FIELD` + `IS_SECURE`)
  because the Java `TextField.Config` does not yet expose `secure`.
