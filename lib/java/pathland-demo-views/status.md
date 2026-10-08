# pathland-demo-views — implementation status

**Last updated:** September 28, 2026

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
- **Real asset refs**: the demo's non-icon assets are served from the reserved
  asset mount (`/_pathland/assets/…`) and referenced **absolute** (fixing the
  relative-path bug on deep routes). The old hand-made
  `icons/home|kitchen|settings|save|cloud|status.svg` assets were **removed** —
  the sidebar, player controls, and label section now use the semantic
  **`Icon`** primitive (`Icon.of(IconName.…)`, spec/ICONS.md) that every
  renderer maps to its native symbol set.
- **`MediaSection`** (kitchensink): a `Video` + `Audio` node with remote sample
  URLs — playback interaction is renderer-native (`controls`); the app supplies
  only the source + size.
- **`MusicPlayerView`** (com.pathland.demo.music) — the **web demos' root** (an
  Apple Music-style player): a track **`LibraryView`** (left) + the
  **`NowPlayingSidebar`** (right) in a fill HStack, over the bottom
  **`PlayerBar`**. The main row is a **`SizeThatFits`** fit slot: the
  now-playing sidebar transmits only when its parent row is **≥ 600pt** wide
  (`Fit.of(rowWithSidebar, 600f)` / `Fit.of(mainArea)` — the `FIT_CHANGED`
  report from the DOM client swaps the row; the shared `mainArea` nodes are
  re-used by the reconcile). Owns the player's `State` (persisted per-session under
  `player.*` keys) and hands the bound signals to its subviews; each subview is
  its own `View` class. **Real playback**: the bottom bar is an app-driven
  `Audio` node whose custom **`PlayerControlsStyle`** (`AudioStyle`) renders the
  whole bar — a centered row of `[⏮] [play-pause] [⏭] | [cover art] [track
  title] | [volume]` over a slim, draggable **seek bar showing progress as a
  percent** (`PercentSeek` maps seconds↔percent in the UI model; the wire keeps
  seconds) — bound to the media configuration's signals; the hidden media
  element plays the current track and reports time/ended/volume back into the
  player state (spec/EVENTS.md Media) — `MEDIA_ENDED` auto-advances. Track
  selection / prev / next change app-owned state (position resets to 0). The
  seek + volume sliders **decouple the seek/volume command from the report**
  (`SeekControl`/`VolumeControl`: display from the report signals, command
  bound to `MEDIA_POSITION`/`MEDIA_VOLUME` written only on a user drag) so a
  `MEDIA_TIME_UPDATED`/`MEDIA_VOLUME_CHANGED` report never echoes back as a
  seek/volume command (spec/EVENTS.md Media — the per-second echo caused seek
  jitter). The seek bar is **seek-on-release**: it declares `Slider.onEditingChanged`
  (the `EDITING` listener bit) and `SeekControl` buffers the drag and commits the
  seek command once when the drag ends — one `MEDIA_POSITION` per drag, identical
  on web and GTK (both renderers send the `EDITING_CHANGED` boundaries). The 8
  tracks are **Pathland concept songs** — one per album, genre-matched invented
  artists (The Foundation, The Crossings, Rena Render, Ember Frame, Rowan
  Frame, Neon Protocol, Open Land, The Byte Ensemble) — sourced from bundled
  `.mp4` covers (the extracted
  frame at ~1s is `coverN.jpg`) and re-encoded **mono-64k** `trackN.mp3` with
  durations matching the real files. Catalog (`Track` record, 8 albums)
  with album art + audio served from
  `/_pathland/assets/albumart/*.jpg` and `/_pathland/assets/audio/*.mp3` in both
  SSR demos. **Synced karaoke subtitles**: a `Lyrics` model parses each track's
  embedded `lyrics/{Title_With_Underscores}.srt` (rolling-window cues) and a
  computed signal resolves the **current karaoke block** at the play position; the
  pill is **always two italic lines** — the line being sung (the one before the
  cue's new line) in bright white on top, the next line dimmed below, on ~50%
  translucent black, just above the floating player bar — shown only while
  playing (`MusicPlayerView`). The covers are downscaled to **440×440** JPEG (2× the largest
  rendered 220×220 CSS box, `NowPlayingSidebar`; ~190 KB total vs the original
  1024×1024 ~960 KB) — JPEG (not WebP) because gdk-pixbuf has no WebP loader, so
  the GTK desktop host decodes them unchanged. Mounted by `QuarkusDemoApp` / `SpringDemoApplication` as
  `newRoot()` and by the **GTK desktop host** (`GtkHost` mounts it directly,
  unchanged); `SplitNavDemo` remains the nav showcase for the Qt host.

## Not implemented / gaps

- The browser experience depends on the `@pathland/dom-renderer` client
  (`lib/typescript`, built to `dist/pathland-dom-renderer.js` and copied into
  each demo's `src/main/resources` under a content-hashed name plus the
  `dom-renderer.current` pointer manifest).

## Verified by

`mvn test -pl pathland-demo-views` (JDK 17+) — `CounterViewTest`,
`KitchenSinkViewTest` (mount + persistence + input routing),
`SplitNavDemoTest` (sidebar + content first frame, `/kitchen` content swap,
menu-click content swap deltas, reactive active-row highlight), and
`MusicPlayerViewTest` (library/art/buttons/sliders render; the app-driven audio
node binds source + media control props; taps persist the player state; the
media events drive position/volume/ended). Both SSR demos are verified by
running them and curling the routes (`/` renders the music player's
`<img>`s + range sliders + transport buttons + the `.pathland-media` audio
wrapper; the albumart + audio assets serve from `/_pathland/assets/`).
- **Single embedded asset copy** (`com.pathland.demo.assets.DemoAssets`): the
  media + icon assets live once in this jar under
  `META-INF/resources/_pathland/assets/`; `DemoAssets.extractToTemp()` unpacks
  them to a temp dir (with `Range`/caching headers) that every host serves from —
  the web demos mount their framework static handler on `<root>/assets/…`, the
  GTK host sets it as the asset root. `DemoAssetsTest` guards all 24 assets.