# pathland-view (Java) — implementation status

**Last updated:** September 14, 2026

The hand-written, framework-agnostic Java 17+ DSL (`com.pathland.view`):
SwiftUI-style views, Angular-style signals, fine-grained emitter, `PLPL` wire
codec, lazy JNA ring interop, and cross-platform `State`. Protocol contract:
`spec/`. Runs on every LTS from Java 17.

## Implemented

- **Construction is `.of()` only**: every concrete view/control exposes a
  static `<ViewName>.of(...)` factory (`Text.of`, `VStack.of`,
  `Button.of(label, action)`, `Slider.of(binding, min, max)`,
  `DatePicker.of(mode, days)`, …); constructors are private and the former
  `View.*` static-factory surface is **removed**. `Slider`/`Stepper` are
  binding-first (initial value read from the signal); `Toggle` also offers the
  SwiftUI-closer `of(label, isOn)` overload; `Color.of(int)` alias.
- **One modifier mechanism — no sugar on `View`**: core modifiers are
  `ViewModifier` values (`Padding.of(16)`, `ForegroundStyle.of(color)`,
  `Border.of(color, width)`, `FrameMod.of(w, h, align)`, …) applied via
  `.modifier(...)` (single) or `.modifiers(...)` (several, innermost-first);
  `FrameMod.of(w)` / `of(w, h)` leave `ALIGNMENT` unset (alignment optional, so a
  stack's own cross-axis alignment is preserved);
  the `View` interface has no per-modifier factory methods. Built-in and
  application-authored modifiers share the exact same surface (spec `DSL.md`
  §5.6); `buttonStyle` is the `ButtonStyleMod` value (an **environment**
  binding — it scopes `Environment.BUTTON_STYLE`, an `EnvironmentKey<ButtonStyle>`,
  down the subtree). Reactive overloads
  (`ForegroundStyle.of(Signal)`, `Background.of(Signal)`, `FontSize.of(Signal)`)
  re-emit only the bound node. Enum-collision modifier names use the `Mod`
  suffix (`FontWeightMod`, `TextAlignmentMod`, `TruncationMod`, `TextCaseMod`,
  `FontStyleMod`, `FontDesignMod`, `ControlSizeMod`); `FrameMod` avoids
  `emit.Frame`.
- **Views**: `View` (open interface), `VStack`, `HStack`, `ZStack`, `Group`
  (transparent container, maps to a `VSTACK` node today), `Text`, `Image`,
  `Color` (dual identity: a `COLOR` view *and* a value passed into style
  modifiers), shapes (`Rectangle`, `Circle`, `Capsule`, `Ellipse`,
  `RoundedRectangle`, generic `Shape.of(ShapeKind)` — all `SHAPE` nodes),
  `Button`, `TextField`, `Spacer`, `TextEditor`, `Toggle`, `Slider`, `Stepper`,
  `ProgressView`, `Gauge`, `Divider`, `Grid`, `ScrollView`, `LazyVStack`,
  `LazyHStack`, `LazyVGrid`, `LazyHGrid`, `Picker`, `Menu`, `ColorPicker`,
  `DatePicker`; `body()` composition; `ButtonStyle`/`ViewModifier`/`Environment`
  (the environment is the **only** inheritance mechanism, Java 17+ — a
  hierarchical `EnvironmentKey`/`EnvironmentValues` scope over the synchronous
  render pass; no modifier scopes values by any other route).
- **Component IDs**: synced to the spec's grouped ranges (`Components.java`:
  `TEXT 0x01`, `IMAGE 0x02`, `COLOR 0x03`, `SHAPE 0x04`, `DIVIDER 0x05`,
  `SPACER 0x06`, `PROGRESS_VIEW 0x07`, `GAUGE 0x08`, `VSTACK 0x10`,
  `HSTACK 0x11`, `ZSTACK 0x12`, `GRID 0x13`, `SCROLLVIEW 0x14`, `LAZY_*`,
  `BUTTON 0x20`, `TEXT_FIELD 0x21`, `TEXT_EDITOR 0x22`, `TOGGLE 0x24`,
  `SLIDER 0x25`, `STEPPER 0x26`, `DATE_PICKER 0x27`, `PICKER 0x28`,
  `MENU 0x29`, `COLOR_PICKER 0x2A`, `COMMENT 0x7F`, …) — frames are spec-valid
  and cross-language correct.
