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
  - `GRID_ROW` 0x1D → `RowLayout` cell.
- **Media transport + reports** (`src/qt/qt_layer.cpp`, `bridge.*`,
  `src/run.rs`): `MEDIA_SOURCE`, `PLAYBACK_STATE`, `MEDIA_POSITION`,
  `MEDIA_VOLUME`, `MEDIA_MUTED`, `MEDIA_LOOP`, `MEDIA_RATE`, and the
  `MEDIA_*` event family.
- **Layout/property parity**: offsets/positions, min/ideal/max sizes,
  content mode, clipping, border color/width/radius, text alignment/line
  limit/truncation, grid columns.
- **Headless test hang fixed**: the shell drops the executable directory from
  the QML import path at init, so `cargo test -p pathland-render-qt` runs
  directly (~2s) instead of scanning `target/debug/deps` for minutes.
- **QtMultimedia** linked in `build.rs`; CI installs `qt6-multimedia-dev` +
  `qml6-module-qtmultimedia`.
- **Tests**: `new_components_and_media_binding`, `decode_maps_media_reports`;
  full crate 24/24 green.

## Still missing / needs doing

- [ ] **`SIZE_THAT_FITS` / `FIT_CHANGED`**: decode the `FIT_QUERY` `LIST`
      (arena `f32` thresholds) in `pathland-render-core` (or the Qt diff), then
      measure allocated width and report `FIT_CHANGED` so the app can switch
      candidates without a round trip.
- [ ] **`GRID_ROW` flattening**: attach a row's cells directly to the `Grid`
      (instead of one `RowLayout` cell) so columns align across rows.
- [ ] **Events**: pointer `hover`/`leave`, `KEY_DOWN`/`KEY_UP`,
      `FOCUS_CHANGED`/`EDITING_CHANGED`/`SUBMIT`, BackSpace native back.
- [ ] **Asset root**: resolve `/_pathland/assets/...` to a local root for
      `MEDIA_SOURCE`/`IMAGE_SOURCE` (a `pathland_qt_set_asset_root` analogue of
      GTK's).
- [ ] **Controls gaps**: `MENU` popup item list, native `DATE_PICKER`/
      `COLOR_PICKER`, `PICKER` option styling, `BACKGROUND_COLOR`/border on
      Qt Quick Controls, `SHAPE` paths.
- [ ] **Lazy view virtualization** (currently eager) and `TRANSITION` animation.
- [ ] **Demo icons (cosmetic)**: the Qt demo references `icons/*.svg`; point it
      at real assets (or the shared `DemoAssets`) so sidebar rows show icons.

## Known constraints

- FILL inside a plain `QQuickGrid` is skipped (`Grid` rejects child anchors);
  stacks use `RowLayout`/`ColumnLayout` so FILL works there.
- Qt Quick Controls own their background delegate, so border/background on
  controls is limited.
