# Pathland DSL Authoring Contract

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** September 3, 2026

---

## Purpose

This document defines the **authoring surface** — the SwiftUI-shaped DSL an
application developer writes — for every Pathland implementation. Where
[PRIMITIVES.md](./PRIMITIVES.md), [MODIFIERS.md](./MODIFIERS.md), and
[EVENTS.md](./EVENTS.md) describe the **wire surface** (component types,
property ids, events, opcodes), this file describes the **surface in source
code**: how views, controls, modifiers, and state are *named*, *ordered*, and
*composed* so that any language can expose a conformant, idiomatic DSL.

A DSL in a new language is **conformant** when it:

1. exposes the full surface catalogued here (views, controls, modifiers,
   signals, state);
2. maps every DSL element to the exact protocol component/property/event
   documented in the companion specs (no re-interpretation, no reallocation);
3. produces **byte-identical opcode output** for the same source tree as the
   reference implementations (see the [Generation
   contract](#9-generation-contract-for-a-new-language)).

### Relationship to the other specs

| File | Role |
|------|------|
| [OPCODE.md](./OPCODE.md) | Wire format: categories, commands, 16-byte opcode layout, ring/arena, value types |
| [PRIMITIVES.md](./PRIMITIVES.md) | Which **views/nodes** exist and their component ids |
| [MODIFIERS.md](./MODIFIERS.md) | Which **modifiers** (properties) exist and how they encode |
| [EVENTS.md](./EVENTS.md) | Which **core events** (raw inputs) exist and how they encode |
| [CONFORMANCE.md](./CONFORMANCE.md) | Golden byte vectors |
| [TOKENS.md](./TOKENS.md) | Design-token catalog, resolution, and color-scheme contract |
| **DSL.md** (this file) | How an application **author** composes those into source code |

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library), **not** in this specification. The Java DSL
> (`com.pathland.view`) and the Rust DSL (`pathland-view`) are the two reference
> realizations and are cited here by their **current signatures**; where they
> diverge from the canonical form below, that divergence is noted as a *delta*,
> and [§8](#8-java-dsl-convergence-adopted) records the adopted Java convergence.
> This document defines the contract only.

### Two guiding rules

1. **Stay as close to SwiftUI as possible.** View names, modifier names, and
   signature shapes mirror SwiftUI. Adapt only where the host language forces
   it (Rust snake_case + macros; Java camelCase + static factories), and never
   in a way that reorders or renames the canonical parameters.
2. **State variables are signals.** Pathland does **not** use SwiftUI's
   `@State`/`@Binding` property wrappers. The view syntax is SwiftUI; the
   reactivity model is Angular-style signals (writable signals, computeds,
   effects, two-way bindings). A generated DSL MUST expose the signal surface
   of [§3](#3-state-model-signals).

---

## 1. Design principles (DSL-flavored)

These recap the protocol's non-negotiables in author-facing terms. A conformant
DSL must be *shaped* by them:

- **Declarative structure, never positions.** The DSL expresses `WHAT` — a
  `VStack`, a `HStack`, a `Text` — and constraint properties (spacing, padding,
  alignment, size hints). It never computes bounds and never emits rects.
- **`body()` is evaluated once at mount.** A composite view declares its
  subtree once; reactivity comes from **signals**, never from re-evaluating the
  body. This is what lets the emitter produce fine-grained
  `SET_TEXT`/`SET_PROPERTY`/`SET_DATE` deltas instead of rebuilding the tree.
  The one sanctioned exception is a **structural container**
  ([§3.4](#34-structural-reactivity-conditional-rendering)): its content is
  re-evaluated on a signal change and the emitter reconciles the retained
  subtree into `TREE` deltas.
- **Modifiers are decoupled from views — and never hard-bound to a view
  type.** Any modifier applies to any view (`.padding` works on a `Text` and a
  `VStack` alike), and any developer can **author a custom modifier in
  application code** (a `ViewModifier`) that applies to any existing view —
  library primitive, container, control, custom view, or an already-modified
  view (see [§5.6](#56-custom-modifiers-developer-authored)). A modifier a
  given renderer cannot apply is **allowed and ignored** — the property is
  still emitted.
- **There is exactly one modifier mechanism.** Built-in core modifiers are
  **not** a separate syntax: they are `ViewModifier` values the library ships,
  and the sugar methods (`.padding(_:)`, `.foregroundStyle(_:)`) are
  conveniences that construct them via `.modifier(...)`. Core and
  application-authored modifiers share the same surface.
- **Constructor properties vs modifiers.** Structural/layout parameters
  (`alignment`, `spacing`) are **constructor arguments**, never chainable.
  Everything decorative (padding, color, font, frame, border, …) is a
  **chainable modifier**. There is no standalone width/height — use the
  compound `frame` modifier.
- **`Color` is never a modifier.** `Color` has SwiftUI's dual identity: it is a
  **View** (a layout-greedy solid-color fill) and a **Data Type** (passed into
  style-taking modifiers: `.foregroundStyle`, `.background`, `.border`,
  `.tint`). There is **no** `.color()` modifier, and `.foregroundColor(_:)` is
  deprecated — foreground styling is always `.foregroundStyle(_:)`.
- **Only changes are transmitted.** Emission is diff-based; an unchanged tree
  emits **zero** opcodes. The DSL describes values; the emitter diffs them.
- **Typed enums, never raw ints.** Every enumerated value is a typed constant
  (`Alignment.leading`, `FontWeight.bold`, `ToggleStyle.checkbox`), not a bare
  integer. The numeric code is the DSL's job to resolve (see the value-type and
  enum tables in MODIFIERS.md).

---

## 2. Canonical signature notation

The canonical signatures below are written SwiftUI-shaped and language
agnostic. Conventions used:

- `T("…")` / `View("…")` — a view/control constructor.
- `{ ... }` — the child subtree (a trailing closure in SwiftUI; a vararg,
  macro, builder, or lambda in other languages).
- `Signal<T>` — a read-only derived or writable signal; `WritableSignal<T>` —
  a two-way binding target. A control takes a `WritableSignal<T>` for its
  value (see [§3](#3-state-model-signals)).
- `()` — the action a `Button` fires.
- `.modifier(name)` — apply a **modifier value** to a view; chainable on any
  view. Every modifier — built-in or application-authored — is such a value
  ([§5.6](#56-custom-modifiers-developer-authored)); the sugar names
  (`.padding`, `.foregroundStyle`, …) are shorthand that construct the
  built-in values.
- Modifiers chain as `.name(args)` on any view and are applied
  **innermost-first** (the last chained modifier's property wins).

Per-language adaptation rules:

| Language | Case | Composition mechanism | Example |
|----------|------|-----------------------|---------|
| SwiftUI | `camelCase` | trailing closures + result builder | `VStack { Text("x").padding() }` |
| Java (`com.pathland.view`) | `camelCase` | `.of()` factories + varargs + `.modifier(...)`/`.modifiers(...)` | `VStack.of(Text.of("x").modifier(Padding.of(16)))` |
| Rust (`pathland-view`) | `snake_case` | macros + free functions + chainable trait methods | `vstack![text("x").padding(16.0)]` |

---

## 3. State model (signals)

Pathland state is **Angular-style signals**. This is the non-negotiable
reactive core every DSL exposes.

### 3.1 Signal surface

| Canonical | Java DSL (`com.pathland.view.signal`) | Semantics |
|-----------|--------------------------------------|-----------|
| `signal(initial)` | `Signals.signal(T)` | a writable signal |
| `signal(name, initial)` | `Signals.signal(String, T)` | named (names surface in dependency errors) |
| `signal(initial, equal)` | `Signals.signal(T, BiPredicate<T,T>)` | custom equality |
| `computed(fn)` | `Signals.computed(Supplier<T>)` | lazy, memoized derived signal |
| `effect(fn)` | `Signals.effect(Runnable)` | runs immediately, then on dependency change |
| `untracked(fn)` | `Signals.untracked(Supplier<T>)` | reads without recording dependencies |
| `read()` | `get()` | current value |
| `write(v)` | `set(T)` | replace (no-op when equal) |
| `update(fn)` | `update(UnaryOperator<T>)` | derive from current and write |
| `asReadonly()` | `asReadonly()` | read-only view of a writable signal |

**Guarantees** (must be preserved by any implementation): equality suppression,
synchronous flush at the end of the outermost write, glitch-free propagation
(each computed recomputes at most once per flush), error caching, write
discipline, and circular-dependency detection.

### 3.2 Two-way binding

A control's value is **a `WritableSignal<T>` passed to its constructor**:
`TextField(placeholder, writable)`, `Toggle(label, isOn: writable)`,
`Slider(value: writable, in: min...max)`. The flow:

1. At **mount**, the DSL control records a **value input** (a sink on the
   retained node) for the signal, and the emitter exposes routing registries to
   the host: tap actions, text inputs, value inputs, date inputs.
2. The host receives the control's raw event (`TEXT_CHANGED`,
   `VALUE_CHANGED`, `DATE_CHANGED`, or a composed tap) and calls the sink,
   which **writes into the signal**.
3. The signal flush re-emits **only that node's** delta (`SET_TEXT`,
   `SET_PROPERTY`, `SET_DATE`) back through the emitter.

Reading is equally fine-grained: a node's text or a property can be **bound to
a signal** (`Text(signal)`, `.foregroundStyle(signal)`, `.fontSize(signal)`),
so a change re-emits only the bound node.

### 3.3 Persisted state (`State<T>`)

A `State<T>` field is the SwiftUI-`@State`-shaped form of a persisted signal,
**auto-wired by key**:

```java
State<Integer> count = new State<>(0);            // key = field name "count"
State<Integer> total = new State<>(0, "total");   // explicit store key
count.get(); count.set(0); count.update(v -> v + 1);
count.signal();                                   // the WritableSignal (for TextField, …)
```

The store (`StateStore`) is untyped and platform-neutral (in-memory, Redis,
file, SQLite, LocalStorage, NVS flash). The Java realization wires `State`
fields via an annotation processor that generates a `<View>_StateBinder` per
view class; connection happens when the view renders. A generated DSL in a
language without reflection/annotation processing MUST offer the equivalent
explicit wiring.

### 3.4 Structural reactivity (conditional rendering)

The tree is normally static once mounted (["`body()` is evaluated once at
mount"](#1-design-principles-dsl-flavored)); the **only** sanctioned way to
change *structure* reactively is a **structural container**: a slot whose
single child subtree is selected by a signal and **reconciled** when the
signal changes. This is the foundation for `if`/`else`, `switch`, and
navigation ([§4.5](#45-navigation)).

| Canonical (SwiftUI-shaped) | Java DSL (current) | Rust DSL (current) | Emits |
|----------------------------|--------------------|--------------------|-------|
| `if cond { then } else { else }` in a result builder | `Conditional.when(Signal<Boolean>, View then, View else)` | plain `if`/`match` in `build()` | `TREE` deltas (reconcile) |
| `switch value { case a -> v; default -> d }` | `Conditional.when(Signal<T>, Case.of(T, View)...)` + `Case.otherwise(View)` | plain `if`/`match` in `build()` | `TREE` deltas (reconcile) |

**Java realization** (`com.pathland.view.Conditional`): statically importable
lowercase factories on a final class with a private constructor, mirroring
[`Signals`](#31-signal-surface):

```java
import static com.pathland.view.Conditional.when;
import static com.pathland.view.Conditional.Case;

when(showLogin, LoginView.of(), HomeView.of());              // if / else
when(mode,
    Case.of(RouteMode.HOME, HomeView.of()),
    Case.of(RouteMode.USERS, UsersView.of()),
    Case.otherwise(NotFoundView.of()));                      // switch + default
```

The names `if`, `switch`, `case`, and `else` are Java reserved keywords, so
`when` (Kotlin's `switch` analog, and a valid Java identifier) is the method
name, and `Case.of(...)` / `Case.otherwise(...)` carry the branches. A boolean
signal takes the two-branch overload (`then`, `else`); an enum/int/string
signal takes keyed `Case` branches typed to the signal's value type, with an
optional `Case.otherwise` default.

**Emission contract** (the body-once exception, formalized):

1. A structural container holds a **stable slot node** (a `Group`, see
   [§8](#8-java-dsl-convergence-adopted)) whose single child is the currently
   selected content. Selection is a **content function** over the selector
   signal — `if`/`else` and `switch` are sugar over it, so value-parametrized
   content (e.g. a route param) is a first-class case.
2. On selector change the container re-evaluates the content function,
   **renders the new subtree**, and the emitter **reconciles** it against the
   retained snapshot — emitting only `TREE` deltas (`CREATE_NODE` /
   `DELETE_NODE` / `INSERT_CHILD` / `REMOVE_CHILD` / `MOVE_CHILD`).
3. **Identical structure emits zero opcodes.** The reconcile diffs old vs new;
   a recompute that yields the same structure produces no `TREE` deltas, so no
   equality predicate is needed on the selector signal.
4. Structural containers **nest**: a container inside a container re-evaluates
   and reconciles its own slot independently.
5. All other reactivity stays fine-grained (`SET_TEXT` / `SET_PROPERTY`); a
   structural container never re-emits siblings or ancestors.

**Rust delta**: the Rust DSL builds `Node` trees directly and the host rebuilds
+ re-`assign_id`s + diffs on every interaction, so plain `if`/`match` in
`build()` already produces the same reconcile — no wrapper slot and no special
type. An optional `switch!` macro is sugar.

---

## 4. View surface

Component ids and wire behavior come from
[PRIMITIVES.md](./PRIMITIVES.md); the tables here give the **DSL signature**.
`Java DSL (current)` is the reference realization today.

### 4.1 Primitive drawing & visual nodes

| View | Canonical (SwiftUI-shaped) | Java DSL (current) | Emits / Binds |
|------|---------------------------|--------------------|---------------|
| `Text` | `Text("…")` / `Text(Signal<String>)` | `Text.of(String)` / `Text.of(Signal<String>)` | `TEXT` 0x01; content `SET_TEXT` |
| `Image` | `Image("name")` / `Image(systemName:)` | `Image.of()` / `Image.of(String source)` | `IMAGE` 0x02; `IMAGE_SOURCE` 0x1002 |
| `Color` | `Color(.sRGB, red:green:blue:)` (a View) | `Color.rgb(int,int,int)` (implements `View`) | `COLOR` 0x03; `COLOR` 0x100A |
| `Shape` | `Rectangle()`, `Circle()`, `Capsule()`, `RoundedRectangle(cornerRadius:)` | `Rectangle.of()` | `SHAPE` 0x04; `SHAPE_KIND` 0x0006 |
| `Divider` | `Divider()` | `Divider.of()` | `DIVIDER` 0x05 |
| `Spacer` | `Spacer()` | `Spacer.of()` | `SPACER` 0x06 |
| `ProgressView` | `ProgressView(value:)` / `ProgressView()` | `ProgressView.of(float)` / `ProgressView.of()` | `PROGRESS_VIEW` 0x07; `PROGRESS` 0x200E / `IS_INDETERMINATE` 0x200F |
| `Gauge` | `Gauge(value:in:)` | `Gauge.of(float value, float min, float max)` | `GAUGE` 0x08; `VALUE`/`MIN_VALUE`/`MAX_VALUE` |
| `Label` | `Label("title", systemImage:)` (composite) | `Label.of(String title)` / `Label.of(String title, String icon)` / `Label.of(Signal<String>…)` | a composite `HSTACK` + `IMAGE` + `TEXT` (PRIMITIVES.md "Composite views") — no component ID |

**Deltas (Java)**: primitives are constructed with `<ViewName>.of(...)`
factories (`Text.of("…")`). Full `ShapeKind` coverage ships as named views
(`Rectangle.of()`, `Circle.of()`, `Capsule.of()`, `Ellipse.of()`,
`RoundedRectangle.of(cornerRadius:)`) plus generic `Shape.of(ShapeKind)` for
`Path`. `Label` is a **composite** (PRIMITIVES.md): an `HStack` of an optional
`IMAGE` and an optional `TEXT`; the scoped `labelStyle` ([§5.5](#55-interaction--state))
decides which parts render, and the title always drives the composite's
accessibility label.

### 4.2 Layout & container nodes

| View | Canonical (SwiftUI-shaped) | Java DSL (current) | Emits / Binds |
|------|---------------------------|--------------------|---------------|
| `VStack` | `VStack(alignment:spacing:) { … }` | `VStack.of(View...)` / `VStack.of(Alignment, float, View...)` | `VSTACK` 0x10; `SPACING` 0x0001, `ALIGNMENT` 0x0002, `CONTENT_MARGINS` 0x0005 |
| `HStack` | `HStack(alignment:spacing:) { … }` | `HStack.of(View...)` / `HStack.of(Alignment, float, View...)` | `HSTACK` 0x11 |
| `ZStack` | `ZStack(alignment:) { … }` | `ZStack.of(View...)` | `ZSTACK` 0x12; `ALIGNMENT` |
| `Grid` | `Grid(alignment:horizontalSpacing:verticalSpacing:) { … }` | `Grid.of(View...)` | `GRID` 0x13 |
| `ScrollView` | `ScrollView { … }` | `ScrollView.of(View...)` | `SCROLLVIEW` 0x14 |
| `LazyVGrid` | `LazyVGrid(columns:alignment:spacing:) { … }` | `LazyVGrid.of(View...)` | `LAZY_VGRID` 0x15 |
| `LazyHGrid` | `LazyHGrid(rows:alignment:spacing:) { … }` | `LazyHGrid.of(View...)` | `LAZY_HGRID` 0x16 |
| `LazyVStack` | `LazyVStack(alignment:spacing:) { … }` | `LazyVStack.of(View...)` | `LAZY_VSTACK` 0x1B |
| `LazyHStack` | `LazyHStack(alignment:spacing:) { … }` | `LazyHStack.of(View...)` | `LAZY_HSTACK` 0x1C |

**Deltas (Java)**: stacks are constructed with the `<ViewName>.of(...)` factory
using varargs (`VStack.of(children...)`), not a builder/trailing-closure
block. Constructor layout properties come as a separate overload
(`VStack.of(Alignment, float, View...)`). The canonical SwiftUI form passes
`alignment`/`spacing` as labeled constructor arguments inside the braces' call.

### 4.3 Semantic controls

Two-way value controls bind a `WritableSignal`. Actions bind a callback.

| Control | Canonical (SwiftUI-shaped) | Java DSL (current) | Emits / Binds |
|---------|---------------------------|--------------------|---------------|
| `Button` | `Button("title", action)` / `Button(action:label:)` | `Button.of(String, Runnable)` / `Button.of(View, Runnable)` | `BUTTON` 0x20; tap = composed `POINTER_DOWN`+`POINTER_UP` |
| `TextField` | `TextField("placeholder", text: writable)` | `TextField.of(String, WritableSignal<String>)` | `TEXT_FIELD` 0x21; `TEXT_CHANGED` → text input |
| `SecureField` | `SecureField("…", text: writable)` | (secure via `IS_SECURE` token) | `TEXT_FIELD` 0x21 + `IS_SECURE` 0x200D |
| `TextEditor` | `TextEditor(text: writable)` | `TextEditor.of(WritableSignal<String>)` | `TEXT_EDITOR` 0x22 |
| `Toggle` | `Toggle("label", isOn: writable)` | `Toggle.of(boolean, WritableSignal<Boolean>)` / `Toggle.of(String label, WritableSignal<Boolean>)` / `Toggle.of(ToggleStyle, boolean, WritableSignal<Boolean>, String)` | `TOGGLE` 0x24; `VALUE_CHANGED` → value input; `TOGGLE_STYLE` 0x2018 |
| `Slider` | `Slider(value: writable, in: min...max)` | `Slider.of(WritableSignal<Float>, float min, float max)` | `SLIDER` 0x25; `VALUE_CHANGED` → value input |
| `Stepper` | `Stepper("label", value: writable, in: min...max, step:)` | `Stepper.of(WritableSignal<Float>, float min, float max, float step)` | `STEPPER` 0x26; `VALUE_CHANGED` → value input |
| `DatePicker` | `DatePicker("label", selection: writable, displayedComponents:)` | `DatePicker.of(DatePickerMode, WritableSignal<Integer>)` | `DATE_PICKER` 0x27; `DATE_CHANGED` → date input; value via `STYLE::SET_DATE` |
| `Picker` | `Picker("label", selection: writable) { options }` | `Picker.of(PickerStyle, WritableSignal<Integer>, View... options)` | `PICKER` 0x28; `VALUE_CHANGED` (index) → value input |
| `Menu` | `Menu { actions } label: { trigger }` | `Menu.of(View trigger, View... actions)` / `Menu.of(View, WritableSignal<Integer>, View...)` | `MENU` 0x29; `VALUE_CHANGED` (item index) → value input |
| `ColorPicker` | `ColorPicker("label", selection: writable)` | `ColorPicker.of(WritableSignal<Color>)` | `COLOR_PICKER` 0x2A; `VALUE_CHANGED` (packed `0xAARRGGBB` as f32) → value input |

**Deltas (Java)**: the value binding is **binding-first** in `.of(...)` —
`Slider.of(binding, min, max)` reads the initial value from the signal (the
single source of truth) instead of SwiftUI's labeled `value:in:` range
binding. `Button` takes a `(label, action)` pair rather than SwiftUI's
`Button("title") { action }` trailing-action form. `Toggle` offers the
SwiftUI-closer `of(label, isOn)`; `DatePicker` binds **days-since-epoch**
(`WritableSignal<Integer>`) rather than a date object — the protocol's
`SET_DATE`/`DATE_CHANGED` two-field encoding is days + millis-of-day (see
[§8](#8-java-dsl-convergence-adopted)).

**Control guarantees** (from PRIMITIVES.md): a control without children renders
in **Native Token Mode** (native OS control); a control with a child subtree
renders in **Composite Override Mode** (custom chrome wrapped with native
gesture semantics). Renderers MUST gate value/text/date events by
`BINDING_ID`, `ACTION_ID`, or the `EVENT_LISTENERS` bitmask (transport-aware
event guards) — see [EVENTS.md](./EVENTS.md).

### 4.4 Gestures

| Gesture | Canonical (SwiftUI-shaped) | Java DSL (current) | Rust DSL (current) |
|---------|---------------------------|--------------------|--------------------|
| tap | `.onTapGesture { action }` | `.modifier(TapGesture.of(Runnable))` | `.on_tap_gesture(f)` |
| raw pointer | `.gesture` composition from raw inputs | `.modifier(PointerEvents.of(int mask))` | `.pointer_events(u32 mask)` |

Tap is **not** a protocol event: it is composed app-side from `POINTER_DOWN`
then `POINTER_UP` on the same target (`EVENT_LISTENERS` bits 0|2). The
`TapGesture` modifier value declares those listeners and records the action;
the host routes the recognized tap via the emitter's tap-action registry.

### 4.5 Navigation

Navigation is **structural reactivity over a route signal**
([§3.4](#34-structural-reactivity-conditional-rendering)): a
`NavigationContainer` is a structural container whose content function is
route-table matching. The wire surface is minimal and entirely optional:
`ROUTE` (MODIFIERS.md `0x2019`, a STRING current-path property on the slot),
`NAV_DEPTH` (MODIFIERS.md `0x201A`, a U32 back-stack-depth property on the
slot), `NAV_CHROME` (MODIFIERS.md `0x201B`, an F32-enum chrome-mode property
on the slot), `TRANSITION` (MODIFIERS.md `0x1031`, a presentation hint), and
the `NAVIGATE` event (EVENTS.md `0x0E`, host→guest). Renderers stay
stateless: they render whatever destination subtree the app emits and **may**
animate a swap when the `TRANSITION` hint is present, but must render normally
when they ignore it.

**Navigation is opt-in — the developer declares it by inserting a
`NavigationContainer`.** A tree without one has no navigation and the renderer
adds none. The container's chrome mode decides who supplies the navigation UI:

- `PlatformDefault` (default): the **renderer** supplies the chrome — the
  platform's native navigation container where one exists (GTK
  `AdwNavigationView`, SwiftUI `NavigationStack`, Compose `NavHost`), and a
  renderer-drawn back affordance where none exists (the DOM renderer shows a
  back button once `NAV_DEPTH > 1`; a no-JS SSR page falls back to the browser
  back button). `NavigationContainer.of(router)`.
- `Custom`: the **developer owns all navigation UI** — they draw their own
  back buttons / bars in the destinations and call `router.back()` /
  `navigate(...)` directly; the renderer adds no chrome (no native header-bar
  back button, no DOM back button). `NavigationContainer.of(router, Chrome.CUSTOM)`.
  Renderers treat a missing `NAV_CHROME` as `PlatformDefault`.

| View | Canonical (SwiftUI-shaped) | Java DSL (current) | Rust DSL (current) | Emits / Binds |
|------|----------------------------|--------------------|--------------------|---------------|
| `NavigationContainer` | `NavigationStack(path:) { destination(for:) }` | `NavigationContainer.of(Router)` / `.of(Router, Chrome.CUSTOM)` | `NavigationContainer::new(Router)` | a `Group` slot + `ROUTE` `0x2019`, `NAV_DEPTH` `0x201A`, `NAV_CHROME` `0x201B`, `TRANSITION` `0x1031`; destination swap = `TREE` deltas |
| `NavigationLink` | `NavigationLink("label", value:)` | `NavigationLink.of(String, Router, String to)` / `NavigationLink.of(String, String to)` (router-agnostic) | `navigation_link(...)` | a `BUTTON` whose tap pushes `to` (via the router, or resolved to the nearest enclosing router when router-agnostic) |
| `RouteTable` | — | `RouteTable` (builder), or `Navigation.navigator(...)` (the ergonomic facade) | `RouteTable::new(...)` | none (app-side matching) |

**Router state** — app-owned (never renderer state):

- `Route` = absolute path + path params (`/users/:id` → `{id:"42"}`) + query.
  Path params are strings; typed params are a DSL concern.
- `RouteTable` maps path patterns to destination factories, captures params,
  and runs **guards**; a guard redirects via `replace()`. Factories are lazy —
  in the SSR model the client only ever receives the chosen destination's
  deltas.
- `Router` owns a `Signal<Route>` plus a **back-stack** and exposes
  `navigate` / `push` / `pop` / `replace` / `back`. `push` appends; `pop` /
  `back` step back; `navigate` / `replace` set the current route. The current
  path is emitted as the `ROUTE` property on the container slot.
- The route is a **plain signal — not persisted**. On the web the URL is the
  persistence layer (the DOM client mirrors it via `pushState`/`popstate`); on
  native the route is per-session.
- The initial route is delivered as a **`META::ENVIRONMENT` `ROUTE` field**
  (host → guest platform environment, OPCODE.md — the same message that carries
  the viewport): on SSR the host synthesizes it from the HTTP request (the
  request path — all the request offers), and over the WebSocket the DOM client
  sends it (with the viewport) as its **first** message and later **enriches**
  the environment (a window-resize re-emits the viewport; future platform
  fields arrive the same way). The application applies the environment
  uniformly — the router hydrates from the `ROUTE` field before mount, so a
  deep-link request renders the right destination on the first frame. The app
  never models the platform's location handling — history adaptation
  (`pushState` / `replaceState` / back) is a renderer/DOM-client translation of
  the `ROUTE` property and the `NAVIGATE` event.

**The `Navigation` facade** — the ergonomic entry point. `Navigation.navigator`
collapses the route table + router + seeding into one readable flow, and
`Navigation.of(router)` is the container:

```java
Router router = Navigation.navigator("/kitchen")     // seeds the initial path
    .route("/",          new HomeView())             // View overload (no params)
    .route("/users/:id", params -> new UserView(params.intValue("id")))
    .fallback(new NotFoundView())
    .build();

View shell = Navigation.of(router);                  // == NavigationContainer.of(router)
Signal<Boolean> onKitchen = Navigation.isActive(router, "/kitchen");
```

- `.route(String, View)` for no-param destinations; `.route(String, RouteHandler)`
  for param destinations; guarded variants take a `Predicate<Params>` + a redirect
  target; `.fallback(View | RouteHandler)` is the 404. `build()` returns the
  seeded `Router` (the initial path flows through the same guard matching).
- **`Params`** replaces the raw `Map<String,String>` in `RouteHandler` with typed
  access: `params.get("id")`, `params.intValue("id")`, `params.longValue(...)`,
  `params.doubleValue(...)`, `params.booleanValue(...)`, `params.path()`.
- **`Navigation.isActive(router, path)` → `Signal<Boolean>`** is a reactive
  "is this the active route" signal derived from the router's route signal —
  style an active menu row or gate conditional content:
  `Signals.computed(() -> Navigation.isActive(router, path).get() ? ACTIVE : CLEAR)`.
- **The environment is the one scoping system** (SwiftUI `.environment`): values
  are bound down a subtree with `View.environment(key, value)` and read with
  `Environment.value(key)`; nearest wins. Styles use it too — `buttonStyle`
  binds `Environment.BUTTON_STYLE` (`EnvironmentKey<ButtonStyle>`), so a style
  scoped above a structural container survives its destination re-render.
- **The router as a scoped environment value** (SwiftUI `.environment` style):
  `Navigation.ROUTER` is an `EnvironmentKey<Router>`. A `NavigationContainer` scopes
  it to its destination subtree; any component reads
  `Environment.value(Navigation.ROUTER)` during render — no constructor threading,
  nearest binding wins (nested containers override), and structural slots re-apply
  the scope when they re-render their destination.
- **Environment reads always return a signal** (`Environment.value(key)` →
  `Signal<T>`): a value injected as a `Signal` comes back as the same instance, so a
  node bound to it (e.g. `Text.of(Environment.value(key))`) re-emits when it changes;
  a plain value is wrapped in a constant signal (`.get()` gives the value). Inject a
  reactive value with `view.environment(key, signal)`. A read made **before the key is
  bound** (e.g. in a field initializer, before the enclosing `.environment(...)` scope
  is pushed) returns a **lazy signal** that captures the binding on the first `.get()`
  during render — the SwiftUI `@Environment` field style:
  ```java
  private final Signal<Router> router = Environment.value(Navigation.ROUTER);
  @Override public View body() { ... menuRow(router.get(), ...) ... }
  ```

**The active platform path is universal** — `Platform.ACTIVE_PATH` is an
`EnvironmentKey<String>` the host provides for **every** app (with
or without navigation), like SwiftUI's `onOpenURL` generalized across platforms:
```java
// host (always):
WritableSignal<String> activePath = Signals.signal(env.route());
RenderResult result = emitter.mount(
        root.environment(Platform.ACTIVE_PATH, activePath), new Environment(state));
// re-route on deep-link/popstate:
activePath.set(env.route());   // or from a NAVIGATE event URL
```
- **An app with navigation** builds a **router bound to the signal**:
  `Navigation.navigator().route(...).build(Environment.value(Platform.ACTIVE_PATH))`.
  The signal is the source of truth: external writes (deep links/popstate) are
  re-routed **through guards** (never bypassed), and the router's own
  `navigate`/`push`/`pop`/`replace` are mirrored back into the signal when it is
  writable (the host's `Platform.ACTIVE_PATH` always is). The initial value is
  guard-processed.
- **An app without navigation** just observes the signal — read it, or register
  `View.onPathChange(path -> …)` (fires on every change, including the initial value).
- `NAVIGATE` events: a URL updates `activePath` (→ bound router re-routes guard-aware,
  `onPathChange` listeners fire); a back (no URL) goes to `RenderResult.navigateHandler`
  → `router.pop()` — meaningful only when a router exists.

**Any component can change the route** — declarative navigation intents. A
component anywhere *inside* a `NavigationContainer` can change the route without
threading a `Router` by hand, using a `NavigationMod` on a `BUTTON`:

```java
Button.of("Go to kitchen").navigate("/kitchen");  // direct selection
Button.of("Open item").push("/item/1");           // drill-down (back-stack)
Button.of("Swap").replace("/settings");           // guard redirect / replace
NavigationLink.of("Users", "/users");             // router-agnostic (pushes)
```

- The intent (`navigateTo` + a `NavOp` — `NAVIGATE`/`PUSH`/`REPLACE`) is
  recorded on the node at render.
- The **emitter resolves it to the nearest enclosing `Router`** — the
  `NavigationContainer` whose subtree the component lives under — while walking
  the retained tree (nested containers resolve to the innermost one). No
  hidden global, no environment lookup.
- The resolved action is exposed on `RenderResult.navigateActions`
  (`node id → Runnable`), a live map like `tapActions`; the host routes a tap
  on that node to the action (checked before `tapActions`).
- A `NavigationContainer` whose subtree *is* a destination resolves its own
  router, so the sidebar's own menu rows (which sit *outside* the container, as
  the developer's custom chrome) capture the router explicitly — the
  nearest-enclosing mechanism covers components inside a container.

**URL sync (web)** — the app owns state; the browser mirrors it:

1. On a route change the app emits the new path as `ROUTE`; the DOM client
   reacts with `history.pushState` (v1 always pushes; `replaceState` for
   `replace()` is a documented follow-up).
2. Browser back/forward fire `popstate`; the DOM client sends a `NAVIGATE`
   event with the new URL over the event path; the host routes it into the
   router (`handlePlatformNavigation`), which matches and re-emits.
3. No sync loop: a server-originated `pushState` never fires `popstate`. On
   initial SSR hydrate the URL is already correct — no `pushState`.

**Native back** — platform back affordances (Android predictive-back, iOS
swipe-back, a desktop back button) map to a `NAVIGATE` event **without** a URL
payload (= "back one step"); the host calls `router.pop()`. The renderer never
decides navigation — it only requests it.

**Native integration** — each renderer **may** promote a `NavigationContainer`
slot onto its platform navigation affordance. The trigger is structural, not a
new primitive: a slot carrying the `ROUTE` (STRING) property is a navigation
slot and may be rendered as the platform's native navigation container
(SwiftUI `NavigationStack`, Compose `NavHost`, WinUI `NavigationView` / `Frame`,
GTK `AdwNavigationView`, LVGL screens `lv_scr_load`). The contract keeps the
renderer stateless:

- **App owns** the route signal, the back-stack, and which destination is
  current; it emits the current destination as the slot child plus `ROUTE`
  and `NAV_DEPTH`.
- **Renderer owns** the native chrome and presentation only: it maps the slot's
  child swaps onto native push/pop, uses `ROUTE` for native path parity and
  `NAV_DEPTH` to reconcile its page stack (push when deeper, replace the top
  when the depth is unchanged, pop down on a depth decrease), uses `TRANSITION`
  (0x1031) to choose a native transition (fade/slide/scale), and
  translates every native back affordance (a header-bar back button, a
  swipe/gesture, a platform back key, predictive back) into a `NAVIGATE` event
  **without** a URL payload (= "back one step"). The renderer never holds the
  back-stack and never decides navigation — it renders whatever destination
  subtree the app emits and may animate the swap.
- Platforms with no native navigation container (LVGL, terminal/canvas, plain
  GTK4 boxes) render the slot as an ordinary container that swaps children in
  place; the app's own back-stack is the only stack. The app does not branch on
  platform — the same `NavigationContainer` works on both, and the renderer's
  use (or not) of a native container is purely a presentation decision.

| Platform | Native container | Slot child swap maps to | Native back → |
|---|---|---|---|
| GTK/Linux | `AdwNavigationView` | `push`/`pop` the child widgets | `NAVIGATE` (no URL) |
| SwiftUI | `NavigationStack(path:)` | bind emitted path → native path | `NAVIGATE` |
| Android/Compose | `NavHost` | route string → `NavHostController.navigate` | `NAVIGATE` |
| WinUI | `NavigationView` / `Frame` | `Frame.Navigate` / sidebar+detail | `NAVIGATE` |
| Web (DOM client) | History API | `ROUTE` → `pushState` (already implemented) | `NAVIGATE` |
| LVGL / embedded | none | whole-tree swap (app's back-stack) | n/a |

**Deltas (Java)**: `Router` / `RouteTable` live in
`com.pathland.view.router`; `Conditional` in `com.pathland.view`. The
`NavigationContainer` is a structural container, so `if`/`switch`-style sugar
is the `Conditional.when` form of [§3.4](#34-structural-reactivity-conditional-rendering).

---

## 5. Modifier surface

Properties, value types, and emission rules come from
[MODIFIERS.md](./MODIFIERS.md); the tables here give the **DSL signature**. A
compound SwiftUI modifier expands to **one `SET_PROPERTY` per underlying
property** (`.frame` → `WIDTH` + `HEIGHT` + `ALIGNMENT`; `.border` →
`BORDER_COLOR` + `BORDER_WIDTH`; `.shadow` → `SHADOW_COLOR` + `SHADOW_RADIUS` +
`SHADOW_X` + `SHADOW_Y`). `Java DSL (current)` is the reference realization.

**One mechanism for every modifier.** Each entry in the tables below is a
`ViewModifier` value. `padding(16)` is shorthand for
`.modifier(Padding.of(16))`; `foregroundStyle(color)` for
`.modifier(ForegroundStyle.of(color))`. The tables list the canonical sugar
names; core and application-authored modifiers use the same `.modifier(...)`
surface ([§5.6](#56-custom-modifiers-developer-authored)).

> **Java realization**: the Java DSL ships **no modifier sugar on `View`** —
> modifiers are values applied via `.modifier(X.of(...))` (single) or
> `.modifiers(X.of(...), Y.of(...))` (several, innermost-first). The "Java DSL
> (current)" column lists the value form; the parameter list is carried from the
> canonical signature.

### 5.1 Layout & frame

| Modifier | Canonical (SwiftUI-shaped) | Java DSL (current) | Property(ies) |
|----------|---------------------------|--------------------|---------------|
| `frame` | `.frame(width:height:alignment:)` | `.modifier(FrameMod.of(float width))` / `.modifier(FrameMod.of(float width, float height))` / `.modifier(FrameMod.of(float width, float height, Alignment))` — alignment optional (omitted → no `ALIGNMENT` emitted, a stack's own alignment is preserved) | `WIDTH` 0x100B, `HEIGHT` 0x100C, `ALIGNMENT` 0x0002 (only when provided) |
| `frame(min:…)` | `.frame(minWidth:idealWidth:maxWidth:minHeight:idealHeight:maxHeight:)` | `.modifier(FrameMod.of(float, float, float, float, float, float))` (NaN = unset) | `MIN_WIDTH` 0x0012 … `MAX_HEIGHT` 0x0017 |
| `padding` | `.padding(_:)` / `.padding(_:edges:)` | `.modifier(Padding.of(int))` / `.modifier(Padding.of(float))` / `.modifier(Padding.of(int top, int right, int bottom, int left))` | `PADDING` 0x1011 / `PADDING_TOP` 0x1012 … `PADDING_LEFT` 0x1015 |
| `offset` | `.offset(x:y:)` | `.modifier(Offset.of(float x, float y))` | `OFFSET_X` 0x000E, `OFFSET_Y` 0x000F |
| `position` | `.position(x:y:)` | `.modifier(Position.of(float x, float y))` | `POSITION_X` 0x0010, `POSITION_Y` 0x0011 |
| `fixedSize` | `.fixedSize()` / `.fixedSize(horizontal:vertical:)` | `.modifier(FixedSize.of())` / `.modifier(FixedSize.of(boolean, boolean))` | `FIXED_SIZE_HORIZONTAL` 0x0018, `FIXED_SIZE_VERTICAL` 0x0019 |
| `layoutPriority` | `.layoutPriority(_:)` | `.modifier(LayoutPriority.of(float))` | `LAYOUT_PRIORITY` 0x001A |
| `zIndex` | `.zIndex(_:)` | `.modifier(ZIndex.of(float))` | `Z_INDEX` 0x100F |
| `aspectRatio` | `.aspectRatio(_:contentMode:)` | `.modifier(AspectRatio.of(float, ContentMode))` | `ASPECT_RATIO` 0x001B, `CONTENT_MODE` 0x001C |
| `scaledToFit` | `.scaledToFit()` | `.modifier(ScaledToFit.of())` | `CONTENT_MODE` 0x001C (Fit) |
| `scaledToFill` | `.scaledToFill()` | `.modifier(ScaledToFill.of())` | `CONTENT_MODE` 0x001C (Fill) |
| `minimumScaleFactor` | `.minimumScaleFactor(_:)` | `.modifier(MinimumScaleFactor.of(float))` | `MINIMUM_SCALE_FACTOR` 0x001D |

**Semantics**: `WIDTH`/`HEIGHT` use the sentinels `FILL` (−1.0 = expand) and
`HUG_CONTENT` (−2.0 = intrinsic); omitting an axis leaves it to the native
renderer. SwiftUI's `.frame(maxWidth: .infinity)` / `maxHeight: .infinity` is
written by passing `Float.POSITIVE_INFINITY` (Java) / `f32::INFINITY` (Rust) to
the width/height overloads, which is normalized to `FILL` before emission. In
the min/ideal/max overload an infinite `maxWidth`/`maxHeight` means *no limit*
(the bound is omitted). `OFFSET` is a post-layout translation; `POSITION` is
absolute placement within the parent.

### 5.2 Text formatting

| Modifier | Canonical (SwiftUI-shaped) | Java DSL (current) | Property(ies) |
|----------|---------------------------|--------------------|---------------|
| `font` | `.font(.system(size:))` | `.modifier(FontSize.of(float))` / `.modifier(FontSize.of(Signal<Float>))` | `FONT_SIZE` 0x1007 |
| `fontWeight` | `.fontWeight(_:)` | `.modifier(FontWeightMod.of(FontWeight))` | `FONT_WEIGHT` 0x1008 (100–900) |
| `font` (custom) | `.font(.custom(name:size:))` | `.modifier(FontFamily.of(String))` | `FONT_FAMILY` 0x1009 |
| `fontStyle` | `.italic()` / `.fontDesign(_:)` | `.modifier(Italic.of())` / `.modifier(FontStyleMod.of(FontStyle))` / `.modifier(FontDesignMod.of(FontDesign))` | `FONT_STYLE` 0x1017, `FONT_DESIGN` 0x1018 |
| `fontWidth` | `.fontWidth(_:)` | `.modifier(FontWidth.of(float))` | `FONT_WIDTH` 0x1019 |
| `kerning` | `.kerning(_:)` | `.modifier(Kerning.of(float))` | `KERNING` 0x101A |
| `tracking` | `.tracking(_:)` | `.modifier(Tracking.of(float))` | `TRACKING` 0x101B |
| `baselineOffset` | `.baselineOffset(_:)` | `.modifier(BaselineOffset.of(float))` | `BASELINE_OFFSET` 0x101C |
| `lineSpacing` | `.lineSpacing(_:)` | `.modifier(LineSpacing.of(float))` | `LINE_SPACING` 0x101D |
| `lineLimit` | `.lineLimit(_:)` | `.modifier(LineLimit.of(int))` | `LINE_LIMIT` 0x000B (0 = unlimited) |
| `multilineTextAlignment` | `.multilineTextAlignment(_:)` | `.modifier(TextAlignmentMod.of(TextAlignment))` | `TEXT_ALIGNMENT` 0x000C |
| `truncationMode` | `.truncationMode(_:)` | `.modifier(TruncationMod.of(Truncation))` | `TRUNCATION_MODE` 0x000D |
| `textCase` | `.textCase(_:)` | `.modifier(TextCaseMod.of(TextCase))` | `TEXT_CASE` 0x101E |
| `underline` | `.underline()` | `.modifier(Underline.of())` / `.modifier(Underline.of(boolean))` | `UNDERLINE` 0x101F |
| `strikethrough` | `.strikethrough()` | `.modifier(Strikethrough.of())` / `.modifier(Strikethrough.of(boolean))` | `STRIKETHROUGH` 0x1020 |
| `foregroundStyle` | `.foregroundStyle(_:)` | `.modifier(ForegroundStyle.of(Color))` / `.modifier(ForegroundStyle.of(Signal<Color>))` | `COLOR` 0x100A |
| `tint` | `.tint(_:)` | `.modifier(Tint.of(Color))` | `TINT` 0x1030 |

> **`Color` is never a modifier** — there is no `.color()` and
> `.foregroundColor(_:)` is deprecated. Foreground styling is
> `.foregroundStyle(_:)`.

### 5.3 Appearance & effects

| Modifier | Canonical (SwiftUI-shaped) | Java DSL (current) | Property(ies) |
|----------|---------------------------|--------------------|---------------|
| `background` | `.background(_:)` | `.modifier(Background.of(Color))` / `.modifier(Background.of(Signal<Color>))` | `BACKGROUND_COLOR` 0x1001 |
| `border` | `.border(_:width:)` | `.modifier(Border.of(Color color, float width, float radius))` | `BORDER_COLOR` 0x1004, `BORDER_WIDTH` 0x1003, `BORDER_RADIUS` 0x1005 |
| `border` (edges) | `.border(_:width:edges:)` | (not exposed) | + `BORDER_EDGES` 0x1016 (u32 bitmask: `TOP`=1, `LEADING`=2, `BOTTOM`=4, `TRAILING`=8) |
| `cornerRadius` | `.cornerRadius(_:)` | `.modifier(CornerRadius.of(float))` | `BORDER_RADIUS` 0x1005 |
| `shadow` | `.shadow(color:radius:x:y:)` | `.modifier(Shadow.of(Color, float, float, float))` / `.modifier(Shadow.of(float))` | `SHADOW_COLOR` 0x1021, `SHADOW_RADIUS` 0x1022, `SHADOW_X` 0x1023, `SHADOW_Y` 0x1024 |
| `opacity` | `.opacity(_:)` | `.modifier(Opacity.of(float))` | `OPACITY` 0x100D |
| `blur` | `.blur(radius:)` | `.modifier(Blur.of(float))` | `BLUR_RADIUS` 0x1025 |
| `saturation` | `.saturation(_:)` | `.modifier(Saturation.of(float))` | `SATURATION` 0x1026 |
| `contrast` | `.contrast(_:)` | `.modifier(Contrast.of(float))` | `CONTRAST` 0x1027 |
| `brightness` | `.brightness(_:)` | `.modifier(Brightness.of(float))` | `BRIGHTNESS` 0x1028 |
| `grayscale` | `.grayscale(_:)` | `.modifier(Grayscale.of(float))` | `GRAYSCALE` 0x1029 |
| `hueRotation` | `.hueRotation(_:)` | `.modifier(HueRotation.of(float))` | `HUE_ROTATION` 0x102A |
| `colorMultiply` | `.colorMultiply(_:)` | `.modifier(ColorMultiply.of(Color))` | `COLOR_MULTIPLY` 0x102B |
| `colorInvert` | `.colorInvert()` | `.modifier(ColorInvert.of())` | `COLOR_INVERT` 0x102C |
| `clipped` | `.clipped()` | `.modifier(Clipped.of())` | `CLIPS_TO_BOUNDS` 0x1010 |
| `clipShape` | `.clipShape(_:)` | `.modifier(ClipShape.of(ShapeKind))` | `CLIPS_TO_BOUNDS` 0x1010 + `SHAPE_KIND` 0x0006 |

**Delta (Java)**: the border modifier value is `Border.of(color, width[, radius])`
— canonical color-then-width order; corner radius is a separate
`.cornerRadius` value (`CornerRadius.of`).

### 5.4 Transform

| Modifier | Canonical (SwiftUI-shaped) | Java DSL (current) | Property(ies) |
|----------|---------------------------|--------------------|---------------|
| `rotationEffect` | `.rotationEffect(_:anchor:)` | `.modifier(Rotation.of(float))` | `ROTATION_DEGREES` 0x102D |
| `scaleEffect` | `.scaleEffect(_:anchor:)` | `.modifier(ScaleEffect.of(float))` | `SCALE` 0x102E |

Anchor is renderer-token-owned (center default); the protocol does not transmit
anchors. Transforms do not affect layout.

### 5.5 Interaction & state

| Modifier | Canonical (SwiftUI-shaped) | Java DSL (current) | Property(ies) |
|----------|---------------------------|--------------------|---------------|
| `hidden` | `.hidden()` | `.modifier(Hidden.of())` / `.modifier(Visible.of(boolean))` | `VISIBLE` 0x100E |
| `disabled` | `.disabled(_:)` | `.modifier(Disabled.of(boolean))` | `ENABLED` 0x2003 (inverse: 1 = interactive) |
| `allowsHitTesting` | `.allowsHitTesting(_:)` | `.modifier(AllowsHitTesting.of(boolean))` | `ALLOWS_HIT_TESTING` 0x102F |
| `controlSize` | `.controlSize(_:)` | `.modifier(ControlSizeMod.of(ControlSize))` | `CONTROL_SIZE` 0x200C |
| `accessibilityLabel` | `.accessibilityLabel(_:)` | `.modifier(AccessibilityLabel.of(String))` | `LABEL` 0x200A |
| `accessibilityRole` | `.accessibilityRole(_:)` | `.modifier(AccessibilityRole.of(int))` | `ROLE` 0x2001 |
| `accessibilityState` | `.accessibilityState(_:)` | `.modifier(AccessibilityState.of(int))` | `STATE` 0x2002 |
| `modifier` (custom) | `.modifier(_:)` | `.modifier(ViewModifier)` | composes core modifiers |
| `buttonStyle` | `.buttonStyle(_:)` | `.modifier(ButtonStyleMod.of(ButtonStyle))` | environment-scoped (`Environment.BUTTON_STYLE` key, nearest-wins) |
| `labelStyle` | `.labelStyle(_:)` | `.modifier(LabelStyleMod.of(LabelStyle))` | environment-scoped (`Environment.LABEL_STYLE` key, nearest-wins); DSL-only control flow — no wire property |
| `focusable` | `.focusable(_:)` | (via the `PointerEvents` modifier) | **no property** — declares `FOCUS` listener bit 5; observe `FOCUS_CHANGED` |
| raw listeners | `.pointerEvents(mask)` / `.pointer_events(mask)` | `.modifier(PointerEvents.of(int))` | `EVENT_LISTENERS` 0x2005 (u32 bitmask, bits per EVENTS.md) |

**Delta (Java)**: `.visible(boolean)` is a Pathland extra (SwiftUI only has
`.hidden()` — `Visible.of(...)`/`Hidden.of()`). `.accessibilityRole`/
`.accessibilityState` take raw `int` codes today rather than a typed enum. Raw
input listeners are exposed only through the `PointerEvents` modifier value —
there is no per-event sugar (e.g. a `.focusable` or `.onSubmit` that sets the
matching bit).

### 5.6 Custom modifiers (developer-authored)

In SwiftUI any developer can define a modifier that applies to **any** existing
view — and Pathland is the same. This is a **first-class, MUST** property of
every conformant DSL: modifiers are never hard-bound to a view type.

- **Applicable to any view.** A modifier works on a library primitive
  (`Text`), a container (`VStack`), a control (`Button`), a custom view, or an
  already-modified view. The same modifier value applies everywhere.
- **One mechanism.** Core and application-authored modifiers are the **same
  surface**: a `ViewModifier` value applied via `.modifier(...)`. The library's
  sugar names (`.padding`, `.foregroundStyle`, …) are conveniences that
  construct the built-in `ViewModifier` values; there is exactly one modifier
  mechanism ([§5](#5-modifier-surface)). The Java realization ships **no sugar**
  — `.modifier(Padding.of(16))` is the only form (plus the `.modifiers(...)`
  varargs convenience for several at once).

| | Canonical | Java DSL (`com.pathland.view`) | Rust DSL (`pathland-view`) |
|-|-----------|-------------------------------|----------------------------|
| authoring | `struct Card: ViewModifier { func body(content: Content) -> some View }` | `@FunctionalInterface ViewModifier { View body(View content) }` | `trait ViewModifier { fn apply(&mut Node) }` |
| applying | `content.modifier(Card())` | `content.modifier(CardStyle.of(...))` / `content.modifiers(A.of(...), B.of(...))` | `content.modifier(Card)` |
| sugar | `.padding(16)` ≡ `.modifier(Padding(16))` | **no sugar** — `.modifier(Padding.of(16))` only | `.padding(16.0)` ≡ `.modifier(Padding(16.0))` |

**Composition**: a custom modifier composes core modifiers **or** wraps
`content` with additional structure (a background, an overlay, a frame) and
returns the decorated view — exactly SwiftUI's `body(content: Content)`.

**Emission contract**: a custom modifier composed from core modifiers emits the
same one-`SET_PROPERTY`-per-property deltas as writing the modifiers inline;
emission stays diff-based, and a renderer that cannot apply a modifier
**allows and ignores** it.

**Realization deltas**:
- **Rust** — already conformant: every core modifier (`Padding`, `FontSize`,
  `ForegroundStyle`, `Frame`, …) implements `ViewModifier`, and the sugar
  methods delegate to `.modifier(...)`. Caveat: `apply(&mut Node)` mutates the
  built node's properties only — it cannot wrap `content` with new structure
  (the Java/SwiftUI `body(content)` form can). A conformant DSL's
  custom-modifier mechanism should support wrapping.
- **Java** — fully conformant: every core modifier is a `ViewModifier` value
  (`Padding.of(16)`, `ForegroundStyle.of(color)`, …) applied via `.modifier(...)`;
  `View` has no modifier sugar, and `buttonStyle` is the `ButtonStyleMod`
  modifier value (which binds the `Environment.BUTTON_STYLE` key down the
  subtree — subtree scoping is environment-only). Application-authored
  `ViewModifier.body(View)` is SwiftUI-shaped and can wrap content with structure.

---

## 6. Authoring conventions

A conformant DSL follows these conventions:

1. **Names**: SwiftUI view/modifier names; per-language case (camelCase,
   snake_case). `foregroundStyle` not `foregroundColor`; `fontWeight`,
   `multilineTextAlignment`, `scaledToFit`, `allowsHitTesting` keep their
   SwiftUI spellings.
2. **Signature order**: canonical parameter order is preserved —
   `.border(color, width)`, `.frame(width, height, alignment)`,
   `.shadow(color, radius, x, y)`. Compound modifiers expand to one property
   per argument.
3. **Constructor vs modifier**: structural (alignment, spacing) in the
   constructor; everything else chainable.
4. **Typed enums**: `Alignment` (leading/center/trailing/fill),
   `TextAlignment`, `FontWeight` (100–900), `Truncation` (head/middle/tail),
   `TextCase` (none/uppercase/lowercase), `FontStyle` (normal/italic),
   `FontDesign` (default/serif/rounded/monospaced), `ContentMode` (fit/fill),
   `ControlSize` (small/regular/large), `ToggleStyle`
   (switch/checkbox/button), `PickerStyle` (menu/segmented/wheel/radiogroup),
   `DatePickerMode` (date/time/dateAndTime), `ShapeKind`
   (circle/rectangle/roundedRectangle/capsule/ellipse/path). Numeric codes
   come from MODIFIERS.md's appendix; the DSL resolves them.
5. **`Spacer`/`Color` as views**: `Spacer()` is a flexible expanding filler;
   `Color(...)` in a tree is a layout-greedy fill.
6. **The environment is the only inheritance mechanism.** Any value that must
   reach a whole subtree — a style (`ButtonStyle`), the router, the active path,
   the session's persisted state — is injected through the environment, never
   threaded through constructors and never through a second scoping system. The
   environment is a hierarchical, nearest-wins scope of typed `EnvironmentKey`
   values (`View.environment(key, value)` / `Environment.value(key)`, Java;
   §4.5). A language MAY implement the scope with a thread-local over the
   synchronous render pass (Java does) — but that is the implementation of the
   one mechanism, not an additional one. A subtree-scoping modifier MUST be
   expressed as an `EnvironmentKey` binding: `buttonStyle` binds
   `Environment.BUTTON_STYLE` (`EnvironmentKey<ButtonStyle>`), exactly like the
   router and the active path.
7. **Reactivity discipline**: `body()` is evaluated once; a signal read during
   mount records a dependency; a later write re-emits only the bound node.
   Never mutate signals during mount. The only structural re-evaluation is a
   structural container ([§3.4](#34-structural-reactivity-conditional-rendering)),
   which the emitter reconciles into `TREE` deltas.
8. **Emission contract**: each DSL call emits exactly the documented
   properties with the documented value types; the emitter diffs. The DSL never
   computes layout, never positions, never serializes full trees.

---

## 7. Theme management

Pathland theming is **application-owned over a renderer-owned token system**
([TOKENS.md](./TOKENS.md); the wire encoding lives in [OPCODE.md](./OPCODE.md)
§Design Token System). The DSL gives the author two distinct surfaces:

1. **Token references in style modifiers** — semantic intent instead of a
   literal: `.foregroundStyle(Color.token("color.primary"))` emits a
   `DESIGN_TOKEN` reference the renderer resolves against the current scheme.
   No opcode is re-emitted when a token or the scheme changes.
2. **Global theme overrides** — a batch of `STYLE::SET_DESIGN_TOKEN` commands
   rolled at mount: a single-scheme `Theme` or an `AdaptiveTheme` light+dark
   pair (base = **light**, `dark.`-prefixed = **dark**).

> **Ownership**: the application owns theme values (base + `dark.*`) and token
> references; the renderer owns token defaults (light + dark), effective
> color-scheme detection, resolution (override → default → parent → fallback),
> and interaction-state styling (hover/pressed/focus/disabled). The protocol
> never transmits hover/click/transition styling.

### 7.1 Token-typed values

Any property whose value type is `COLOR` / `F32` / `STRING` / `ENUM` MAY carry a
**token reference** instead of a literal value (MODIFIERS.md §Value types). The
reference rides the `DESIGN_TOKEN` value type (`0x08`) with the token path in
the arena — **never** a packed literal. A property carries exactly one value
(literal or reference, never both).

| Canonical (SwiftUI-shaped) | Java DSL (current) | Rust DSL (current) | Emits |
|----------------------------|--------------------|--------------------|-------|
| `Color.token("color.primary")` | `Color.token("color.primary")` | `Color::token("color.primary")` | `DESIGN_TOKEN` ref (`0x08`) in any COLOR-typed property: `COLOR` 0x100A (`.foregroundStyle`), `BACKGROUND_COLOR` 0x1001, `BORDER_COLOR` 0x1004, `SHADOW_COLOR` 0x1021, `TINT` 0x1030, … |
| `Color.token("dark.color.surface")` | `Color.token("dark.color.surface")` | `Color::token("dark.color.surface")` | same — an explicit dark-variant reference |
| `.padding(space.2)` / `Spacing(space.2)` | (token-valued spacing/padding) | (token-valued spacing/padding) | `DESIGN_TOKEN` ref in a F32-typed property: `PADDING` 0x1011 / `SPACING` 0x0001 / `CONTENT_MARGINS` 0x0005, … (see TOKENS.md §Generative token families) |

`Color` keeps its dual identity: as a **View** it is a layout-greedy fill; as a
token reference (`Color.token(path)`) it is a **data value** passed to
style-taking modifiers. A token-typed property resolves renderer-side and
**re-resolves automatically** when the effective scheme changes — overrides and
scheme are renderer state, so no node is ever re-emitted for a theme/scheme
change.

Rules:

1. Reference any token path — the standard catalog (TOKENS.md §Standard Token
   Catalog) or an application-defined path. A renderer that cannot apply a token
   falls back rather than erroring.
2. `space.<N>` members are **generative**: reference them freely (`space.2`,
   `space.0.5`), but override only `space.base` (see §7.2 rule 2).
3. A token reference must not be combined with a literal on the same property.

### 7.2 Global theme overrides

A **theme** is a batch of token overrides. Every override defines **exactly one
value**; combine a light and a dark `Theme` via `AdaptiveTheme` for adaptive
theming.

| Canonical (SwiftUI-shaped) | Java DSL (`com.pathland.view`) | Rust DSL (`pathland-view` / `pathland-engine`) | Emits |
|----------------------------|--------------------------------|-----------------------------------------------|-------|
| `Theme()` — one-scheme override batch | `new Theme()` (`ThemeData`) | `Theme::new()` | one `STYLE::SET_DESIGN_TOKEN` per override (`A` = arena path, `B` = valueType, `C` = value) |
| `.color("color.primary", 0xFF2563EB)` | `.color("color.primary", 0xFF2563EB)` | `.color("color.primary", 0xFF2563EB)` | `COLOR`-typed override (sRGB `0xAARRGGBB`) |
| `.f32("space.base", 4.0)` | `.f32("space.base", 4.0f)` | `.f32("space.base", 4.0)` | `F32`-typed override (lengths in device pixels) |
| `.u32("…", v)` / `.u8("…", v)` | `.u32(path, int)` / `.u8(path, int)` | `.u32(path, v)` / `.u8(path, v)` | `U32` / `U8`-typed override |
| `.string("font.body.family", "Inter")` | `.string(path, String)` | `.string(path, "Inter")` | `STRING`-typed override (arena) |
| `AdaptiveTheme(light, dark)` | `new AdaptiveTheme(light, dark)` | `AdaptiveTheme::new(light, dark)` | light overrides as base tokens; dark overrides `dark.`-prefixed |

Emission contract:

- The theme is emitted **once at mount** as a frame of
  `STYLE::SET_DESIGN_TOKEN` opcodes before the tree. Java: `new Emitter(sink,
  theme)` emits it at the start of the mount and `renderFull` (resync) frames;
  Rust: `Engine::apply_theme` / `Engine::apply_adaptive_theme`.
- Overrides are **renderer state**: they never re-emit nodes and never
  participate in the diff — an unchanged tree with a theme still emits **zero**
  per-node opcodes.
- `AdaptiveTheme` emits the light `Theme`'s overrides as base paths and the dark
  `Theme`'s with the `dark.` prefix (added automatically when the author wrote a
  bare path). The renderer derives the effective scheme from the platform and
  resolves each override against it.
- The renderer supplies light + dark defaults for the whole Tier 1 catalog, so
  dark mode works even when the author only overrides base tokens.

Authoring rules:

1. Override any token in the catalog or an application-defined path; never
   assume specific renderer default values.
2. Override `space.base` to control the whole spacing scale — individual
   `space.<N>` members MUST NOT be overridden.
3. Overrides are **global and renderer-wide**: apply the theme once per
   session/host at mount, not per view.

---

## 8. Java DSL convergence (adopted)

The Java DSL (`com.pathland.view`) has the complete surface. The convergence
below has been **adopted** (`.of()` construction, the removal of the `View.*`
factories, and modifiers as `ViewModifier` values); the remaining items are
marked *optional*. The canonical contract is [§4](#4-view-surface) and
[§5](#5-modifier-surface).

| Canonical | Java (adopted) | Optional / remaining |
|-----------|----------------|----------------------|
| `Text("…")` | `Text.of(String)` / `Text.of(Signal<String>)` | — |
| `Button("…", action)` / `Button(action:label:)` | `Button.of(String, Runnable)` / `Button.of(View, Runnable)` | — |
| `Toggle("label", isOn: writable)` | `Toggle.of(String label, WritableSignal<Boolean>)` (+ `ToggleStyle` overload) | — |
| `Slider(value: in:)` | `Slider.of(binding, min, max)` (binding first) | — |
| `.border(color, width)` | `Border.of(Color, float)` via `.modifier(...)` | `Border.of(Color, float, float)` radius convenience |
| `DatePicker(selection:)` | `DatePicker.of(mode, days)` | *optional* `Date`-shaped overload encoding days + millis-of-day per `SET_DATE`/`DATE_CHANGED` |

Adopted conventions:

- **`.of()` is the one construction path.** Every concrete view/control exposes
  a static `<ViewName>.of(...)` factory on its own class. Construction is never
  `new <ViewName>()` and never `View.<name>(...)`.
- **The `View.*` static-factory surface is removed.** The entire factory block
  (`text`, `image`, `rectangle`, `spacer`, `vstack`, `hstack`, `zstack`,
  `button`, `textField`, `textEditor`, `toggle`, `slider`, `stepper`,
  `progressView`, `gauge`, `divider`, `grid`, `scrollView`, `lazyVStack`,
  `lazyHStack`, `lazyVGrid`, `lazyHGrid`, `picker`, `menu`, `colorPicker`,
  `datePicker`, `group`) is deleted from `View.java`.
- **`Group` is a first-class view.** `Group.of(View...)` is a transparent
  container view (SwiftUI `Group`).
- **Core and custom modifiers share one mechanism.** Core modifiers are
  `ViewModifier` values (`Padding.of(16)`, `ForegroundStyle.of(color)`) applied
  via `.modifier(...)` — there is **no sugar on `View`**; several modifiers at
  once use `.modifiers(...)`, innermost-first. Parameterized modifier values use
  `.of(...)`; `buttonStyle` is the `ButtonStyleMod` value (an environment
  binding: it scopes `Environment.BUTTON_STYLE` down the subtree).
- **Full `ShapeKind` coverage.** `Circle.of()`, `Capsule.of()`, `Ellipse.of()`,
  `RoundedRectangle.of(cornerRadius:)`, generic `Shape.of(ShapeKind)` (for
  `Path`).
- *Optional*: an environment path letting the trailing-action form
  (`Button(label) { action }`) read the action from the environment, if a
  Java-idiomatic trailing lambda is desirable.
- *Optional*: a `Date`-shaped `DatePicker` binding (see table).
- Implementation status: `lib/java/pathland-view/status.md`.

---

## 9. Generation contract for a new language

An AI (or a human) generating a Pathland DSL in a new language MUST produce the
following and verify each item. This is a checklist; the authoritative details
are the companion specs.

### 9.1 Required surface

- [ ] **Views/controls** — every entry of [§4](#4-view-surface):
  `Text`, `Image`, `Color`, `Shape` (all `ShapeKind`s), `Divider`, `Spacer`,
  `ProgressView`, `Gauge`; `VStack`, `HStack`, `ZStack`, `Grid`, `ScrollView`,
  `LazyVGrid`, `LazyHGrid`, `LazyVStack`, `LazyHStack`; `Button`,
  `TextField`, `SecureField`, `TextEditor`, `Toggle`, `Slider`, `Stepper`,
  `Picker`, `Menu`, `DatePicker`, `ColorPicker`.
- [ ] **Modifiers** — every entry of [§5](#5-modifier-surface), chainable on
  any view, innermost-first, emitting one property per underlying argument.
- [ ] **Custom modifiers / unified mechanism** — a `ViewModifier` authoring
  surface (`body(content) -> View`) + `.modifier(...)` chainable on **any**
  view; **core modifiers are implemented through the same mechanism**, never a
  separate syntax ([§5.6](#56-custom-modifiers-developer-authored)).
- [ ] **Signals** — `signal`, `computed`, `effect`, `untracked`,
  `get`/`set`/`update`/`asReadonly`, with equality suppression, synchronous
  flush, glitch-free propagation, error caching, write discipline, circular
  detection ([§3.1](#31-signal-surface)).
- [ ] **Two-way bindings** — every value control takes a writable signal and
  exposes a sink the host routes its event into ([§3.2](#32-two-way-binding)).
- [ ] **Persisted state** — `State<T>` keyed fields (or the language's
  equivalent wiring) against a platform-neutral store ([§3.3](#33-persisted-state-statet)).
- [ ] **Gestures** — `.onTapGesture` (composed from raw pointer down/up) and
  raw `pointerEvents`/`EVENT_LISTENERS` bitmask ([§4.4](#44-gestures)).
- [ ] **Structural reactivity (conditional rendering)** — a structural
  container over a selector signal (`Conditional.when(...)` / `Case.of` /
  `Case.otherwise`, or the language's `if`/`switch` equivalent), reconciling
  the slot into `TREE` deltas with zero opcodes for identical structure
  ([§3.4](#34-structural-reactivity-conditional-rendering)).
- [ ] **Navigation** — `Router` / `RouteTable` / `NavigationContainer` /
  `NavigationLink` over a plain `Signal<Route>`, the initial route host-seeded
  before mount, emitting `ROUTE` and `TRANSITION` and consuming the `NAVIGATE`
  event ([§4.5](#45-navigation)).
- [ ] **Design tokens & theming** — a token surface: token-typed values usable
  in style modifiers (a `Color` accepting a token path, e.g. `Color.token("color.primary")`
  or the language's equivalent), a theme/override helper emitting
  `STYLE::SET_DESIGN_TOKEN` (base values + `dark.`-prefixed dark values), and
  the **base=light / `dark.*`** color-scheme convention with renderer-derived
  scheme detection — see [§7](#7-theme-management) and [TOKENS.md](./TOKENS.md).

### 9.2 Mapping rules (signature → protocol)

1. Each view/control maps to its **fixed component id** in PRIMITIVES.md.
   Application/custom component types live in `0x0100`–`0xFFFF`; a DSL must
   never reuse allocated ids.
2. Each modifier maps to the **documented property id(s)** in MODIFIERS.md
   with the documented **value type** (U8/U32/I32/F32/STRING/ENUM/COLOR/
   DESIGN_TOKEN). Enumerated values use the numeric codes in MODIFIERS.md's
   appendix.
3. `Color` values pack as sRGB `0xAARRGGBB`. A **token-typed** value instead
   carries the `DESIGN_TOKEN` value type with the token path in the arena
   (never a packed literal); the renderer resolves it against the current
   scheme. A theme/override helper emits `STYLE::SET_DESIGN_TOKEN`
   (`A` = arenaRef path, `B` = valueType, `C` = value); a `dark.`-prefixed path
   supplies the dark design (base = light — [TOKENS.md](./TOKENS.md)).
4. `WIDTH`/`HEIGHT` sentinels: `FILL` = −1.0, `HUG_CONTENT` = −2.0.
5. `DatePicker` value/event use the two-field encoding **days since epoch
   (I32) + millis of day (U32)**; there is no `DATE_VALUE` property.
6. Value/text/date events are gated by the transport-aware event guards; the
   DSL's controls emit the `ACTION_ID`/`BINDING_ID`/`EVENT_LISTENERS` that
   declare intent.
7. Emission is **diff-based and reactive**: the DSL describes values, the
   emitter diffs. A generated DSL must keep the retained tree + emitter
   separation; it must not hand-serialize full trees.

### 9.3 Language adaptation rules

| SwiftUI mechanism | Adapt as |
|-------------------|----------|
| trailing closure / result builder for children | varargs (Java), macros (Rust), builders/block (C#/Kotlin), element list (any) |
| `Binding<T>` (Swift) | `WritableSignal<T>` (always — signals are the model, never property wrappers) |
| `some View` opaque return | the language's `View` interface/trait (Java `View`, Rust `View` trait) |
| `@State`/`@Binding` property wrappers | signals + persisted `State<T>` ([§3.3](#33-persisted-state-statet)) |
| labeled parameters | named params where the language has them; otherwise preserve **parameter order** and document labels |
| method chaining for modifiers | default methods (Java), extension traits (Rust), extension methods (C#/Kotlin) |

### 9.4 Conformance recipe

A generated DSL is verified by proving it **emits the same bytes** as the
reference implementations:

1. Port the golden vectors in [CONFORMANCE.md](./CONFORMANCE.md) into the new
   DSL's test harness; assert byte-identical `TREE`/`STYLE`/`EVENT`/`META`
   opcodes for each canonical element.
2. Port the Java `EmitterTest` cases (mount → `SET_TEXT`/`SET_PROPERTY`/`SET_DATE`
   deltas; unchanged tree → zero opcodes; two-way routing: `TEXT_CHANGED`,
   `VALUE_CHANGED`, `DATE_CHANGED` write into the bound signal).
3. Verify the no-heap-allocation steady-state property where the target
   language supports it (the Rust engine proves zero dynamic allocations during
   steady-state emission).
4. Cross-check every referenced id against PRIMITIVES.md/MODIFIERS.md/EVENTS.md
   (the specs are the source of truth, never the reference code).

**Reference implementations to read**: Java — `lib/java/pathland-view`
(`com.pathland.view`, `com.pathland.view.signal`, `com.pathland.view.state`,
`com.pathland.view.emit`); Rust — `lib/rust/crates/pathland-view` +
`pathland-engine` + `pathland-core::signal`.

---

## 10. Appendix A: SwiftUI ↔ DSL signature map

Representative rows; the full surface is in [§4](#4-view-surface) and
[§5](#5-modifier-surface).

| SwiftUI | Canonical DSL | Java DSL (current) | Rust DSL (current) |
|---------|---------------|--------------------|--------------------|
| `Text("Hi")` | `Text("Hi")` | `Text.of("Hi")` | `text("Hi")` |
| `VStack(alignment: .center, spacing: 8) { … }` | `VStack(alignment:spacing:) { … }` | `VStack.of(Alignment.CENTER, 8, children...)` | `vstack![…].modifier(Spacing.of(8.0))` |
| `Button("+") { inc() }` | `Button("+", action)` | `Button.of("+", () -> inc())` | `button("+")` (no action yet) |
| `TextField("Name", text: $name)` | `TextField("Name", text: writable)` | `TextField.of("Name", name)` | `TextField { placeholder }` (no binding yet) |
| `Toggle("On", isOn: $on)` | `Toggle("On", isOn: writable)` | `Toggle.of(selected, binding)` | `Toggle(style)` (no binding yet) |
| `Slider(value: $v, in: 0...100)` | `Slider(value: writable, in: min...max)` | `Slider.of(vSig, 0, 100)` | `Slider { value, min, max }` (no binding yet) |
| `DatePicker("D", selection: $d)` | `DatePicker("D", selection: writable)` | `DatePicker.of(DatePickerMode.DATE, days)` | `DatePicker` (bare) |
| `.foregroundStyle(.red)` | `.foregroundStyle(Color)` | `.modifier(ForegroundStyle.of(Color))` | `.foreground_style(Color(0xFF0000FF))` |
| `.background(.gray)` | `.background(Color)` | `.modifier(Background.of(Color))` | `.background(Color(0xFFEEEEEE))` |
| `.border(.blue, width: 2)` | `.border(Color, width: 2)` | `.modifier(Border.of(Color, 2))` | `.border(Color, 2.0)` |
| `.frame(width: 100, height: 24)` | `.frame(width:height:alignment:)` | `.modifier(FrameMod.of(100, 24))` | `.frame(Some(100.0), Some(24.0), None)` |
| `.padding(16)` | `.padding(16)` | `.modifier(Padding.of(16))` | `.padding(16.0)` |
| `.font(.system(size: 28))` | `.font(size: 28)` | `.modifier(FontSize.of(28))` | `.font_size(28.0)` |
| `.fontWeight(.bold)` | `.fontWeight(FontWeight)` | `.modifier(FontWeightMod.of(FontWeight.BOLD))` | `.font_weight(700.0)` |
| `.shadow(color:radius:x:y:)` | `.shadow(color:radius:x:y:)` | `.modifier(Shadow.of(Color, float, float, float))` | (not yet) |
| `.onTapGesture { go() }` | `.onTapGesture(action)` | `.modifier(TapGesture.of(() -> go()))` | `.on_tap_gesture(|| go())` |
| `if showLogin { LoginView() } else { HomeView() }` | `if/else` in a result builder | `Conditional.when(showLogin, LoginView.of(), HomeView.of())` | `if`/`match` in `build()` |
| `NavigationStack { … }` | `Router` + `NavigationContainer` | `NavigationContainer.of(router)` | `NavigationContainer::new(router)` |
| `NavigationLink("Users", value:)` | `NavigationLink("label", router, to)` | `NavigationLink.of("Users", router, "/users")` | `navigation_link(...)` |
| `content.modifier(Card())` | `content.modifier(Card())` | `content.modifier(CardStyle.of(...))` | `.modifier(Card)` |

Core modifier sugar (`.padding`, `.foregroundStyle`, …) is shorthand for
`.modifier(CoreModifier.of(...))` — one mechanism for built-in and
application-authored modifiers alike ([§5.6](#56-custom-modifiers-developer-authored)).
The Java realization has **no sugar**: `.modifier(Padding.of(16))` is the only
form, with `.modifiers(...)` for several at once.

**Status deltas captured by this table**: the Java DSL is surface-complete —
`<ViewName>.of(...)` construction, modifiers as values via `.modifier(...)`
(no sugar on `View`), `ButtonStyleMod` for `.buttonStyle`; the Rust DSL is
structural today — `pathland-view` exposes the view/modifier surface without
signals, two-way bindings, or actions (signals live in `pathland-core::signal`
for engine-side binding). Both are per-project implementation status tracked in
each project's `status.md`.

---

## Conformance

- The wire footprint of every DSL element above is defined by the golden
  vectors in [CONFORMANCE.md](./CONFORMANCE.md). A new DSL element MUST add
  vectors before any implementation depends on it.
- Component ids: [PRIMITIVES.md](./PRIMITIVES.md); property ids and value
  types: [MODIFIERS.md](./MODIFIERS.md); event ids and listener bits:
  [EVENTS.md](./EVENTS.md). These specs are the single source of truth.