- **Value controls**: `Toggle`/`Slider`/`Stepper`/`Picker`/`ColorPicker`/
  `Menu` bind to writable signals and route `VALUE_CHANGED` through the
  emitter's value-input registry; `TextEditor` mirrors `TextField`.
  `DatePicker` emits `STYLE::SET_DATE` (and re-emits it on signal change via a
  node-level date binding) and routes `DATE_CHANGED` through a dedicated
  date-input registry (`RenderResult.dateInputs`).
- **Events**: the full catalog round-trips — pointer, `KEY_*`, `VALUE_CHANGED`,
  `TEXT_CHANGED`, `FOCUS_CHANGED`, `EDITING_CHANGED`, `SUBMIT`, `SCROLL`,
  `WHEEL`, `DATE_CHANGED`; listener bits `0..9` declared
  (`Commands.Listeners`). `FrameCodec.decodeEvents` decodes all of them.
- **Resync**: `Commands.Meta.RESYNC`, `FrameCodec.encodeResync`/`isResync`
  (host → guest `META::RESYNC` request), and `Emitter.renderFull()` (re-emit the
  complete retained tree as one snapshot frame — no re-mount).
- **Modifiers** (modifier values applied via `.modifier(...)`/`.modifiers(...)`,
  chainable on any view): `Padding`, `ForegroundStyle(Color)` (no `.color()`),
  `Background(Color)`, `Tint`, `Opacity`, `FontSize`, `FontWeightMod`, `Border`
  (`Border.of(color, width)` canonical + `(color, width, radius)` convenience),
  `Visible`/`Hidden`, `FrameMod` (fixed and min/ideal/max), `Offset`, `Position`,
  `FixedSize`, `LayoutPriority`, `ZIndex`, `AspectRatio`/`ScaledToFit`/
  `ScaledToFill`, `MinimumScaleFactor`, `FontStyleMod`/`Italic`/`FontDesignMod`/
  `FontWidth`/`Kerning`/`Tracking`/`BaselineOffset`/`LineSpacing`/`LineLimit`/
  `TextAlignmentMod`/`TruncationMod`/`TextCaseMod`/`Underline`/`Strikethrough`/
  `FontFamily`, `CornerRadius`, `Shadow`, `Blur`, `Saturation`/`Contrast`/
  `Brightness`/`Grayscale`/`HueRotation`/`ColorMultiply`/`ColorInvert`,
  `Clipped`/`ClipShape`, `Rotation`, `ScaleEffect`, `Disabled`,
  `AllowsHitTesting`, `ControlSizeMod`,
  `AccessibilityLabel`/`AccessibilityRole`/`AccessibilityState`, `ImageSource`,
  `PointerEvents` (OR-in), `ActionId`/`BindingId`, `TapGesture` (onTapGesture),
  `ButtonStyleMod` — `Color` is never a modifier.
- **Value types**: `ValueTypes.forProperty` mirrors `value_type_for` —
  `COLOR` family → `COLOR`; `VISIBLE`/`ENABLED`/`CLIPS_TO_BOUNDS`/`UNDERLINE`/
  `STRIKETHROUGH`/`COLOR_INVERT`/`ALLOWS_HIT_TESTING`/`IS_SECURE`/
  `IS_INDETERMINATE`/`FIXED_SIZE_*`/`SELECTED` → `U8`; `LINE_LIMIT`/`SELECTION`/
  `ACTION_ID`/`BINDING_ID`/`EVENT_LISTENERS`/`BORDER_EDGES` → `U32`;
  `LABEL`/`PROMPT`/`FONT_FAMILY`/`IMAGE_SOURCE` → `STRING`; enum-valued
  properties ride as `F32` holding the numeric enum code. `FontWeight` uses the
  spec's numeric 100–900 scale.
- **Signals** (`com.pathland.view.signal`): `signal`/`computed`/`effect`/
  `untracked` — glitch-free synchronous flush, equality suppression,
  circular-dependency detection.
- **Emitter** (`emit`): mounts once, stable ids, node-level binding effects
  (a signal change re-emits only that node), `FrameOpcodeSink` /
  `RingOpcodeSink`.
- **Structural reactivity** (spec DSL.md §3.4): `Conditional.when(Signal<Boolean>,
  then, else)` and `when(Signal<T>, Case.of(...)...) / Case.otherwise(...)` —
  statically importable lowercase factories on a final class (mirrors
  `Signals`; `if`/`switch`/`case`/`else` are Java keywords). A structural
  container materializes as a `Group` slot (bare `VSTACK` node); the emitter's
  structural effect reconciles the slot's single child on selector change —
  position+type-stable ids, `TREE` deltas only, identical recompute emits
  **zero** opcodes, nested containers work, and the slot's subtree owns its own
  bindings. `RenderResult` routing maps are now live unmodifiable views (a swap
  updates tap/text/value/date routing after mount).
