# pathland-demo-views — implementation status

**Last updated:** September 3, 2026

The framework-agnostic shared demo views (`com.pathland.demo`), consumed by the
Quarkus and Spring Boot demos. Uses `State` fields wired by the
`pathland-view-processor` annotation processor.

## Implemented

- **`KitchenSinkView`** (the demo root): a scrollable column of every protocol
  section, mounted by both demos' `SessionApp`.
- **Sections** (each a custom `View`):
  - `SectionCard` — reusable titled/bordered/rounded section wrapper.
  - `CounterSection` — `State<Integer>` + `Button` + `Stepper`.
  - `TextFieldSection` — `TextField` + `TextEditor` on one `State<String>`.
  - `ToggleSection` — `Toggle` in all three `ToggleStyle`s.
  - `ValueControlsSection` — `Slider`/`Stepper`/`Gauge`/`ProgressView` on one
    `State<Float>`.
  - `PickerSection` — `Picker` (Segmented + Menu) + `Menu`.
  - `ColorSection` — `ColorPicker` + `Color` as a View + reactive `Rectangle`.
  - `DateSection` — `DatePicker` bound to days-since-epoch.
  - `LayoutSection` — `Grid`/`LazyVStack`/`HStack`+`Spacer`/`ZStack`/`Divider`.
  - `TextStylesSection` — text-formatting modifiers.
  - `AppearanceSection` — border/shadow/opacity/transform/hidden/z-index.
  - `ThemeSection` — **design-token demo**: `Color.token("color.primary")`,
    `Color.token("control.accent")`, `Color.token("color.surface")` /
    `Color.token("dark.color.surface")` and a `control.background`-referenced
    fill, resolving against the active scheme.
- **`DemoTheme`** — the demo's global `SET_DESIGN_TOKEN` theme (base + `dark.*`:
  `color.primary`, `color.surface`, `color.accent`, `space.base`,
  `font.body.family`, `control.*`). Both demos' `SessionApp` pass it to the
  `Emitter`, which rides the overrides into the mount (SSR) + resync frames so
  the HTML/JS client re-themes under `prefers-color-scheme: dark`.
- **Legacy views kept**: `CounterView`, `CounterControls`, `NameField` (still
  covered by `CounterViewTest`).
- **`SplitNavDemo`** — the demo **root** (spec PRIMITIVES.md:
  `NavigationSplitView` → `HStack` sidebar + detail): a fixed menu column on
  the left (3 items: Home / Kitchen sink / Settings) and a
  `NavigationContainer` content area on the right that swaps on selection
  (`router.navigate` — direct selection, no back-stack growth). Mounted directly
  by both demos' `SessionApp` with the active platform path provided as an
  environment value — **`new SplitNavDemo().environment(Platform.ACTIVE_PATH,
  activePath)`**; `SplitNavDemo.body()` reads it and builds a **bound** router
  (`Navigation.navigator()...build(activePath)`) — external path changes are
  guard-processed and navigation is mirrored back into the signal. The view is
  router-free (no app factory / `NavigationApp`).
  `SessionApp` seeds `activePath` from the applied
  `META::ENVIRONMENT` `ROUTE` field (a request URL on SSR, the DOM client's
  first message on the WebSocket), so deep links render correctly on the first
  frame; env re-routing sets `activePath`, `NAVIGATE`-with-URL sets `activePath`,
  and `NAVIGATE`-back forwards into `RenderResult.navigateHandler`. The view also
  demonstrates **`onPathChange`** (logging the active path). The
  active menu row is highlighted **reactively** via `Navigation.isActive(router,
  path)` → a computed signal that drives `Background.of(Signal<Color>)` /
  `ForegroundStyle.of(Signal<Color>)`, so a selection re-emits only that row's
  color properties. Content areas are router-free, self-contained views
  instantiated inline in the route table:
  - `HomeView` — titled welcome pane with a declarative `.navigate("/kitchen")`
    button (spec DSL.md §4.5 — a component inside the container changing the
    route without a router) (`/` and `/home`).
  - `KitchenSinkView` — the full showcase (`/kitchen`).
  - `SettingsView` — a switch + volume slider on local signals (`/settings`).
  - A `fallback` ("Not Found") for any other path.

- **Semantic accessibility roles** (`AccessibilityRole.of(Roles.*)`,
  `com.pathland.view.Roles`): the demo root views carry the `MAIN` landmark
  (`HomeView`/`SettingsView`/`KitchenSinkView`/`CounterView`), the sidebar is
  `NAVIGATION`, and `SectionCard` titles are `HEADER` headings — the web
  renderer reflects these as native semantic elements (`<nav>`, `<main>`,
  `<h2>`). Sidebar menu rows are **real `Button`s styled with
  `PlainButtonStyle`** (`Button.of(Label, action)`) — control semantics are
  intrinsic to the component, not a `ROLE`; a custom-looking button is a
  `Button` + `ButtonStyle`.
- **Real asset refs**: the demo icons are now actual SVGs served from the
  reserved asset mount (`/_pathland/assets/icons/*.svg`) and referenced
  **absolute** (fixing the relative-path bug on deep routes).
- **`MediaSection`** (kitchensink): a `Video` + `Audio` node with remote sample
  URLs — playback interaction is renderer-native (`controls`); the app supplies
  only the source + size.

## Not implemented / gaps

- The browser experience depends on the `@pathland/dom-renderer` client
  (`lib/typescript`, built to `dist/pathland-dom-renderer.js` and copied into
  each demo's `src/main/resources`).

## Verified by

`mvn test -pl pathland-demo-views` (JDK 17+) — `CounterViewTest`,
`KitchenSinkViewTest` (mount + persistence + input routing), and
`SplitNavDemoTest` (sidebar + content first frame, `/kitchen` content swap,
menu-click content swap deltas, reactive active-row highlight). Both SSR demos
are verified by running them and curling the deep links (`/`, `/home`,
`/kitchen`, `/settings` render the splitview seeded at that path with the right
content + `data-pathland-route`; unknown paths show the sidebar + "Not Found";
the JS bundle serves correctly).