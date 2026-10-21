# pathland-render-qt — todo / remaining work

Post-merge status (October 2026): `main` is merged into the branch and the
renderer maps the new protocol surface (icons, media, proxy views, fit slots,
grid rows). Remaining work is tracked here and in `status.md`.

## Done

- **`main` merged** (`PARAMETER` rename; the shared `pathland-render-core`
  absorbs the merged decode). Core/render-core/Qt tests green; wasm guard green.
- **New component mappings** (`src/qt/qt_layer.cpp`):
  - `ICON` 0x0B → glyph `Text` from the canonical `ICON_NAME` map.
  - `MEDIA_CONTROLLER` 0x30 → non-visual (layout-transparent) `MediaPlayer` +
    `AudioOutput`.
  - `VIDEO` 0x0A → `VideoOutput`, bound via `MEDIA_CONTROLLER_ID`.
  - `PROXY_VIEW` 0x1E → transparent vertical container.
  - `SIZE_THAT_FITS` 0x17 → greedy two-axis fill slot.
  - `GRID_ROW` 0x1D → row container whose cells flatten into the Grid.
- **Visual styling layer** (demo parity with web/GTK):
  - container `BACKGROUND_COLOR` / `BORDER_*` / `BORDER_RADIUS` on a background
    `Rectangle`; `RESET_NODE` clears it;
  - **composite control mode** — a `Button` with children is promoted to a bare
    shell that renders its `ButtonStyle` body (leaf buttons stay native);
  - foreground `COLOR` inheritance into descendant `Text`/`Icon`;
  - cross-axis **hug** by default with spec *effective-fill* (explicit FILL,
    greedy `SPACER`/`DIVIDER`/`COLOR`/`SCROLLVIEW`, fill propagation incl.
    composite bodies), recomputed at frame boundaries; `CONTENT_MARGINS`;
  - `TEXT_STYLE` base typography and a best-effort `SHADOW_*` layer.
- **Media transport + reports** (`src/qt/qt_layer.cpp`, `bridge.*`,
  `src/run.rs`): `MEDIA_SOURCE`, `PLAYBACK_STATE`, `MEDIA_POSITION`,
  `MEDIA_VOLUME`, `MEDIA_MUTED`, `MEDIA_LOOP`, `MEDIA_RATE`, and the
  `MEDIA_*` event family.
- **Layout/property parity**: offsets/positions, min/ideal/max sizes,
  content mode, clipping, border color/width/radius, text alignment/line
  limit/truncation, grid columns.
- **Asset root** (`pathland_qt_set_asset_root`): `/_pathland/...` sources
  resolve against a host-extracted local directory.
- **Java Qt demo migrated** to `dev.pathland.demo.qt` (view/demo package
  renames, `environmentBinding` mount, `DemoAssets` extraction).
- **Nav content-area sizing**: the page wrapper fills the nav container, so
  route content (not just its title) renders at full size.
- **Headless test hang fixed**: the shell drops the executable directory from
  the QML import path at init, so `cargo test -p pathland-render-qt` runs
  directly (~2s) instead of scanning `target/debug/deps` for minutes.
- **QtMultimedia** linked in `build.rs`; CI installs `qt6-multimedia-dev` +
  `qml6-module-qtmultimedia`.
- **Tests**: `new_components_and_media_binding`, `decode_maps_media_reports`;
  full crate 35/35 green.

## Still missing / needs doing

- [x] **`SIZE_THAT_FITS` / `FIT_CHANGED`** — shared `LIST` decode + width measure
      + `FIT_CHANGED`.
- [x] **`GRID_ROW` flattening** — cells attach to the Grid; columns align.
- [x] **Events** — hover/leave, keys (focused text input),
      focus/editing/submit, BackSpace native back.
- [x] **`DATE_PICKER`** — `SET_DATE` → ISO text (QML approximation).
- [ ] **Controls gaps**: `MENU` popup item list, `COLOR_PICKER` (QML popup),
      `PICKER` option styling, `SHAPE` paths; native **leaf** controls keep
      Qt Quick Controls' own chrome. (`DATE_PICKER` is an ISO-text
      approximation.)
- [ ] **Icon fidelity**: glyph map vs. the web's SVG/Lucide set; shadow is an
      offset-rectangle approximation, not a blurred effect.
- [ ] **Lazy view virtualization** (currently eager) and `TRANSITION` animation.
- [ ] **Demo icons (cosmetic)**: the Qt demo references `icons/*.svg`; point it
      at real assets (or the shared `DemoAssets`) so sidebar rows show icons.

## Known constraints

- FILL inside a plain `QQuickGrid` is skipped (`Grid` rejects child anchors);
  stacks use `RowLayout`/`ColumnLayout` so FILL works there.
- Native leaf controls own their background delegate; the styling layer covers
  containers, composite controls, and `Rectangle`-backed nodes.
- Icons are renderer-owned glyphs (not the web's SVG set); shadows are an
  offset rectangle approximation.