- **Router** (`com.pathland.view.router`, spec DSL.md §4.5): `Route` (absolute
  path, `pathOnly()` strips query/fragment), `RouteTable` (literal + `:param`
  patterns, guards → `replace()` redirect, `fallback` 404), `Router`
  (`Signal<Route>` + back-stack; `navigate`/`push`/`pop`/`replace`/`back`,
  `handlePlatformNavigation`/`handleEvent`), **`Navigation`** (the ergonomic
  facade: `Navigation.navigator(initialPath).route(...).fallback(...).build()`
  collapses table + router + seeding, `Navigation.of(router)` is the container,
  `Navigation.isActive(router, path)` → a reactive `Signal<Boolean>` for
  active-route styling, `Navigation.ROUTER` = the `EnvironmentKey<Router>`),
  **`Params`** (typed path-param access — `get`/
  `intValue`/`longValue`/`doubleValue`/`booleanValue`/`path` — replacing the raw
  `Map` in `RouteHandler`), **a bound router** (`Navigation.navigator()...
  .build(WritableSignal<String>)` — the router binds to the host-provided
  `Platform.ACTIVE_PATH` signal: external writes are re-routed **guard-aware**,
  its own navigation is mirrored back, the initial value is guard-processed), and
  **generic scoped environment values**
  (`EnvironmentKey`/`EnvironmentValues` + `View.environment(key, value)` +
  `Environment.value(key)` — SwiftUI `.environment` style, hierarchical nearest-
  wins; a `NavigationContainer` scopes `Navigation.ROUTER` to its destination
  subtree and structural slots re-apply the incoming scope on re-render via
  `PathlandNode.environmentForChildren`/`Emitter.reconcileSlot`; **reads always
  return a signal** — `Environment.value(key)` → `Signal<T>`, a value injected as
  a `Signal` comes back as the same instance (bindable for reactive updates) and a
  plain value is wrapped in a constant signal; a read made before the key is bound
  returns a **lazy signal** that captures the binding on the first `.get()` during
  render, enabling the SwiftUI `@Environment` **field style**
  (`private final Signal<Router> router = Environment.value(Navigation.ROUTER);`);
  inject reactive values with `view.environment(key, signal)`), plus the
  **universal active platform path** (`Platform.ACTIVE_PATH` =
  `EnvironmentKey<String>`, the host injects a writable signal — provided for every
  app with or without navigation — read via `Environment.value` and observed via
  `View.onPathChange(Consumer)` / reading the signal; fires on every change incl.
  the initial value),
  `NavigationContainer` (structural
  slot emitting the `ROUTE` property coalesced into the same frame as the
  destination swap, plus a `NAV_DEPTH` U32 back-stack-depth property
  (`router.depth()` = stack size + 1; `push`+1 / `pop`−1 / `replace` unchanged)
  so native navigation adapters reconcile their page stack by depth, a
  `NAV_CHROME` chrome-mode property (`Chrome.PLATFORM_DEFAULT` / `Chrome.CUSTOM`
  — the renderer supplies default navigation chrome, or the developer owns all
  nav UI), and a `TRANSITION` PlatformDefault hint renderers may use
  to animate the swap), `NavigationLink` (a `BUTTON` that pushes — both
  explicit-router and **router-agnostic** overloads). **Any component can change
  the route** (spec DSL.md §4.5): `Button.of(...).navigate/push/replace(path)`
  (`NavigationMod`) and router-agnostic `NavigationLink.of(label, to)` record a
  `NavOp` intent on the node; the emitter resolves it to the **nearest
  enclosing** `Router` (the `NavigationContainer` the component lives under)
  during the retained-tree walk and exposes it on
  `RenderResult.navigateActions` (a live `node id → Runnable` map like
  `tapActions`), which the host routes on tap before `tapActions`. The route is a
  **plain signal** (not persisted; the URL is the web's persistence layer) and
  the host **seeds it before mount** via `navigate(...)` — no `Location`, no
  environment-carried route. A `NAVIGATE` event (`Event.navigate(url)` /
  `Event.navigateBack()`, wire round-trip via `FrameCodec`) routes into the
  router through `RenderResult.navigateHandler`.
- **Wire codec** (`transport`): `FrameCodec` — self-contained `PLPL` frames
  both directions, `encodeEvents`/`decodeEvents` (host→guest events with
  `TEXT_CHANGED` string-section offsets), and **`META::ENVIRONMENT`**
  (`isEnvironment`/`decodeEnvironment` → `EnvironmentData` — the platform
  environment: viewport + initial route; SSR synthesizes it from the request,
  the DOM client sends/enriches it over the WebSocket).
- **JNA ring interop** (`ffm`): lazy `libpathland_core` binding, zero JNI;
  `PathlandCore.ringMut(handle)` borrows the underlying ring for the in-process
  GTK renderer (`pathland_gtk_run_ring`), and `RingOpcodeSink` is the zero-copy
  guest writer.
- **Input dispatch** (`emit.InputDispatcher`): routes raw `EVENT`s into the
  `RenderResult` registries (tap / navigate / text / value / date, `NAVIGATE`
  URL → active path, back → router). Shared by the server session
  (`PathlandSession.dispatch`) and the native GTK host, so both route events
  identically and neither reaches into view internals.
- **`BINDING_ID` on the ring path**: for a `RingOpcodeSink`, the emitter sets
  `BINDING_ID` (= the node's id) on every text/value/date-bound control so the
  GTK renderer's transport-aware event guard fires (`TEXT_CHANGED` /
  `VALUE_CHANGED`). The frame (SSR) path never emits it, keeping server output
  unchanged.
- **State** (`state`): `StateStore`/`PersistentState`/`State`, auto-wired by
  `pathland-view-processor`.
- **Design tokens**: `Color` is `(argb, token)` — `Color.token("color.primary")`
  (incl. `dark.*` paths) usable in every color-taking modifier
  (`ForegroundStyle`/`Background`/`Border`/`Tint`, and the `Color` view). The
  emitter sends token refs as the `DESIGN_TOKEN` value type (path in the frame's
  string section / arena) in both `FrameOpcodeSink` and `RingOpcodeSink`
  (spec/TOKENS.md).
- **`Theme` / `AdaptiveTheme` (global overrides)**: `Theme` — single-value
  builders (`color`/`f32`/`u32`/`u8`/`string`) for one scheme; `AdaptiveTheme(light,
  dark)` composes a light + dark pair. Both implement the `ThemeData` interface
  (`emit`/`emitInto`); `Theme.emit`/`AdaptiveTheme.emit` write one frame of
  `STYLE::SET_DESIGN_TOKEN` overrides (the dark theme's with the `dark.` prefix),
  and `OpcodeSink.setDesignToken` puts path + STRING values into the string
  section (frame) / arena (ring). The `Emitter` takes a `ThemeData` and rides it
  into the mount + resync frames. Overrides are renderer-global — they never
  re-emit nodes.

## Not implemented / gaps

- `ACTION_ID`/`BINDING_ID` are usable as modifiers (`actionId`/`bindingId`); the
  Java model routes events by node id through the emitter's registries
  (`RenderResult`), so the frame (SSR) path does not set them automatically.
  On the ring (desktop) path the emitter auto-sets `BINDING_ID` for bound
  controls (see Implemented) — the GTK renderer gates value/text events on it.
- Structural reconcile does not emit `SET_PROPERTY` for a *removed* property on
  a matched node (the wire has no unset; the renderer keeps the last value) —
  consistent with the Rust engine.

## Verified by

`mvn test` (JDK 17+) — emitter, codec round-trips (incl. `NAVIGATE`), signals,
state, structural-reactivity (`ConditionalTest`) and the router (`RouterTest`:
initial route + `ROUTE` property + `NAV_DEPTH` depth tracking + `NAV_CHROME`
chrome mode, destination swap deltas, back-stack, params, guard redirect,
fallback, `NAVIGATE` routing, `NavigationLink`, the **declarative nav
intents** (`.navigate/.push/.replace` + router-agnostic `NavigationLink`
resolving to the nearest enclosing router via `navigateActions`), the
**`Navigation` facade / `Params` / `isActive`**, the **scoped environment
values** (`View.environment` + `Environment.value` hierarchy/restore), and the
**universal active path** (a bound router re-routing guard-aware on external
signal writes + mirroring its own navigation; `onPathChange` firing on changes));
the JNA ring
test runs when `libpathland_core` is on `java.library.path`. CI proves every
LTS from 17 (Temurin 17/21/25).