# pathland-demo-views — implementation status

**Last updated:** November 3, 2026

The remaining framework-agnostic **Java** demo views (`dev.pathland.demo`), consumed
by the Quarkus and Spring Boot demos. Uses `State` fields wired by the
`pathland-view-processor` annotation processor.

The music player moved to `pathland-demo-views-kotlin`; the shared demo theme and
assets moved to the DSL-neutral `pathland-demo-common` module.

## Implemented

- **`KitchenSinkView`** — a scrollable column of every protocol section.
- **Sections** — `SectionCard`, `CounterSection`, `TextFieldSection`,
  `ToggleSection`, `ValueControlsSection`, `PickerSection`, `ColorSection`,
  `DateSection`, `LayoutSection`, `TextStylesSection`, `AppearanceSection`, and
  `ThemeSection`.
- **Legacy views** — `CounterView`, `CounterControls`, `NameField`.
- **`SplitNavDemo`** — sidebar + detail navigation with `NavigationContainer`,
  route-driven window metadata, reactive active-row highlighting, and a fallback.
- **`AdView`** — the small sponsored-proxy origin app.
- **Semantic accessibility** — `MAIN`/`NAVIGATION`/`HEADER` roles; sidebar rows
  are real `Button`s; controls carry accessible labels.
- **Real asset refs** — icons use the semantic `Icon` primitive; non-icon media
  assets live in `pathland-demo-common`.
- **`DemoTheme` and `DemoAssets`** — now provided by `pathland-demo-common`.

## Not implemented / gaps

- The browser experience depends on the `@pathland/dom-renderer` client.
- These Java views remain a pre-1.0 porting target for Kotlin (#169).

## Verified by

`mvn test -pl pathland-demo-views` (JDK 17+) — `CounterViewTest`,
`KitchenSinkViewTest`, and `SplitNavDemoTest`.
