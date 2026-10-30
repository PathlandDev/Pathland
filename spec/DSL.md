# Pathland DSL Authoring Contract

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** October 15, 2026

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
> realizations; the "Java DSL" / "Rust DSL" columns below record the **agreed
> reference shapes**, not implementation status. This document defines the
> contract only.

### Two guiding rules

1. **Stay as close to SwiftUI as possible.** View names, modifier names, and
   signature shapes mirror SwiftUI. Adapt only where the host language forces
   it (Rust snake_case + macros; Java camelCase + builders; Python kwargs), and
   never in a way that reorders or renames the canonical parameters.
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
- **Exactly three authoring operations.** Views expose the same three
  operations — **configure values** (§5.0 `make`, §2), **apply modifiers**
  (`modifiers`), and, for content-bearing views, **supply children**
  (`children`). The three are a single fluent builder: any of them may be the
  first call and they may be chained in **any order** (see
  [§2](#2-canonical-signature-notation)); a later call overrides an earlier
  value for the same property.
- **`body()` is a tracked render function.** A composite view declares its
  subtree in `body()`; a signal read there subscribes that **component**, and a
  later write re-renders only that component and diffs its retained subtree.
  The wire still carries only actual changes (`SET_TEXT`/`SET_PROPERTY`/
  `SET_DATE`/`TREE`), never a rebuilt tree. A signal **bound** to a node
  (rather than read in `body()`) stays fine-grained: it emits one delta without
  re-running the body. Non-signal reads are mount-time only. The render body
  must be pure/idempotent and must not write signals. This is the
  **per-component reactive body** ([§3.4](#34-structural-reactivity-reactive-bodies--slots)).
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
  composed into a view through the shared `modifiers(...)` operation. There is
  **no per-modifier sugar on the view type** — `Padding` applied via
  `.modifiers(padding(p -> p.uniform(16)))`. Core and application-authored
  modifiers share the same surface.
- **A modifier's type is named for the modifier it applies — never with a
  `Mod` suffix.** Where a modifier applies a single value (a font, a font
  weight, an alignment, a style), the value type itself MAY *be* the modifier
  (`FontWeight.BOLD`, `Font.headline()`, `MyButtonStyle`), so there is one name
  for the concept. Languages that cannot merge the two (e.g. C#, whose enums
  cannot implement interfaces) use the equivalent value form. See
  [§9.3](#93-language-adaptation-rules).
- **There is exactly one style mechanism.** A customizable control/view takes
  its **content** from a *style* value (`ButtonStyle`, `LabelStyle`,
  `VideoStyle`, …) scoped through the environment; the control
  owns its native component + interaction and the style owns the content
  ([§5.7](#57-styleable-controls-the-style-contract)). Closed-variant/token
  styles (`ToggleStyle`, `PickerStyle`, `TextStyle`) are the same surface.
- **Values vs modifiers.** Structural/layout parameters and a control's bound
  value (`alignment`, `spacing`, `text`, `isOn`, `selection`) are **values**
  configured through a view's `make(...)`. Everything decorative (padding, color, font,
  frame, border, …) is a **modifier** applied through `modifiers(...)` (constructed
  by a lowercase function — `padding(...)`, `frame(...)`).
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

- `View(config:)` / `T("…")` — a view/control constructed with its **values**.
- `{ ... }` — the child subtree (a trailing closure in SwiftUI; a vararg,
  macro, builder, or lambda in other languages).
- `Signal<T>` — a read-only derived or writable signal; `WritableSignal<T>` —
  a two-way binding target. A control takes a `WritableSignal<T>` for its
  value (see [§3](#3-state-model-signals)).
- `()` — the action a `Button` fires.
- `.modifier(m)` — apply a **modifier value** to a view; chainable on any
  view. Every modifier — built-in or application-authored — is such a value
  ([§5.6](#56-custom-modifiers-developer-authored)).
- Modifiers chain and are applied **innermost-first** (the last chained
  modifier's property wins).

### The three operations

Every concrete view exposes the same three operations — **construction**
(`make`), **modifiers** (`.modifiers`), and **children** (`.children`). In the
Java realization `make(...)` **is the only static of the three**; a later call
overrides an earlier value for the same property.

| Operation | Purpose | Canonical (SwiftUI-shaped) |
|-----------|---------|----------------------------|
| **values** | Configure structural/layout values and a control's bound value | `View(config:)` — labels in the constructor |
| **modifiers** | Apply `ViewModifier` values, innermost-first | `.modifier(_:)` / `.padding(_:)` / … |
| **children** | Supply the content subtree (content-bearing views only) | trailing closure `{ … }` |

The **canonical builder spelling** (the recommended form; per-language spellings
are in [§9.3](#93-language-adaptation-rules)) is:

```
ViewName.make(values)              // values   — a config-builder lambda (Java views)
      .modifiers(modifier, ...)       // modifiers — varargs
      .children(view, ...)            // children  — varargs, content-bearing views only
```

In the Java realization a view's values entry is the static `make(...)` (the
construction factory; empty `make()` for config-less or value-less starts).
`modifiers(...)` is an **instance** operation available on the builder and on any
`View` value (the interface default), so it (and `children(...)`) chain on the
builder in any order:

```
VStack.make(v -> v.spacing(8)).children(a, b).modifiers(m)
VStack.make(v -> v.spacing(8)).modifiers(m).children(a, b)
VStack.make().modifiers(m).children(a, b)
```

The old static `X.modifiers(...)` first-call entry points are **removed** —
`modifiers(...)` is an **instance** operation (interface default for bare `View`
values, covariant override on the builder). Static `X.children(...)` is **kept**
as a value-less first-call convenience (`HStack.children(a, b)` ≡
`HStack.make().children(a, b)`); `make(...)` is the only values static.

**Values are a config-builder lambda.** `make { c in … }` populates a per-view
fluent **config** (`VStack.Config`, `Text.Config`, …). It is the *same type* a
style's `makeBody(Config)` receives ([§5.7](#57-styleable-controls-the-style-contract)).
The builder is **fluent and mutable**: setters return the config. Config-less
views (`Divider`, `Spacer`) take an empty `make()`.

**Every value member is reactive-capable (MUST).** A config member that carries a
value exposes **two forms** — a raw value and a `Signal<T>`:

```
Text.make(t -> t.text("hi"))            ≡  Text.make(t -> t.text(constant("hi")))
visible(v -> v.visible(flagSignal))      // reactive visibility
frame(f -> f.width(widthSignal))         // reactive sizing, layout included
```

The **raw form is sugar for a constant signal**; a **non-constant** signal
becomes a **node-level binding** — a change re-emits only that node's `SET_TEXT`
/ `SET_PROPERTY`, and a constant emits as a plain property with **zero binding
overhead**. This holds for *every* value member, layout included
(`spacing`, `alignment`, `padding`, `frame`, `columns`, …): anything describable
in the protocol can animate. A control's two-way *value* takes a
`WritableSignal<T>`; every other member takes a one-way `Signal<T>`.

**Per-language realization.** Java cannot declare a static and an instance
method with the same signature, so the Java realization holds the values entry
**statically** (`make(...)`) and exposes the chainable forms on the returned
builder/view type — `.modifiers(...)` is an instance operation (a `View`
interface default, covariantly overridden on the builder), and static
`X.children(...)` is retained as a value-less first-call convenience. Other
languages map the operations to their idioms (extension methods, associated
functions + traits, package constructors + methods, kwargs) — see [§9.3](#93-language-adaptation-rules).

**Single-value shortcuts.** A view or modifier whose config designates a
**primary value member** MAY also expose a single-value construction overload that
is pure sugar for the config-builder lambda (views construct with `make(value)`,
modifiers with the lowercase function's value form):

```
Text.make("hi")            ≡  Text.make(t -> t.text("hi"))
padding(16)           ≡  padding(p -> p.uniform(16))
fontSize(sizeSignal)  ≡  fontSize(s -> s.size(sizeSignal))
```

A shortcut emits **byte-identical** opcodes and carries no extra semantics
(source-surface only — [§Conformance](#conformance)). The config-lambda form
(`make(c -> …)` for views, `modifierName(c -> …)` for modifiers) remains
canonical and always sufficient.

- **Only a designated primary member** gets a shortcut. A config with **several
  equivalent members** (`Padding` edges, `Offset`/`Position` x+y, `Gauge`
  value/min/max, `Border` color+width(+radius), `FixedSize` horizontal+vertical,
  `AspectRatio` ratio+mode, a control's value+range) has **no** shortcut — use the
  lambda. `Padding` is the exception that proves it: its single-value primary is
  `uniform`, and per-edge padding is the lambda.
- **Both forms exist where the member has both**: a raw value and a
  `Signal<T>` (same rule as the config setters above). A two-way control value is
  never a shortcut — it stays a `WritableSignal` config member.
- The Java realization factors both forms through the **single `Modifiers`
  facade**: one `import static com.pathland.view.Modifiers.*;` brings in every
  lowercase modifier/style function, and each delegates to the same construction
  path (spec §5.6). There is exactly one construction path; the former per-view
  construction `.of(...)` factories and the former per-type `with(...)` overloads
  are **removed** — a shortcut (or the lambda / `.children(...)` / empty `make()`
  for config-less views) replaces every one. Only data/branch factories keep
  `of` (`Color.of`, `Fit.of`, `View.of`, `EnvironmentKey.of`, `Route.of`,
  `Params.of`, `Signals.*`).

Eligible members (non-exhaustive; the companion tables list the surface):

| Kind | Shortcut |
|------|----------|
| Views | `Text(text)`, `Image(source)`, `Icon(name)`, `Label(title)`, `ProgressView(value)`, `RoundedRectangle(cornerRadius)`, `Video(controller)`, `Shape(kind)` |
| Modifiers | `Padding(uniform)`, `FontSize(size)`, `ForegroundStyle(color)`, `Background(color)`, `Tint(color)`, `CornerRadius(radius)`, `Opacity(value)`, `Blur(radius)`, `Rotation(degrees)`, `ScaleEffect(value)`, `LineLimit(value)`, `Kerning`/`Tracking`/`BaselineOffset`/`LineSpacing`/`FontWidth`/`MinimumScaleFactor`/`LayoutPriority`/`ZIndex`(value), `FontFamily(family)`, `HueRotation(degrees)`, `Saturation`/`Contrast`/`Brightness`/`Grayscale`(value), `ColorMultiply(color)`, `ClipShape(shape)`, `AccessibilityLabel(text)`, `AccessibilityRole(role)`, `AccessibilityState(state)`, `Shadow(radius)`, `ImageSource(source)`, `Visible`/`Disabled`/`AllowsHitTesting`(bool), `Underline`/`Strikethrough`(bool), `PointerEvents(mask)`, `TapGesture(action)` |

Per-language adaptation summary:

| Language | Case | Composition mechanism | Example |
|----------|------|-----------------------|---------|
| SwiftUI | `camelCase` | trailing closures + result builder | `VStack(alignment: .center, spacing: 8) { Text("x").padding(16) }` |
| Java (`com.pathland.view`) | `camelCase` | `make(Consumer)` builder + `modifiers(...)` + `children(...)` | `VStack.make(v -> v.spacing(8)).children(Text.make(t -> t.text("x")).modifiers(padding(p -> p.uniform(16))))` |
| Rust (`pathland-view`) | `snake_case` | assoc fn + `ViewExt` trait + macros | `vstack![text("x").modifiers(Padding(16.0))]` |

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

**One signal per value.** A view or modifier holds **one `Signal<T>`** for each
reactive value — never a raw field *plus* a signal field for the same value. A
static value is authored through a raw config setter, which wraps a
`constant(...)` signal; the emitter treats a constant as a plain, non-reactive
property (no binding/effect). `WritableSignal<T>` extends `Signal<T>`, so a
two-way binding also satisfies a one-way member.

### 3.2 Two-way binding

A control's value is **a `WritableSignal<T>` supplied as a config value**:
`TextField.make(t -> t.placeholder("…").text(writable))`,
`Toggle.make(t -> t.isOn(writable))`,
`Slider.make(s -> s.value(writable).min(0).max(100))`. The flow:

1. At **mount**, the DSL control records a **value input** (a sink on the
   retained node) for the signal, and the emitter exposes routing registries to
   the host: tap actions, text inputs, value inputs, date inputs.
2. The host receives the control's raw event (`TEXT_CHANGED`,
   `VALUE_CHANGED`, `DATE_CHANGED`, or a composed tap) and calls the sink,
   which **writes into the signal**.
3. The signal flush re-emits **only that node's** delta (`SET_TEXT`,
   `SET_PROPERTY`, `SET_DATE`) back through the emitter.

Reading is equally fine-grained: a node's text or a property value can be
**bound to a signal** — `Text.make(t -> t.text(signal))`,
`.modifiers(foregroundStyle(f -> f.color(colorSignal)), fontSize(s -> s.size(sizeSignal)))`
— so a change re-emits only the bound node.

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

### 3.4 Structural reactivity (reactive bodies + slots)

The unit of structural reactivity is the **component**. Each child view rendered
by a parent is a tracked component slot: a signal read during its `body()` /
`renderNode()` re-renders **that component**, and the emitter diffs the fresh
subtree against the retained nodes. Java authors therefore use ordinary control
flow instead of a wrapper type:

| Canonical (SwiftUI-shaped) | Java DSL | Rust DSL | Emits |
|----------------------------|----------|----------|-------|
| `if cond { then } else { else }` in a result builder | plain `if` in `body()`; `View.of(() -> …)` for an inline expression | plain `if`/`match` in `build()` | `TREE` deltas (reconcile) |
| `switch value { case a -> v; default -> d }` | plain `switch` in `body()`; `View.of(() -> switch …)` inline | plain `if`/`match` in `build()` | `TREE` deltas (reconcile) |
| `ForEach(items) { … }` | plain `for` over a signal collection in `body()` | plain `for` in `build()` | `TREE` deltas (reconcile) |

**Java realization** — bare control flow in a component body:

```java
@Override
public View body() {
    if (loading.get()) return Spinner.make();
    if (error.get() != null) return ErrorView.make(error.get());

    var rows = new ArrayList<View>();
    for (var track : tracks.get()) rows.add(TrackRow.make(track));
    return VStack.make().children(rows.toArray(View[]::new));
}
```

`View.of(Supplier<View>)` is the **inline** form (Java has no `if` expression),
used where a branch must be an argument:

```java
VStack.make().children(
    View.of(() -> mode.get() == Mode.LIST ? listView : gridView));
```

**Component boundary.** A component's root chain — `body()` delegation,
modifiers, environment/tap/navigation wrappers — is one slot. Each child view
rendered by a primitive is a **child slot**. A read in a child therefore
re-renders only the child; a read in the parent re-renders the parent (and
rebuilds its children, diffing them). `NavigationContainer`, `SizeThatFits`, and
`View.of` are ordinary components, not a separate mechanism.

**Emission contract:**

1. On a signal change only the owning component re-renders; the emitter
   reconciles its subtree and emits only `TREE` / `PARAMETER` deltas
   (`CREATE_NODE` / `DELETE_NODE` / `INSERT_CHILD` / `REMOVE_CHILD` /
   `MOVE_CHILD`, plus changed text/properties).
2. **Identical recompute emits zero opcodes.** No selector equality predicate
   is needed; the retained-tree diff decides.
3. Components **nest** and reconcile independently. A parent re-render rebuilds
   its child slots, but an unchanged child subtree still emits nothing.
4. **Bindings stay fine-grained.** A signal bound to a node's text/property
   (`Text.make(t -> t.text(signal))`) emits one delta and does **not** re-run the
   body. Prefer bindings for high-frequency values.
5. **Non-signal reads are mount-time only.** A plain field read in `body()` is
   not a dependency; only signals (including `computed`) trigger a re-render.
6. The **root component type must stay stable** across re-renders; wrap a
   conditional root in `View.of` or another stable container.
7. Render bodies must be **pure and idempotent** and must not write signals.
   `MediaController` and `State` values must be fields, not created inline in
   `body()`.

**Rust delta (Phase 2 parity)**: the Rust DSL builds `Node` trees directly and
the host rebuilds + re-assigns ids + diffs on re-emit, so plain `if`/`match` in
`build()` has the same shape. The Rust engine does not yet track reads during
`build()`; that and the removal of `Conditional` are the Phase 2 parity work.

---

## 4. View surface

Component ids and wire behavior come from
[PRIMITIVES.md](./PRIMITIVES.md); the tables here give the **DSL signature**.
The `Java DSL` column is the reference realization shape. Every view exposes
the three operations of [§2](#2-canonical-signature-notation): `values` via
`make(...)`, `modifiers(...)`, and — where noted — `children(...)`.

### 4.1 Primitive drawing & visual nodes

| View | Canonical (SwiftUI-shaped) | Java DSL | Emits / Binds |
|------|---------------------------|----------|---------------|
| `Text` | `Text("…")` / `Text(Signal<String>)` | `Text.make(t -> t.text(String))` / `Text.make(t -> t.text(Signal<String>))` | `TEXT` 0x01; content `SET_TEXT` |
| `Image` | `Image("name")` / `Image(systemName:)` | `Image.make(i -> i.source(String))` / `Image.make(i -> i.systemName(String))` | `IMAGE` 0x02; `IMAGE_SOURCE` 0x1002 |
| `Icon` | — (semantic symbol; SwiftUI `Image(systemName:)`) | `Icon.make(i -> i.name(IconName))` / `Icon.make(i -> i.name(String))` / `Icon.make(i -> i.name(Signal<String>))` / `Icon.labeled(IconName, String)` | `ICON` 0x0B; `ICON_NAME` 0x1038 (spec/ICONS.md) |
| `Color` | `Color(.sRGB, red:green:blue:)` (a View) | `Color.make(c -> c.rgb(int,int,int))` (implements `View`); `Color.token(path)` as data | `COLOR` 0x03; `COLOR` 0x100A |
| `Shape` | `Rectangle()`, `Circle()`, `Capsule()`, `RoundedRectangle(cornerRadius:)` | `Rectangle.make()` / `Circle.make()` / `Capsule.make()` / `Ellipse.make()` / `RoundedRectangle.make(r -> r.cornerRadius(float))` / generic `Shape.make(s -> s.kind(ShapeKind))` | `SHAPE` 0x04; `SHAPE_KIND` 0x0006 |
| `Divider` | `Divider()` | `Divider.make()` | `DIVIDER` 0x05 |
| `Spacer` | `Spacer()` | `Spacer.make()` | `SPACER` 0x06 |
| `ProgressView` | `ProgressView(value:)` / `ProgressView()` | `ProgressView.make(p -> p.value(float))` / `ProgressView.make()` | `PROGRESS_VIEW` 0x07; `PROGRESS` 0x200E / `IS_INDETERMINATE` 0x200F |
| `Gauge` | `Gauge(value:in:)` | `Gauge.make(g -> g.value(float).min(float).max(float))` | `GAUGE` 0x08; `VALUE`/`MIN_VALUE`/`MAX_VALUE` |
| `Label` | `Label("title", systemImage:)` (composite) | `Label.make(l -> l.title(String).icon(String))` / `Label.make(l -> l.title(Signal<String>).icon(Icon))` — title-only, icon-only, or reactive parts; content supplied by the scoped `labelStyle` | a composite `HSTACK` + `IMAGE` (or `ICON`) + `TEXT` (PRIMITIVES.md "Composite views") — no component ID |

**`make()` for values.** Primitives are constructed by `make(...)`; a
config-less primitive (`Divider`, `Spacer`) takes an empty `make()`. `Label` is
a **composite** whose content is produced by the scoped `labelStyle`
([§5.7](#57-styleable-controls-the-style-contract)): `DefaultLabelStyle` renders
an `HStack` of an optional `IMAGE` and an optional `TEXT`,
`TitleOnlyLabelStyle`/`IconOnlyLabelStyle` render one part; the title always
drives the accessibility label. `Color` keeps its dual identity: `Color.make(c
-> c.rgb(..))` is a layout-greedy fill view, `Color.token(path)` is a data
value passed to style-taking modifiers ([§7](#7-theme-management)).

### 4.2 Layout & container nodes

Containers are **content-bearing**: they expose `children(...)`.

| View | Canonical (SwiftUI-shaped) | Java DSL | Emits / Binds |
|------|---------------------------|----------|---------------|
| `VStack` | `VStack(alignment:spacing:) { … }` | `VStack.make(v -> v.alignment(HorizontalAlignment).spacing(float)).children(View...)` | `VSTACK` 0x10; `SPACING` 0x0001, `ALIGNMENT` 0x0002, `CONTENT_MARGINS` 0x0005 |
| `HStack` | `HStack(alignment:spacing:) { … }` | `HStack.make(h -> h.alignment(VerticalAlignment).spacing(float)).children(View...)` | `HSTACK` 0x11 |
| `ZStack` | `ZStack(alignment:) { … }` | `ZStack.make(z -> z.alignment(Alignment)).children(View...)` | `ZSTACK` 0x12; `ALIGNMENT` |
| `Grid` | `Grid(columns:rows:alignment:spacing:) { … }` | `Grid.make(g -> g.columns(int).rows(int).tracks(List<GridItem>).alignment(Alignment).spacing(float)).children(View...)` | `GRID` 0x13; `GRID_COLUMNS` 0x001E, `GRID_ROWS` 0x001F, `GRID_TRACKS` 0x0020 |
| `GridRow` | `GridRow { … }` | `GridRow.make().children(View...)` | `GRID_ROW` 0x1D (structural — a grid child whose children are one row's cells; renders nothing outside a `GRID`) |
| `ScrollView` | `ScrollView { … }` | `ScrollView.make().children(View...)` | `SCROLLVIEW` 0x14 |
| `LazyVGrid` | `LazyVGrid(columns:alignment:spacing:) { … }` | `LazyVGrid.make(g -> g.columns(int).tracks(List<GridItem>).alignment(Alignment).spacing(float)).children(View...)` | `LAZY_VGRID` 0x15; `GRID_COLUMNS`, `GRID_TRACKS` |
| `LazyHGrid` | `LazyHGrid(rows:alignment:spacing:) { … }` | `LazyHGrid.make(g -> g.rows(int).tracks(List<GridItem>).alignment(Alignment).spacing(float)).children(View...)` | `LAZY_HGRID` 0x16; `GRID_ROWS`, `GRID_TRACKS` |
| `LazyVStack` | `LazyVStack(alignment:spacing:) { … }` | `LazyVStack.make(v -> v.alignment(HorizontalAlignment).spacing(float)).children(View...)` | `LAZY_VSTACK` 0x1B |
| `LazyHStack` | `LazyHStack(alignment:spacing:) { … }` | `LazyHStack.make(h -> h.alignment(VerticalAlignment).spacing(float)).children(View...)` | `LAZY_HSTACK` 0x1C |
| `SizeThatFits` | `ViewThatFits { … }` / `ViewThatFits(in: .horizontal) { … }` | `SizeThatFits.make().children(Fit.of(view, minWidth))...` (candidates `Fit` declared in surface order; `FIT_QUERY` thresholds ascending; the slot shows one child — the selected candidate — the others never transmit in the live form; a full-snapshot/SSR emission carries every candidate for a no-JS first paint gated by CSS container queries — the DSL is unchanged) | `SIZE_THAT_FITS` 0x17; `FIT_QUERY` 0x1039 (LIST); reacts to `FIT_CHANGED` 0x13 by swapping the slot's child (`TREE` deltas) |
| `ProxyView` | `RemoteView(endpoint:route:)` (no SwiftUI equivalent) | `ProxyView.make(p -> p.endpoint(String).route(String).credential(String))` | `PROXY_VIEW` 0x1E; child = the **origin subtree root** (served by another Pathland server, id-remapped); see [PROXY.md](./PROXY.md) |

**`ProxyView` (proxy view).** A container whose child subtree is served by
**another** Pathland server — it lets one app compose views from several servers
into a single unified document. It is **not** `children(...)`-bearing: the app
does not author the subtree; the proxying server fetches it from the origin
(blocking `META::RESYNC` at SSR, live deltas over the origin connection),
**remaps its node ids** into a disjoint high range, and splices it in
([PROXY.md](./PROXY.md)). `endpoint` is the origin server's base URL (app-
configured, never client-supplied); `route` is the origin route to render;
`credential` names the service credential the proxying server presents to the
origin. The proxy node lays out as a **transparent passthrough** container
(§[PRIMITIVES.md §ProxyView](./PRIMITIVES.md#proxyview--proxy_view-0x1e)) and
takes size modifiers like any view.

Alignment is **position-only** (SwiftUI/Compose parity): `VStack` takes a
`HorizontalAlignment` (leading/center/trailing), `HStack` a `VerticalAlignment`
(top/center/bottom), and `ZStack`/grids (plus the `frame` modifier's content
alignment) a 2D `Alignment` (topLeading … bottomTrailing). Stretching a child is
its `FILL` size kind, never an alignment.

**Grid counts**: `GRID_COLUMNS`/`GRID_ROWS` are **config values**
(SwiftUI `LazyVGrid(columns:)` / Compose `GridCells.Fixed(n)` parity), never
chainable modifiers. The grid's own size uses the universal `WIDTH`/`HEIGHT`
frame model — a `Frame` on a grid is a pixel box, never a count. A count is
`int` in the DSL and emits a positive F32; absent = auto-fit. The static `Grid`
takes optional `columns`/`rows` (both absent = SwiftUI `Grid` auto-fit);
`LazyVGrid` takes `columns`; `LazyHGrid` takes `rows`.

**`GridItem` (per-track sizes)**: a **`List<GridItem>`** config value expresses
per-track sizes (SwiftUI `GridItem` / Compose `GridCells` parity) —
`GridItem.flexible()` (`flex`), `GridItem.fixed(pts)` (`fixed:<pts>`),
`GridItem.adaptive(min)` (`adaptive:<min>`). The list serializes to the
`GRID_TRACKS` STRING property (comma-separated), which **takes precedence** over
the counts; a count is the `N`×`flexible` sugar. CSS-grid-native — renderers
without a native per-track equivalent size tracks naturally (renderer status,
spec/PRIMITIVES.md §grid model).

### 4.3 Semantic controls

Two-way value controls bind a `WritableSignal` (a config value). Actions bind a
callback. Controls marked **content-bearing** expose `children(...)` for their
content (their style turns it into the control's child, [§5.7](#57-styleable-controls-the-style-contract)).

| Control | Canonical (SwiftUI-shaped) | Java DSL | Emits / Binds |
|---------|---------------------------|----------|---------------|
| `Button` | `Button("title", action)` / `Button(action:label:)` | **content-bearing** — `Button.make(b -> b.title(String).action(Runnable))` and/or `.children(View)`; the title string is handed to the active `ButtonStyle`, an explicit child takes precedence ([§5.7](#57-styleable-controls-the-style-contract)) | `BUTTON` 0x20 (control node); tap = composed `POINTER_DOWN`+`POINTER_UP` |
| `TextField` | `TextField("placeholder", text: writable)` | `TextField.make(t -> t.placeholder(String).text(WritableSignal<String>))` | `TEXT_FIELD` 0x21; `TEXT_CHANGED` → text input |
| `SecureField` | `SecureField("…", text: writable)` | `TextField.make(t -> t.placeholder(String).text(writable).secure(true))` | `TEXT_FIELD` 0x21 + `IS_SECURE` 0x200D |
| `TextEditor` | `TextEditor(text: writable)` | `TextEditor.make(t -> t.text(WritableSignal<String>))` | `TEXT_EDITOR` 0x22 |
| `Toggle` | `Toggle("label", isOn: writable)` | `Toggle.make(t -> t.style(ToggleStyle).label(String).isOn(WritableSignal<Boolean>))` | `TOGGLE` 0x24; `VALUE_CHANGED` → value input; `TOGGLE_STYLE` 0x2018 |
| `Slider` | `Slider(value: writable, in: min...max)` | `Slider.make(s -> s.value(WritableSignal<Float>).min(float).max(float).onEditingChanged(Consumer<Boolean>))` | `SLIDER` 0x25; `VALUE_CHANGED` → value input |
| `Stepper` | `Stepper("label", value: writable, in: min...max, step:)` | `Stepper.make(s -> s.value(WritableSignal<Float>).min(float).max(float).step(float).label(String))` | `STEPPER` 0x26; `VALUE_CHANGED` → value input |
| `DatePicker` | `DatePicker("label", selection: writable, displayedComponents:)` | `DatePicker.make(d -> d.mode(DatePickerMode).selection(WritableSignal<Integer>))` | `DATE_PICKER` 0x27; `DATE_CHANGED` → date input; value via `PARAMETER::SET_DATE` |
| `Picker` | `Picker("label", selection: writable) { options }` | **content-bearing** — `Picker.make(p -> p.style(PickerStyle).selection(WritableSignal<Integer>)).children(View... options)` | `PICKER` 0x28; `VALUE_CHANGED` (index) → value input |
| `Menu` | `Menu { actions } label: { trigger }` | **content-bearing** — `Menu.make(m -> m.trigger(View).selection(WritableSignal<Integer>)).children(View... actions)` | `MENU` 0x29; `VALUE_CHANGED` (item index) → value input |
| `ColorPicker` | `ColorPicker("label", selection: writable)` | `ColorPicker.make(c -> c.selection(WritableSignal<Color>))` | `COLOR_PICKER` 0x2A; `VALUE_CHANGED` (packed `0xAARRGGBB` as f32) → value input |

**Value bindings.** The value binding is a **config value** — the DSL reads the
initial value from the signal (the single source of truth) instead of SwiftUI's
labeled `value:in:` range binding. `Button` binds an action and content rather
than SwiftUI's trailing-action form. `DatePicker` binds **days-since-epoch**
(`WritableSignal<Integer>`) rather than a date object — the protocol's
`SET_DATE`/`DATE_CHANGED` two-field encoding is days + millis-of-day (see
[§8](#8-java-dsl-convergence-adopted)).

**Control guarantees** (from PRIMITIVES.md): a control without children renders
in **Native Token Mode** (native OS control); a control with a child subtree
renders in **Composite Override Mode** (custom chrome wrapped with native
gesture semantics). A **style** supplies that child subtree — the control owns
the native component + interaction, the style owns the content
([§5.7](#57-styleable-controls-the-style-contract)). Renderers MUST gate
value/text/date events by `BINDING_ID`, `ACTION_ID`, or the `EVENT_LISTENERS`
bitmask (transport-aware event guards) — see [EVENTS.md](./EVENTS.md).

### 4.4 Gestures

| Gesture | Canonical (SwiftUI-shaped) | Java DSL | Rust DSL |
|---------|---------------------------|----------|----------|
| tap | `.onTapGesture { action }` | `.modifiers(tapGesture(g -> g.action(Runnable)))` | `.modifiers(TapGesture::new(f))` |
| raw pointer | `.gesture` composition from raw inputs | `.modifiers(pointerEvents(p -> p.mask(int)))` | `.pointer_events(u32 mask)` |

Tap is **not** a protocol event: it is composed app-side from `POINTER_DOWN`
then `POINTER_UP` on the same target (`EVENT_LISTENERS` bits 0|2). The
`TapGesture` modifier value declares those listeners and records the action;
the host routes the recognized tap via the emitter's tap-action registry.

### 4.5 Navigation

Navigation is **reactive structure over a route signal**
([§3.4](#34-structural-reactivity-reactive-bodies--slots)): a
`NavigationContainer` is a component whose tracked render selects the current
route-table destination. The wire surface is minimal and entirely optional:
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
  platform's native navigation container where one exists, and a renderer-drawn
  back affordance where none exists.
  `NavigationContainer.make(n -> n.router(router))`.
- `Custom`: the **developer owns all navigation UI** — they draw their own
  back buttons / bars in the destinations and call `router.back()` /
  `navigate(...)` directly; the renderer adds no chrome (no native header-bar
  back button, no DOM back button).
  `NavigationContainer.make(n -> n.router(router).chrome(Chrome.CUSTOM))`.
  Renderers treat a missing `NAV_CHROME` as `PlatformDefault`.

| View | Canonical (SwiftUI-shaped) | Java DSL | Rust DSL | Emits / Binds |
|------|----------------------------|----------|----------|---------------|
| `NavigationContainer` | `NavigationStack(path:) { destination(for:) }` | `NavigationContainer.make(n -> n.router(Router).chrome(Chrome))` | `NavigationContainer::with(|n| n.router(router))` | a `Group` slot + `ROUTE` `0x2019`, `NAV_DEPTH` `0x201A`, `NAV_CHROME` `0x201B`, `TRANSITION` `0x1031`; destination swap = `TREE` deltas |
| `NavigationLink` | `NavigationLink("label", value:)` | `NavigationLink.make(l -> l.label(String).router(Router).to(String))` / `NavigationLink.make(l -> l.label(String).to(String))` (router-agnostic) | `navigation_link(...)` | a `BUTTON` whose tap pushes `to` (via the router, or resolved to the nearest enclosing router when router-agnostic) |
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

**Reserved framework root** — the host reserves **`/_pathland/**`** for its own
system endpoints, so app routes never collide with them: the WebSocket lives at
`/_pathland/ws`, the DOM-client bundle under a **content-hashed name**
(`/_pathland/dom-renderer-<sha256>.js`; the current name is published next to the
copies in the one-line pointer `/_pathland/dom-renderer.current`, read by the SSR
layer so the page always references the built artifact — the fresh URL per build
busts immutable asset caches), the
renderer-owned icon glyphs at `/_pathland/icons/<name>.svg` (served from the
shared HTML-renderer library — `pathland_html_icon_svg` over the C ABI — which
the DOM client lazily fetches when a delta references an `ICON_NAME` the page
did not embed), and the host's asset mount at `/_pathland/assets/**`. App routes
are everything else (served by the SSR catch-all). The DOM client reads the
base from the SSR page's `data-pathland-base` attribute (default `/_pathland`),
so a host can relocate it behind a proxy. Asset references in the UI model are
**absolute** (`/_pathland/assets/icons/home.svg`) so deep-linked routes resolve
correctly.

**The `Navigation` facade** — the ergonomic entry point. `Navigation.navigator`
collapses the route table + router + seeding into one readable flow, and
`Navigation.container(router)` is the container:

```java
Router router = Navigation.navigator("/kitchen")     // seeds the initial path
    .route("/",          new HomeView())             // View overload (no params)
    .route("/users/:id", params -> new UserView(params.intValue("id")))
    .fallback(new NotFoundView())
    .build();

View shell = Navigation.container(router);           // == NavigationContainer.make(n -> n.router(router))
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
  are bound down a subtree with the `EnvironmentBinding` modifier value and read
  with `Environment.value(key)`; nearest wins. Styles use it too —
  `ButtonStyle` binds `Environment.BUTTON_STYLE`
  (`EnvironmentKey<ButtonStyle>`), so a style scoped above a structural
  container survives its destination re-render.
- **The router as a scoped environment value** (SwiftUI `.environment` style):
  `Navigation.ROUTER` is an `EnvironmentKey<Router>`. A `NavigationContainer` scopes
  it to its destination subtree; any component reads
  `Environment.value(Navigation.ROUTER)` during render — no constructor threading,
  nearest binding wins (nested containers override). Each component slot captures
  the environment scope at render time and re-applies it on its own re-renders.
- **Environment reads always return a signal** (`Environment.value(key)` →
  `Signal<T>`): a value injected as a `Signal` comes back as the same instance, so a
  node bound to it (e.g. `Text.make(t -> t.text(Environment.value(key)))`) re-emits
  when it changes; a plain value is wrapped in a constant signal (`.get()` gives the
  value). Inject a reactive value with the `EnvironmentBinding` modifier. A read
  made **before the key is bound** (e.g. in a field initializer, before the
  enclosing binding scope is pushed) returns a **lazy signal** that captures the
  binding on the first `.get()` during render — the SwiftUI `@Environment` field
  style:
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
        root.modifiers(environmentBinding(e -> e.key(Platform.ACTIVE_PATH).value(activePath))),
        new Environment(state));
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
  a `PathChange` modifier listener (`pathChange(p -> p.listener(path -> …))`)
  (fires on every change, including the initial value).
- `NAVIGATE` events: a URL updates `activePath` (→ bound router re-routes guard-aware,
  `PathChange` listeners fire); a back (no URL) goes to `RenderResult.navigateHandler`
  → `router.pop()` — meaningful only when a router exists.

**Any component can change the route** — declarative navigation intents. A
component anywhere *inside* a `NavigationContainer` can change the route without
threading a `Router` by hand, using the `NavigationIntent` modifier:

```java
Button.make(b -> b.title("Go to kitchen").action(...))
      .modifiers(NavigationIntent.navigate("/kitchen"));   // direct selection
Button.make(b -> b.title("Open item").action(...))
      .modifiers(NavigationIntent.push("/item/1"));        // drill-down (back-stack)
Button.make(b -> b.title("Swap").action(...))
      .modifiers(NavigationIntent.replace("/settings"));   // guard redirect / replace
NavigationLink.make(l -> l.label("Users").to("/users"));   // router-agnostic (pushes)
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
slot and may be rendered as the platform's native navigation container. The
contract keeps the renderer stateless:

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
- Platforms with no native navigation container render the slot as an ordinary
  container that swaps children in
  place; the app's own back-stack is the only stack. The app does not branch on
  platform — the same `NavigationContainer` works on both, and the renderer's
  use (or not) of a native container is purely a presentation decision.

**Java realization**: `Router` / `RouteTable` live in
`com.pathland.view.router`; `NavigationIntent` there too; `View.of` in
`com.pathland.view`. The `NavigationContainer` is an ordinary component whose
tracked render reads the route signal, so a route change re-renders only its
destination slot ([§3.4](#34-structural-reactivity-reactive-bodies--slots)).

---

## 5. Modifier surface

Properties, value types, and emission rules come from
[MODIFIERS.md](./MODIFIERS.md); the tables here give the **DSL signature**. A
compound SwiftUI modifier expands to **one `SET_PROPERTY` per underlying
property** (`.frame` → `WIDTH` + `HEIGHT` + `ALIGNMENT`; `.border` →
`BORDER_COLOR` + `BORDER_WIDTH`; `.shadow` → `SHADOW_COLOR` + `SHADOW_RADIUS` +
`SHADOW_X` + `SHADOW_Y`). The `Java DSL` column is the reference realization.

### 5.0 Authoring a modifier

**One mechanism for every modifier.** Each entry below is a `ViewModifier`
value, applied through the shared `modifiers(...)` operation:

- **Parameterized modifiers** are created with `modifierName(c -> …)` (a fluent
  config builder), e.g. `padding(p -> p.uniform(16))`,
  `frame(f -> f.width(100).height(24))`.
- **Single-value modifiers** — a font, a font weight, an alignment, a style —
  may pass the value directly, because the value type *is* the modifier: `FontWeight.BOLD`,
  `TextAlignment.CENTER`, `Font.headline()`, `MyButtonStyle`.
- A modifier's type is **never** suffixed with `Mod` (§1).

```java
Text.make(t -> t.text("hi")).modifiers(
        padding(p -> p.uniform(16)),
        frame(f -> f.width(100).height(24)),
        FontWeight.BOLD,
        foregroundStyle(f -> f.color(Color.WHITE)));
```

**Every value member above also accepts a `Signal<T>`**
(`padding(p -> p.uniform(padSignal))`, `frame(f -> f.width(widthSignal))`,
`foregroundStyle(f -> f.color(colorSignal))`) — see
[§2](#2-canonical-signature-notation). Because `Signal<T>` is a
`@FunctionalInterface`, a bare lambda would also type-check but would register a
(harmless) reactive binding; **literals MUST use the raw overload**, which wraps
a `ConstantSignal` (no binding overhead).

### 5.1 Layout & frame

| Modifier | Canonical (SwiftUI-shaped) | Java DSL | Property(ies) |
|----------|---------------------------|----------|---------------|
| `frame` | `.frame(width:height:alignment:)` | `.modifiers(frame(f -> f.width(w).height(h).alignment(a)))` — the 2D `Alignment` positions the content within the box; omitted → no `ALIGNMENT` emitted, a stack's own alignment is preserved | `WIDTH` 0x100B, `HEIGHT` 0x100C, `ALIGNMENT` 0x0002 (only when provided) |
| `frame(min:…)` | `.frame(minWidth:idealWidth:maxWidth:minHeight:idealHeight:maxHeight:)` | `.modifiers(frame(f -> f.minWidth(..).idealWidth(..).maxWidth(..).minHeight(..).idealHeight(..).maxHeight(..)))` (the `Frame.Builder`; unset bounds are omitted — NaN/∞ = no limit) | `MIN_WIDTH` 0x0012 … `MAX_HEIGHT` 0x0017 |
| `padding` | `.padding(_:)` / `.padding(_:edges:)` | `.modifiers(padding(p -> p.uniform(float)))` / `.modifiers(padding(p -> p.edges(t, r, b, l)))` | `PADDING` 0x1011 / `PADDING_TOP` 0x1012 … `PADDING_LEFT` 0x1015 |
| `offset` | `.offset(x:y:)` | `.modifiers(offset(o -> o.x(float).y(float)))` | `OFFSET_X` 0x000E, `OFFSET_Y` 0x000F |
| `position` | `.position(x:y:)` | `.modifiers(position(p -> p.x(float).y(float)))` | `POSITION_X` 0x0010, `POSITION_Y` 0x0011 |
| `fixedSize` | `.fixedSize()` / `.fixedSize(horizontal:vertical:)` | `.modifiers(fixedSize())` / `.modifiers(fixedSize(f -> f.horizontal(boolean).vertical(boolean)))` | `FIXED_SIZE_HORIZONTAL` 0x0018, `FIXED_SIZE_VERTICAL` 0x0019 |
| `layoutPriority` | `.layoutPriority(_:)` | `.modifiers(layoutPriority(l -> l.value(float)))` | `LAYOUT_PRIORITY` 0x001A |
| `zIndex` | `.zIndex(_:)` | `.modifiers(zIndex(z -> z.value(float)))` | `Z_INDEX` 0x100F |
| `aspectRatio` | `.aspectRatio(_:contentMode:)` | `.modifiers(aspectRatio(a -> a.ratio(float).contentMode(ContentMode)))` | `ASPECT_RATIO` 0x001B, `CONTENT_MODE` 0x001C |
| `scaledToFit` | `.scaledToFit()` | `.modifiers(scaledToFit())` | `CONTENT_MODE` 0x001C (Fit) |
| `scaledToFill` | `.scaledToFill()` | `.modifiers(scaledToFill())` | `CONTENT_MODE` 0x001C (Fill) |
| `minimumScaleFactor` | `.minimumScaleFactor(_:)` | `.modifiers(minimumScaleFactor(m -> m.value(float)))` | `MINIMUM_SCALE_FACTOR` 0x001D |

**Semantics**: `WIDTH`/`HEIGHT` use the sentinels `FILL` (−1.0 = expand) and
`HUG_CONTENT` (−2.0 = intrinsic); omitting an axis leaves it to the native
renderer. SwiftUI's `.frame(maxWidth: .infinity)` / `maxHeight: .infinity` is
written by passing `Float.POSITIVE_INFINITY` (Java) / `f32::INFINITY` (Rust) to
the width/height builder members, which is normalized to `FILL` before emission.
In the min/ideal/max builder an infinite `maxWidth`/`maxHeight` means *no limit*
(the bound is omitted). `OFFSET` is a post-layout translation; `POSITION` is
absolute placement within the parent.

### 5.2 Text formatting

| Modifier | Canonical (SwiftUI-shaped) | Java DSL | Property(ies) |
|----------|---------------------------|----------|---------------|
| `font` | `.font(.system(size:))` | `.modifiers(Font.system(float))` / `Font.system(size, weight, design)` | `FONT_SIZE` 0x1007 (+ `FONT_WEIGHT`/`FONT_DESIGN` when given) |
| `font` (predefined) | `.font(.largeTitle)` | `.modifiers(Font.headline())` (the `Font` value *is* the modifier) | `TEXT_STYLE` 0x1032 |
| `fontWeight` | `.fontWeight(_:)` | `.modifiers(FontWeight.BOLD)` (the enum *is* the modifier) | `FONT_WEIGHT` 0x1008 (100–900) |
| `font` (custom) | `.font(.custom(name:size:))` | `.modifiers(Font.custom("Georgia", 20))` | `FONT_FAMILY` 0x1009 |
| `fontStyle` | `.italic()` / `.fontDesign(_:)` | `.modifiers(italic())` / `.modifiers(FontStyle.ITALIC)` / `.modifiers(FontDesign.SERIF)` | `FONT_STYLE` 0x1017, `FONT_DESIGN` 0x1018 |
| `fontWidth` | `.fontWidth(_:)` | `.modifiers(fontWidth(w -> w.value(float)))` | `FONT_WIDTH` 0x1019 |
| `kerning` | `.kerning(_:)` | `.modifiers(kerning(k -> k.value(float)))` | `KERNING` 0x101A |
| `tracking` | `.tracking(_:)` | `.modifiers(tracking(t -> t.value(float)))` | `TRACKING` 0x101B |
| `baselineOffset` | `.baselineOffset(_:)` | `.modifiers(baselineOffset(b -> b.value(float)))` | `BASELINE_OFFSET` 0x101C |
| `lineSpacing` | `.lineSpacing(_:)` | `.modifiers(lineSpacing(l -> l.value(float)))` | `LINE_SPACING` 0x101D |
| `lineLimit` | `.lineLimit(_:)` | `.modifiers(lineLimit(l -> l.value(int)))` | `LINE_LIMIT` 0x000B (0 = unlimited) |
| `multilineTextAlignment` | `.multilineTextAlignment(_:)` | `.modifiers(TextAlignment.CENTER)` (the enum *is* the modifier) | `TEXT_ALIGNMENT` 0x000C |
| `truncationMode` | `.truncationMode(_:)` | `.modifiers(Truncation.TAIL)` (the enum *is* the modifier) | `TRUNCATION_MODE` 0x000D |
| `textCase` | `.textCase(_:)` | `.modifiers(TextCase.UPPERCASE)` (the enum *is* the modifier) | `TEXT_CASE` 0x101E |
| `underline` | `.underline()` | `.modifiers(underline())` / `.modifiers(underline(u -> u.enabled(boolean)))` | `UNDERLINE` 0x101F |
| `strikethrough` | `.strikethrough()` | `.modifiers(strikethrough())` / `.modifiers(strikethrough(s -> s.enabled(boolean)))` | `STRIKETHROUGH` 0x1020 |
| `foregroundStyle` | `.foregroundStyle(_:)` | `.modifiers(foregroundStyle(f -> f.color(Color)))` / `.modifiers(foregroundStyle(f -> f.color(Signal<Color>)))` | `COLOR` 0x100A |
| `tint` | `.tint(_:)` | `.modifiers(tint(t -> t.color(Color)))` | `TINT` 0x1030 |

> **`Color` is never a modifier** — there is no `.color()` and
> `.foregroundColor(_:)` is deprecated. Foreground styling is
> `.foregroundStyle(_:)`.

### 5.3 Appearance & effects

| Modifier | Canonical (SwiftUI-shaped) | Java DSL | Property(ies) |
|----------|---------------------------|----------|---------------|
| `background` | `.background(_:)` | `.modifiers(background(b -> b.color(Color)))` / `.modifiers(background(b -> b.color(Signal<Color>)))` | `BACKGROUND_COLOR` 0x1001 |
| `border` | `.border(_:width:)` | `.modifiers(border(b -> b.color(Color).width(float).radius(float)))` | `BORDER_COLOR` 0x1004, `BORDER_WIDTH` 0x1003, `BORDER_RADIUS` 0x1005 |
| `border` (edges) | `.border(_:width:edges:)` | (not exposed) | + `BORDER_EDGES` 0x1016 (u32 bitmask: `TOP`=1, `LEADING`=2, `BOTTOM`=4, `TRAILING`=8) |
| `cornerRadius` | `.cornerRadius(_:)` | `.modifiers(cornerRadius(c -> c.radius(float)))` | `BORDER_RADIUS` 0x1005 |
| `shadow` | `.shadow(color:radius:x:y:)` | `.modifiers(shadow(s -> s.color(Color).radius(float).x(float).y(float)))` / `.modifiers(shadow(s -> s.radius(float)))` | `SHADOW_COLOR` 0x1021, `SHADOW_RADIUS` 0x1022, `SHADOW_X` 0x1023, `SHADOW_Y` 0x1024 |
| `opacity` | `.opacity(_:)` | `.modifiers(opacity(o -> o.value(float)))` | `OPACITY` 0x100D |
| `blur` | `.blur(radius:)` | `.modifiers(blur(b -> b.radius(float)))` | `BLUR_RADIUS` 0x1025 |
| `saturation` | `.saturation(_:)` | `.modifiers(saturation(s -> s.value(float)))` | `SATURATION` 0x1026 |
| `contrast` | `.contrast(_:)` | `.modifiers(contrast(c -> c.value(float)))` | `CONTRAST` 0x1027 |
| `brightness` | `.brightness(_:)` | `.modifiers(brightness(b -> b.value(float)))` | `BRIGHTNESS` 0x1028 |
| `grayscale` | `.grayscale(_:)` | `.modifiers(grayscale(g -> g.value(float)))` | `GRAYSCALE` 0x1029 |
| `hueRotation` | `.hueRotation(_:)` | `.modifiers(hueRotation(h -> h.degrees(float)))` | `HUE_ROTATION` 0x102A |
| `colorMultiply` | `.colorMultiply(_:)` | `.modifiers(colorMultiply(c -> c.color(Color)))` | `COLOR_MULTIPLY` 0x102B |
| `colorInvert` | `.colorInvert()` | `.modifiers(colorInvert())` | `COLOR_INVERT` 0x102C |
| `clipped` | `.clipped()` | `.modifiers(clipped())` | `CLIPS_TO_BOUNDS` 0x1010 |
| `clipShape` | `.clipShape(_:)` | `.modifiers(clipShape(c -> c.shape(ShapeKind)))` | `CLIPS_TO_BOUNDS` 0x1010 + `SHAPE_KIND` 0x0006 |

**Border** canonical order is color-then-width; corner radius is a separate
`CornerRadius` modifier.

### 5.4 Transform

| Modifier | Canonical (SwiftUI-shaped) | Java DSL | Property(ies) |
|----------|---------------------------|----------|---------------|
| `rotationEffect` | `.rotationEffect(_:anchor:)` | `.modifiers(rotation(r -> r.degrees(float)))` | `ROTATION_DEGREES` 0x102D |
| `scaleEffect` | `.scaleEffect(_:anchor:)` | `.modifiers(scaleEffect(s -> s.value(float)))` | `SCALE` 0x102E |

Anchor is renderer-token-owned (center default); the protocol does not transmit
anchors. Transforms do not affect layout.

### 5.5 Interaction & state

| Modifier | Canonical (SwiftUI-shaped) | Java DSL | Property(ies) |
|----------|---------------------------|----------|---------------|
| `hidden` | `.hidden()` | `.modifiers(hidden())` / `.modifiers(visible(v -> v.visible(boolean)))` | `VISIBLE` 0x100E |
| `disabled` | `.disabled(_:)` | `.modifiers(disabled(d -> d.disabled(boolean)))` | `ENABLED` 0x2003 (inverse: 1 = interactive) |
| `allowsHitTesting` | `.allowsHitTesting(_:)` | `.modifiers(allowsHitTesting(a -> a.allowed(boolean)))` | `ALLOWS_HIT_TESTING` 0x102F |
| `controlSize` | `.controlSize(_:)` | `.modifiers(ControlSize.LARGE)` (the enum *is* the modifier) | `CONTROL_SIZE` 0x200C |
| `accessibilityLabel` | `.accessibilityLabel(_:)` | `.modifiers(accessibilityLabel(a -> a.text(String)))` | `LABEL` 0x200A |
| `accessibilityRole` | `.accessibilityRole(_:)` | `.modifiers(accessibilityRole(a -> a.role(int)))` | `ROLE` 0x2001 |
| `accessibilityState` | `.accessibilityState(_:)` | `.modifiers(accessibilityState(a -> a.state(int)))` | `STATE` 0x2002 |
| `modifier` (custom) | `.modifier(_:)` | `.modifiers(ViewModifier)` | composes core modifiers |
| `buttonStyle` | `.buttonStyle(_:)` | `.modifiers(ButtonStyle)` | environment-scoped (`Environment.BUTTON_STYLE` key, nearest-wins); the style supplies the button's content ([§5.7](#57-styleable-controls-the-style-contract)) |
| `labelStyle` | `.labelStyle(_:)` | `.modifiers(LabelStyle)` | environment-scoped (`Environment.LABEL_STYLE` key, nearest-wins); DSL-only control flow — no wire property |
| `videoStyle` | `.videoStyle(_:)` | `.modifiers(VideoStyle)` | environment-scoped (`Environment.VIDEO_STYLE` key, nearest-wins); the style supplies the `VIDEO` surface's control content ([§5.7](#57-styleable-controls-the-style-contract)) |
| `environment` | `.environment(_:_:)` | `.modifiers(environmentBinding(e -> e.key(EnvironmentKey).value(value)))` | environment-scoped (a typed `EnvironmentKey`, nearest-wins); no wire property |
| `windowMetadata` | (platform shell metadata) | `.modifiers(windowMetadata(w -> w.title(signal).description(signal).locale(value)))` | `META::SET_WINDOW_METADATA` fields (`TITLE`/`DESCRIPTION`/`LOCALE`) — renderer maps them to its window chrome |
| `focusable` | `.focusable(_:)` | (via the `PointerEvents` modifier) | **no property** — declares `FOCUS` listener bit 5; observe `FOCUS_CHANGED` |
| raw listeners | `.pointerEvents(mask)` / `.pointer_events(mask)` | `.modifiers(pointerEvents(p -> p.mask(int)))` | `EVENT_LISTENERS` 0x2005 (u32 bitmask, bits per EVENTS.md) |

**Notes**: `visible(boolean)` is a Pathland extra (SwiftUI only has
`.hidden()` — `Visible`/`Hidden`). `accessibilityRole`/`accessibilityState`
take raw `int` codes today rather than a typed enum. Raw input listeners are
exposed through the `PointerEvents` modifier value — there is no per-event sugar
(e.g. a `.focusable` or `.onSubmit` that sets the matching bit). `environment`
is the one inheritance/scoping modifier
([§6 rule 6](#6-authoring-conventions)), alongside the style modifiers.

**Window metadata is root-scoped.** `windowMetadata` applies to the whole
hosting surface, not to the subtree it wraps. Values are signals (a plain value
is wrapped as a constant), so a route's title is a `computed` over the router:
`windowMetadata(w -> w.title(computed(() -> titleFor(router.path()))))`. A
signal change re-emits only that `META::SET_WINDOW_METADATA` field; the renderer
maps it to the window/tab title and description.

### 5.6 Custom modifiers (developer-authored)

In SwiftUI any developer can define a modifier that applies to **any** existing
view — and Pathland is the same. This is a **first-class, MUST** property of
every conformant DSL: modifiers are never hard-bound to a view type.

- **Applicable to any view.** A modifier works on a library primitive
  (`Text`), a container (`VStack`), a control (`Button`), a custom view, or an
  already-modified view. The same modifier value applies everywhere.
- **One mechanism.** Core and application-authored modifiers are the **same
  surface**: a `ViewModifier` value applied via `.modifiers(...)`. There is
  **no per-modifier sugar on the view type**; a parameterized modifier is
  constructed by a lowercase static function named for the modifier ([§5.0](#50-authoring-a-modifier))
  and single-value modifiers pass the value directly.

| | Canonical | Java DSL (`com.pathland.view`) | Rust DSL (`pathland-view`) |
|-|-----------|-------------------------------|----------------------------|
| authoring | `struct Card: ViewModifier { func body(content: Content) -> some View }` | `@FunctionalInterface ViewModifier { View body(View content) }` | `trait ViewModifier { fn apply(&mut Node) }` |
| applying | `content.modifier(Card())` | `content.modifiers(card(c -> …))` / `content.modifiers(A, B)` | `content.modifiers(Card)` |
| single-value | `.modifier(Padding(16))` | `content.modifiers(padding(p -> p.uniform(16)))` | `content.modifiers(Padding(16.0))` |

**Composition**: a custom modifier composes core modifiers **or** wraps
`content` with additional structure (a background, an overlay, a frame) and
returns the decorated view — exactly SwiftUI's `body(content: Content)`.

**Emission contract**: a custom modifier composed from core modifiers emits the
same one-`SET_PROPERTY`-per-property deltas as writing the modifiers inline;
emission stays diff-based, and a renderer that cannot apply a modifier
**allows and ignores** it.

**Realization deltas**:
- **Rust** — every core modifier (`Padding`, `FontSize`, `ForegroundStyle`,
  `Frame`, …) implements `ViewModifier`, and `.modifiers(...)` applies them
  (a single modifier or a tuple).
  Caveat: `apply(&mut Node)` mutates the built node's properties only — it
  cannot wrap `content` with new structure (the Java/SwiftUI `body(content)`
  form can). A conformant DSL's custom-modifier mechanism should support
  wrapping.
- **Java** — every core modifier is a `ViewModifier` value applied via
  `.modifiers(...)`; `View` has no modifier sugar, and a style
  (`ButtonStyle`, …) is itself a `ViewModifier` value (it binds the
  `Environment.BUTTON_STYLE` key down the subtree — subtree scoping is
  environment-only). Application-authored `ViewModifier.body(View)` is
  SwiftUI-shaped and can wrap content with structure.

#### Media (`MediaController`)

A media playback is a **state object**, not a view (spec/MEDIA.md; the SwiftUI
`AVPlayer` analog). `MediaController` owns the transport as signals — commands
(`source()`, `playing()`, `position()`, `volume()`, `muted()`, `loop()`,
`rate()`) and reports (`time()`, `duration()`, `volumeReport()`) — plus
`play()/pause()/toggle()/seek()/setVolume()`. The emitter materializes one
non-visual `MEDIA_CONTROLLER` node per controller (keyed by object identity), so
playback is stable across structural swaps.

- **Audio has no view**: bind ordinary views (buttons/sliders) to the
  controller's signals. Include `controller.attach()` once in the tree — a
  non-visual marker that materializes the controller node — placed **outside**
  any swapping branch (e.g. a `SizeThatFits` slot).
- **Video** is the only media view: `Video.make(controller)` renders the
  controller's output. `VideoStyle` supplies a custom control surface; the
  default `NativeVideoStyle` shows renderer-native controls.
- A `MediaController` may be rebound to the app's own (e.g. persisted) signals
  (`controller.playing(signal)`), so the controller drives the same state the
  rest of the UI reads.

```java
MediaController player = new MediaController(computed(() -> track.get().audio()));
player.playing(playing.signal()).position(seekRequest.signal()).volume(volume.signal());
player.time(position.signal()).onEnded(this::next);

VStack.make().children(
    player.attach(),                                   // audio: non-visual
    Button.make(b -> b.action(player::toggle)),
    Slider.make(s -> s.value(player.time())),
    Video.make(player));                               // video: the only media view
```

## 5.7 Styleable controls (the style contract)

A **style** customizes a view or control's *content*; the control owns its
*component and interaction*. This is one contract for every styleable surface
(`ButtonStyle`, `LabelStyle`, `VideoStyle`, …), and it is the
author-facing shape of the protocol's **Composite Override Mode**
([PRIMITIVES.md §2](./PRIMITIVES.md#2-dual-mode-rendering-pipeline--platform-delegation)).

- **The control owns the native component and the interaction.** A semantic
  control renders its **native control node** (its component id — `BUTTON`,
  `VIDEO`, …) and attaches its behavior to *that* node: the tap action /
  `EVENT_LISTENERS` (Button), the value/text/date sink, or the media binding
  (`Video`). The **whole control is interactive**, including its padding.
- **The style owns the content.** A style returns the control's **content view**
  from `makeBody(Config)`; the control attaches it as the control node's
  **single child**. The content keeps its own component(s), layout, and
  properties — a style **never** sets or overwrites the control's component and
  never wires the interaction. The result is a control with children → protocol
  **Composite Override Mode**.
- **The style consumes the control's config.** The `Config` a style's
  `makeBody(Config)` receives is the **same type** the view configured through
  `make(...)` ([§2](#2-canonical-signature-notation)): `ButtonStyle.makeBody(Button.Config)`,
  `LabelStyle.makeBody(Label.Config)`,
  `VideoStyle.makeBody(VideoStyle.Configuration)`. The style **reads** the config (the
  control's values: content, action, label, media state); it does not mutate it.
- **A style is a modifier.** Styles are `ViewModifier` values scoped down a
  subtree with `.modifiers(<Style>)`, binding the style's `EnvironmentKey` over
  the wrapped subtree (nearest wins); the control reads it via its
  `Environment.<name>Style()` accessor. This is the one inheritance mechanism
  ([§6 rule 6](#6-authoring-conventions)); `ButtonStyle` binds
  `Environment.BUTTON_STYLE`, `VideoStyle` `Environment.VIDEO_STYLE`, `LabelStyle`
  `Environment.LABEL_STYLE`.
- **Button content precedence (three developer options).** A `Button`'s content
  resolves as: (1) an **explicit child** supplied via `.children(...)` wins;
  else (2) the config **title** string (wrapped as a `Text`); else (3) the
  **style-supplied content** from `makeBody`. The `buttonStyle` modifier itself
  (option 3) lets a developer hand content in through the style. This mirrors
  SwiftUI's multiple button-authoring forms.
- **Defaults preserve the native path.** The default style contributes the
  control's natural content: `PlainButtonStyle` → the label;
  `NativeVideoStyle` → **no content** (an empty-content
  sentinel), so the control stays a **leaf** in Native Token Mode and the
  renderer shows native media controls; `DefaultLabelStyle` → title + icon.
- **Text/foreground modifiers cascade to content.** A `.foregroundStyle`/
  `.fontSize` (and the other inherited text modifiers) applied *to the control*
  reach the content's text — SwiftUI environment semantics. In the Java DSL the
  properties ride the control node; renderers cascade them to child text.
- **Main-axis alignment is the content's own layout — no protocol property.**
  A control's content box centers its child by default; to left-align a
  composite label (e.g. a navigation row), make the label full-width
  (`frame(f -> f.width(FILL))`) and let its own `ALIGNMENT` place its
  content. There is deliberately no main-axis/justification property (the engine
  describes WHAT, never WHERE — [§1](#1-design-principles-dsl-flavored)).
- **Closed-variant ("token") styles are the same surface, not a wrapper.** Some
  styles select a whole native variant rather than supplying content:
  `ToggleStyle`/`PickerStyle`/`TextStyle` are wire **enums** applied as
  properties (`TOGGLE_STYLE`, …), and `LabelStyle` shapes which parts render.
  They are scoped/read exactly like the wrapper styles above.

Canonical signatures (SwiftUI-shaped) and the realization:

| Control | Style protocol | `Config` | Default style |
|---------|----------------|----------|---------------|
| `Button` | `ButtonStyle { makeBody(config) -> View }` | the `Button.Config` (title, action, content) | `PlainButtonStyle` (label) |
| `Label` | `LabelStyle { makeBody(config) -> View }` | the `Label.Config` (title, icon) | `DefaultLabelStyle` (title + icon) |
| `Video` | `VideoStyle { makeBody(config) -> View }` | the `VideoStyle.Configuration` (its `MediaController`) | `NativeVideoStyle` (no content) |

```java
// Button: the control wires the action; the style decorates the label/content.
enum MyButtonStyle implements ButtonStyle { INSTANCE;
    @Override public View makeBody(Button.Config c) {
        return c.content().modifiers(padding(p -> p.uniform(12)), background(b -> b.color(BLUE)));
    }
}
Button.make(b -> b.title("Save").action(this::save)).modifiers(MyButtonStyle.INSTANCE);

// Label: the style decides which parts render (nullable parts = absent).
enum BadgeLabelStyle implements LabelStyle { INSTANCE;
    @Override public View makeBody(Label.Config c) {
        return HStack.make().children(
                c.icon().modifiers(frame(f -> f.width(18).height(18))),
                c.title());
    }
}
```

The Java DSL ships `PlainButtonStyle`/`BorderedButtonStyle`,
`DefaultLabelStyle`/`TitleOnlyLabelStyle`/`IconOnlyLabelStyle`, and
`NativeVideoStyle`; application-authored styles implement the
same interfaces. **Delta:** SwiftUI's `LabelStyleConfiguration.title`/`.icon`
are always-present views; Pathland passes `null` for an absent part (a style
checks `hasTitle()`/`hasIcon()`). Because styles are modifiers, a style is
applied with `.modifiers(<Style>)`.

---

## 6. Authoring conventions

A conformant DSL follows these conventions:

1. **Names**: SwiftUI view/modifier names; per-language case (camelCase,
   snake_case). `foregroundStyle` not `foregroundColor`; `fontWeight`,
   `multilineTextAlignment`, `scaledToFit`, `allowsHitTesting` keep their
   SwiftUI spellings. A modifier's type is **never** suffixed `Mod`.
2. **Signature order**: canonical parameter order is preserved —
   `.border(color, width)`, `.frame(width, height, alignment)`,
   `.shadow(color, radius, x, y)`. Compound modifiers expand to one property
   per argument.
3. **Values vs modifiers**: structural/layout values and a control's bound value
   are configured through a view's `make(...)` (modifiers through lowercase
   functions); everything else is a chainable `modifiers(...)` value. The three
   operations may be chained in any order.
4. **Typed enums**: `HorizontalAlignment` (leading/center/trailing, for
   `VStack`), `VerticalAlignment` (top/center/bottom, for `HStack`), `Alignment`
   (2D — topLeading … bottomTrailing — for `ZStack`/grids and the `frame`
   modifier's content alignment); all **position-only, never stretching** (a
   child's `FILL` size kind stretches). Plus `TextAlignment`, `FontWeight`
   (100–900), `Truncation` (head/middle/tail),
   `TextCase` (none/uppercase/lowercase), `FontStyle` (normal/italic),
   `FontDesign` (default/serif/rounded/monospaced), `ContentMode` (fit/fill),
   `ControlSize` (small/regular/large), `ToggleStyle`
   (switch/checkbox/button), `PickerStyle` (menu/segmented/wheel/radiogroup),
   `DatePickerMode` (date/time/dateAndTime), `ShapeKind`
   (circle/rectangle/roundedRectangle/capsule/ellipse/path). Numeric codes
   come from MODIFIERS.md's appendix; the DSL resolves them.
5. **`Spacer`/`Color` as views**: `Spacer` is a flexible expanding filler;
   `Color` in a tree is a layout-greedy fill.
6. **The environment is the only inheritance mechanism.** Any value that must
   reach a whole subtree — a style (`ButtonStyle`), the router, the active path,
   the session's persisted state — is injected through the environment, never
   threaded through constructors and never through a second scoping system. The
   environment is a hierarchical, nearest-wins scope of typed `EnvironmentKey`
   values (the `EnvironmentBinding` modifier value / `Environment.value(key)`,
   Java; §4.5). A language MAY implement the scope with a thread-local over the
   synchronous render pass (Java does) — but that is the implementation of the
   one mechanism, not an additional one. A subtree-scoping modifier MUST be
   expressed as an `EnvironmentKey` binding: `ButtonStyle` binds
   `Environment.BUTTON_STYLE` (`EnvironmentKey<ButtonStyle>`), exactly like the
   router and the active path.
7. **Reactivity discipline**: `body()` is a tracked render; a signal read there
   subscribes the component and a later write re-renders that component, whose
   subtree is diffed into deltas. A bound node stays fine-grained (one delta, no
   body re-run). Never write signals during render. Non-signal reads are
   mount-time only ([§3.4](#34-structural-reactivity-reactive-bodies--slots)).
8. **Emission contract**: each DSL call emits exactly the documented
   properties with the documented value types; the emitter diffs. The DSL never
   computes layout, never positions, never serializes full trees.

---

## 7. Theme management

Pathland theming is **application-owned over a renderer-owned token system**
([TOKENS.md](./TOKENS.md); the wire encoding lives in [OPCODE.md](./OPCODE.md)
§Design Token System). The DSL gives the author two distinct surfaces:

1. **Token references in style modifiers** — semantic intent instead of a
   literal: `foregroundStyle(f -> f.color(Color.token("color.primary")))`
   emits a `DESIGN_TOKEN` reference the renderer resolves against the current
   scheme. No opcode is re-emitted when a token or the scheme changes.
2. **Global theme overrides** — a batch of `PARAMETER::SET_DESIGN_TOKEN` commands
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

| Canonical (SwiftUI-shaped) | Java DSL | Rust DSL | Emits |
|----------------------------|----------|----------|-------|
| `Color.token("color.primary")` | `Color.token("color.primary")` | `Color::token("color.primary")` | `DESIGN_TOKEN` ref (`0x08`) in any COLOR-typed property: `COLOR` 0x100A (`.foregroundStyle`), `BACKGROUND_COLOR` 0x1001, `BORDER_COLOR` 0x1004, `SHADOW_COLOR` 0x1021, `TINT` 0x1030, … |
| `Color.token("dark.color.surface")` | `Color.token("dark.color.surface")` | `Color::token("dark.color.surface")` | same — an explicit dark-variant reference (resolves the dark layer in either scheme) |
| `.padding(space.2)` / `Spacing(space.2)` | `Padding.with(Length.token("space.2"))` / `spacing(Length.token("space.2"))` | `Padding(Length::token("space.2"))` / `spacing(Length::token("space.2"))` | `DESIGN_TOKEN` ref in a F32-typed property: `PADDING` 0x1011 / `SPACING` 0x0001 / `CONTENT_MARGINS` 0x0005, … (see TOKENS.md §Generative token families) |
| `Font.body.family` token | `FontFamily.token("font.body.family")` | `FontFamily::token("font.body.family")` | `DESIGN_TOKEN` ref in a STRING-typed property (`FONT_FAMILY`) |

Token references are **static paths**: a signal-bound property carries a
literal value, not a token reference.

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
| `Theme()` — one-scheme override batch | `new Theme().color(...)...` | `Theme::new()` | one `PARAMETER::SET_DESIGN_TOKEN` per override (`A` = arena path, `B` = valueType, `C` = value) |
| `.color("color.primary", 0xFF2563EB)` | `.color("color.primary", 0xFF2563EB)` | `.color("color.primary", 0xFF2563EB)` | `COLOR`-typed override (sRGB `0xAARRGGBB`) |
| `.f32("space.base", 4.0)` | `.f32("space.base", 4.0f)` | `.f32("space.base", 4.0)` | `F32`-typed override (lengths in device pixels) |
| `.u32("…", v)` / `.u8("…", v)` | `.u32(path, int)` / `.u8(path, int)` | `.u32(path, v)` / `.u8(path, v)` | `U32` / `U8`-typed override |
| `.string("font.body.family", "Inter")` | `.string(path, String)` | `.string(path, "Inter")` | `STRING`-typed override (arena) |
| `AdaptiveTheme(light, dark)` | `new AdaptiveTheme(light, dark)` | `AdaptiveTheme::new(light, dark)` | light overrides as base tokens; dark overrides `dark.`-prefixed |

Emission contract:

- The theme is emitted **once at mount** as a frame of
  `PARAMETER::SET_DESIGN_TOKEN` opcodes before the tree. Java: `new Emitter(sink,
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

### 7.3 Runtime theming and scheme choice

A host MAY replace the theme after mount. Replacement is **reset + apply**: the
host emits `META::RESET_THEME` followed by the new theme's
`PARAMETER::SET_DESIGN_TOKEN` overrides in one frame. The renderer clears the
old overrides, re-resolves every token-referencing property, and re-applies the
new values; no node is re-emitted.

The host MAY also force the effective scheme with
`META::SET_COLOR_SCHEME`:

| Canonical mode | Emits | Behavior |
|----------------|-------|----------|
| `.platform` | `SET_COLOR_SCHEME` A=0 | follow the OS/browser light/dark signal (default) |
| `.light` | `SET_COLOR_SCHEME` A=1 | force the light design |
| `.dark` | `SET_COLOR_SCHEME` A=2 | force the dark design |

While forced, platform scheme changes are ignored. Returning to `.platform`
restores native detection. Both commands are renderer state — the emitter
re-resolves, never re-emitting the tree.

Java exposes this through `Emitter.replaceTheme(...)` / `resetTheme()` /
`setColorScheme(...)`; the server runtime's host-level
`PathlandSession.setTheme(...)` / `setColorScheme(...)` delegate to the same
emission path. Rust exposes `Engine::reset_theme` / `set_color_scheme` (and
`replace_theme` / `replace_adaptive_theme`).

---

## 8. Java DSL convergence (adopted)

The Java DSL (`com.pathland.view`) has the complete surface. The convergence
below has been **adopted**: construction through the three operations
(`with` / `modifiers` / `children`), and modifiers as `ViewModifier` values with
**no `Mod` suffix** and **no per-modifier sugar on `View`**. The canonical
contract is [§4](#4-view-surface) and [§5](#5-modifier-surface).

| Canonical | Java (adopted) | Optional / remaining |
|-----------|----------------|----------------------|
| `Text("…")` | `Text.make(t -> t.text("…"))` (String or `Signal<String>`) | — |
| `Button("…", action)` / `Button(action:label:)` | `Button.make(b -> b.title("…").action(action))` and/or `.children(label)` | — |
| `Toggle("label", isOn: writable)` | `Toggle.make(t -> t.label("…").isOn(writable))` | — |
| `Slider(value: in:)` | `Slider.make(s -> s.value(writable).min(..).max(..))` (binding in config) | — |
| `.border(color, width)` | `border(b -> b.color(..).width(..))` via `.modifiers(...)` | radius convenience on the same builder |
| `DatePicker(selection:)` | `DatePicker.make(d -> d.mode(..).selection(days))` | *optional* `Date`-shaped config encoding days + millis-of-day per `SET_DATE`/`DATE_CHANGED` |

Adopted conventions:

- **`with` / `modifiers` / `children` are the one construction path.** Every
  concrete view/control exposes these three operations; the former per-view
  construction `.of(...)` is gone (`View.of` is the inline reactive-subtree
  factory, not a view-construction shortcut).
  They are available **at creation or chained, in any order**
  ([§2](#2-canonical-signature-notation)).
- **`make(config)` is a fluent config builder.** The config holds the view's
  values and is the same type a style's `makeBody(Config)` reads
  ([§5.7](#57-styleable-controls-the-style-contract)).
- **The `View.*` static-factory surface is removed.** The entire factory block
  (`text`, `image`, `rectangle`, `spacer`, `vstack`, `hstack`, `zstack`,
  `button`, `textField`, `textEditor`, `toggle`, `slider`, `stepper`,
  `progressView`, `gauge`, `divider`, `grid`, `scrollView`, `lazyVStack`,
  `lazyHStack`, `lazyVGrid`, `lazyHGrid`, `picker`, `menu`, `colorPicker`,
  `datePicker`, `group`) is deleted from `View.java`.
- **`Group` is a first-class view.** `Group.children(View...)` is a
  transparent container view (SwiftUI `Group`).
- **Core and custom modifiers share one mechanism.** Core modifiers are
  `ViewModifier` values (`padding(p -> p.uniform(16))`,
  `foregroundStyle(f -> f.color(color))`) applied via `.modifiers(...)` —
  there is **no sugar on `View`**; several modifiers at once use
  `.modifiers(...)`, innermost-first. Single-value modifiers pass the value
  directly (`FontWeight.BOLD`).
- **No `Mod` suffix.** A modifier type is named for the modifier it applies;
  where it wraps a single value, the value type is the modifier. Main's
  `FrameMod → Frame` (with the wire batch renamed `emit.Frame → ProtocolFrame`)
  is the precedent; the remaining `*Mod` types are renamed accordingly
  (`FontWeightMod → FontWeight`, `ButtonStyleMod → ButtonStyle`, …;
  `EnvironmentMod → EnvironmentBinding`, `router/NavigationMod → NavigationIntent`).
- **Full `ShapeKind` coverage.** `Circle.make()`, `Capsule.make()`,
  `Ellipse.make()`, `RoundedRectangle.make(r -> r.cornerRadius(..))`, generic
  `Shape.make(s -> s.kind(ShapeKind))` (for `Path`).
- Implementation status: `lib/java/pathland-view/status.md`.

---

## 9. Generation contract for a new language

An AI (or a human) generating a Pathland DSL in a new language MUST produce the
following and verify each item. This is a checklist; the authoritative details
are the companion specs.

### 9.1 Required surface

- [ ] **Views/controls** — every entry of [§4](#4-view-surface):
  `Text`, `Image`, `Color`, `Shape` (all `ShapeKind`s), `Divider`, `Spacer`,
  `ProgressView`, `Gauge`; `VStack`, `HStack`, `ZStack`, `Grid`, `GridRow`,
  `ScrollView`,
  `LazyVGrid`, `LazyHGrid`, `LazyVStack`, `LazyHStack`; `Button`,
  `TextField`, `SecureField`, `TextEditor`, `Toggle`, `Slider`, `Stepper`,
  `Picker`, `Menu`, `DatePicker`, `ColorPicker`.
- [ ] **The three operations** — configure values, apply modifiers, supply
  children — available at creation or by chaining, in any order
  ([§2](#2-canonical-signature-notation)). Each language maps them to its idiom
  ([§9.3](#93-language-adaptation-rules)).
- [ ] **Modifiers** — every entry of [§5](#5-modifier-surface), applicable to
  any view, innermost-first, emitting one property per underlying argument; a
  modifier's type carries no `Mod` suffix.
- [ ] **Custom modifiers / unified mechanism** — a `ViewModifier` authoring
  surface (`body(content) -> View`) + a chainable `modifiers(...)` operation on
  **any** view; **core modifiers are implemented through the same mechanism**,
  never a separate syntax ([§5.6](#56-custom-modifiers-developer-authored)).
- [ ] **Shared config with styles** — a style's `makeBody(Config)` receives the
  same config type the control configured via `make(...)`
  ([§5.7](#57-styleable-controls-the-style-contract)).
- [ ] **Signals** — `signal`, `computed`, `effect`, `untracked`,
  `get`/`set`/`update`/`asReadonly`, with equality suppression, synchronous
  flush, glitch-free propagation, error caching, write discipline, circular
  detection ([§3.1](#31-signal-surface)).
- [ ] **Two-way bindings** — every value control takes a writable signal (a
  config value) and exposes a sink the host routes its event into
  ([§3.2](#32-two-way-binding)).
- [ ] **Persisted state** — `State<T>` keyed fields (or the language's
  equivalent wiring) against a platform-neutral store ([§3.3](#33-persisted-state-statet)).
- [ ] **Gestures** — `.onTapGesture` (composed from raw pointer down/up) and
  raw `pointerEvents`/`EVENT_LISTENERS` bitmask ([§4.4](#44-gestures)).
- [ ] **Structural reactivity (reactive bodies)** — a signal read in a
  component body (`if`/`switch`/`for`, or the language's equivalent) re-renders
  that component and reconciles it into `TREE` deltas with zero opcodes for
  identical structure; `View.of` is the inline supplier form
  ([§3.4](#34-structural-reactivity-reactive-bodies--slots)).
- [ ] **Navigation** — `Router` / `RouteTable` / `NavigationContainer` /
  `NavigationLink` over a plain `Signal<Route>`, the initial route host-seeded
  before mount, emitting `ROUTE` and `TRANSITION` and consuming the `NAVIGATE`
  event ([§4.5](#45-navigation)).
- [ ] **Design tokens & theming** — a token surface: token-typed values usable
  in style modifiers (a `Color` accepting a token path, e.g. `Color.token("color.primary")`
  or the language's equivalent), a theme/override helper emitting
  `PARAMETER::SET_DESIGN_TOKEN` (base values + `dark.`-prefixed dark values), and
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
   scheme. A theme/override helper emits `PARAMETER::SET_DESIGN_TOKEN`
   (`A` = arenaRef path, `B` = valueType, `C` = value); a `dark.`-prefixed path
   supplies the dark design (base = light — [TOKENS.md](./TOKENS.md)).
4. `WIDTH`/`HEIGHT` sentinels: `FILL` = −1.0, `HUG_CONTENT` = −2.0.
5. `Grid` counts map to the **config values** `GRID_COLUMNS` (`0x001E`) /
   `GRID_ROWS` (`0x001F`): a positive `int` count emits a positive F32; absent =
   auto-fit. A **`List<GridItem>`** value serializes to the **`GRID_TRACKS`**
   (`0x0020`, STRING) per-track spec, which takes precedence over the counts. A
   `WIDTH`/`HEIGHT` frame on a grid is the grid's **box**, never a count
   ([§4.2](#42-layout--container-nodes)).
6. `DatePicker` value/event use the two-field encoding **days since epoch
   (I32) + millis of day (U32)**; there is no `DATE_VALUE` property.
7. Value/text/date events are gated by the transport-aware event guards; the
   DSL's controls emit the `ACTION_ID`/`BINDING_ID`/`EVENT_LISTENERS` that
   declare intent.
8. Emission is **diff-based and reactive**: the DSL describes values, the
   emitter diffs. A generated DSL must keep the retained tree + emitter
   separation; it must not hand-serialize full trees.

### 9.3 Language adaptation rules

**The three operations are normative; their spellings are not.** A conformant
DSL MUST expose *configure values*, *apply modifiers*, and *supply children*,
each available at creation or by chaining (any order), mapped to the host
language's idioms. The canonical (SwiftUI-shaped) spelling is
`Text("x").padding(16)`; Java spells values `make(...)` (views) / lowercase
functions (modifiers) and chains `modifiers(...)` / `children(...)`.

| Aspect | Java | C# | Rust | Go | Python |
|--------|------|----|------|----|--------|
| configure values | `make(c -> …)` (views) / `padding(c -> …)` (modifiers) | `Make(c => …)` | `Type::new(config)` / `with(\|c\| …)` | ctor + options | kwargs / `configure(…)` |
| single-value shortcut | `make(value)` / `padding(value)` (sugar for the lambda) | `Make(value)` | `Type::new(value)` / `Type(sig)` | `Type(val)` | `Type(val=…)` / `Type(sig)` |
| apply modifiers | `.modifiers(…)` | `.Modifiers(…)` | `.modifiers(…)` (single or tuple) | `.Modifiers(…)` | `.modifiers(…)` |
| supply children | `.children(…)` | `.Children(…)` | `.children(vec![…])` / `vstack![…]` | ctor args / `.Children(…)` | `.children(*v)` |
| creation + chaining | static on the view; chain on the returned builder | static on the view; chain via **extension methods** | associated fn `Type::new`; chain via a **trait** + macros | package ctor; chain via methods | kwargs + methods |
| same-name static + chain | **builder split** (static on class, chain on builder) | extension methods (separate static class) | assoc fn + trait method | package func + method (not type-scoped) | **not expressible** (methods are last-wins) |
| `with` name legal | yes | yes (`With`; lowercase `with` is the record keyword) | yes | yes | **no** — `with` is a keyword → kwargs / `configure` |
| varargs children | yes | `params` | no — slices/arrays or macros | `...` | `*args` |
| value *is* the modifier | enum OK | **enum can't implement an interface** → `record struct` / static readonly value | enum OK | typed const + method OK | enum member with `apply` OK |

Rules:

- A language that cannot express "static-or-chained under one name" (Python)
  MUST still offer the three operations — value configuration via kwargs /
  `configure(...)`, modifiers and children via chainable methods.
- A language whose enums cannot implement the modifier interface (C#) MUST
  supply the equivalent single-value modifier form (a `record struct`, a static
  readonly value, or an implicit conversion).
- **Reactive values are config members, in every language.** A config member
  accepts a raw value or a signal ([§2](#2-canonical-signature-notation)). Rust
  realizes this with `impl Into<Reactive<T>>` on each builder setter
  (`Reactive<T> { Static(T), Signal(SignalId) }`); the former `Bound` /
  `*.bound` modifier surface is folded into it and **removed**.
- Names are recommendations; semantics
  ([§1](#1-design-principles-dsl-flavored)) are the contract.

### 9.4 Conformance recipe

A generated DSL is verified by proving it **emits the same bytes** as the
reference implementations:

1. Port the golden vectors in [CONFORMANCE.md](./CONFORMANCE.md) into the new
   DSL's test harness; assert byte-identical `TREE`/`PARAMETER`/`EVENT`/`META`
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

| SwiftUI | Canonical DSL | Java DSL | Rust DSL |
|---------|---------------|----------|----------|
| `Text("Hi")` | `Text("Hi")` | `Text.make(t -> t.text("Hi"))` | `text("Hi")` |
| `VStack(alignment: .center, spacing: 8) { … }` | `VStack(alignment:spacing:) { … }` | `VStack.make(v -> v.alignment(Alignment.CENTER).spacing(8)).children(…)` | `VStack::with(\|v\| { v.spacing(8.0); }).children(vec![…])` |
| `Button("+") { inc() }` | `Button("+", action)` | `Button.make(b -> b.title("+").action(() -> inc()))` | `button("+")` (no action yet) |
| `TextField("Name", text: $name)` | `TextField("Name", text: writable)` | `TextField.make(t -> t.placeholder("Name").text(name))` | `TextField::new` (no binding yet) |
| `Toggle("On", isOn: $on)` | `Toggle("On", isOn: writable)` | `Toggle.make(t -> t.label("On").isOn(binding))` | `Toggle(style)` (no binding yet) |
| `Slider(value: $v, in: 0...100)` | `Slider(value: writable, in: min...max)` | `Slider.make(s -> s.value(vSig).min(0).max(100))` | `Slider::new` (no binding yet) |
| `DatePicker("D", selection: $d)` | `DatePicker("D", selection: writable)` | `DatePicker.make(d -> d.mode(DatePickerMode.DATE).selection(days))` | `DatePicker` (bare) |
| `.foregroundStyle(.red)` | `.foregroundStyle(Color)` | `.modifiers(foregroundStyle(f -> f.color(Color)))` | `.modifiers(ForegroundStyle(Color::argb(0xFF0000FF)))` |
| `.background(.gray)` | `.background(Color)` | `.modifiers(background(b -> b.color(Color)))` | `.modifiers(Background(Color::argb(0xFFEEEEEE)))` |
| `.border(.blue, width: 2)` | `.border(Color, width: 2)` | `.modifiers(border(b -> b.color(Color).width(2)))` | `.modifiers(Border::new(Color::argb(…), 2.0))` |
| `.frame(width: 100, height: 24)` | `.frame(width:height:alignment:)` | `.modifiers(frame(f -> f.width(100).height(24)))` | `.modifiers(Frame::new(Some(100.0), Some(24.0), None))` |
| `.padding(16)` | `.padding(16)` | `.modifiers(padding(p -> p.uniform(16)))` | `.modifiers(Padding(16.0))` |
| `.font(.system(size: 28))` | `.font(size: 28)` | `.modifiers(Font.system(28))` | `.modifiers(FontSize(28.0))` |
| `.font(.largeTitle)` | `.font(Font.largeTitle())` | `.modifiers(Font.largeTitle())` | `.modifiers(Font::large_title())` |
| `.font(.custom("Georgia", size: 20))` | `.font(Font.custom("Georgia", 20))` | `.modifiers(Font.custom("Georgia", 20))` | `.modifiers(Font::custom("Georgia", 20.0))` |
| `.fontWeight(.bold)` | `.fontWeight(FontWeight)` | `.modifiers(FontWeight.BOLD)` | `.modifiers(FontWeight(700.0))` |
| `.shadow(color:radius:x:y:)` | `.shadow(color:radius:x:y:)` | `.modifiers(shadow(s -> s.color(Color).radius(r).x(x).y(y)))` | (not yet) |
| `.onTapGesture { go() }` | `.onTapGesture(action)` | `.modifiers(tapGesture(g -> g.action(() -> go())))` | `.modifiers(TapGesture::new(\|\| go()))` |
| `if showLogin { LoginView() } else { HomeView() }` | `if/else` in a result builder | `if (showLogin.get()) return new LoginView(); return new HomeView();` in `body()` (or `View.of(() -> …)` inline) | `if`/`match` in `build()` |
| `NavigationStack { … }` | `Router` + `NavigationContainer` | `NavigationContainer.make(n -> n.router(router))` | `NavigationContainer::with(\|n\| n.router(router))` |
| `NavigationLink("Users", value:)` | `NavigationLink("label", router, to)` | `NavigationLink.make(l -> l.label("Users").router(router).to("/users"))` | `navigation_link(...)` |
| `content.modifier(Card())` | `content.modifier(Card())` | `content.modifiers(card(c -> …))` | `.modifiers(Card)` |

The three operations (`make` / `modifiers` / `children`) are the one mechanism
for construction and modification alike
([§2](#2-canonical-signature-notation),
[§5.6](#56-custom-modifiers-developer-authored)); built-in and
application-authored modifiers share the same `modifiers(...)` surface.

**Status deltas captured by this table**: the Java DSL is surface-complete with
the three operations, modifiers as values via `.modifiers(...)` (no sugar on
`View`), and styles as modifiers; **every value member carries a raw + `Signal<T>`
form** (uniform signals-first configs, so any property — layout included — can
react); the Rust DSL now exposes the surface plus
**signals** — value, computed (lazy/memoized/equality-suppressed), and effect
signals in a shared `no_std` runtime (`Signal<T>`/`WritableSignal<T>`,
`Engine::signal`/`computed`/`effect`/`read`/`set`) with text/property binding —
**two-way control bindings** (input sinks collected with
`collect_input_handlers`) and **actions** (`Button::action`), plus
**styles + environment** (a subtree `Environment` threaded through
`View::build_env`, `ButtonStyle` applied with `ViewExt::button_style`),
**structural reactivity + navigation** (`RouteTable`,
`Router`, `NavigationContainer`, `NavigationLink`), and **persisted state**
(a `StateStore` trait + `PersistentState::state` auto-load/auto-save). Both are
per-project implementation status tracked in each project's `status.md`.

---

## Conformance

- The wire footprint of every DSL element above is defined by the golden
  vectors in [CONFORMANCE.md](./CONFORMANCE.md). A new DSL element MUST add
  vectors before any implementation depends on it.
- Component ids: [PRIMITIVES.md](./PRIMITIVES.md); property ids and value
  types: [MODIFIERS.md](./MODIFIERS.md); event ids and listener bits:
  [EVENTS.md](./EVENTS.md). These specs are the single source of truth.
- The v3 authoring surface (`make` + lowercase modifier functions,
  config-with-styles, no `Mod` suffix, single-value shortcuts) is a
  **source-surface** change only: it emits the **same bytes** as the prior
  v2 `.of(...)` / `with(...)` surface for an equivalent tree — no
  component/property/event/opcode change.
