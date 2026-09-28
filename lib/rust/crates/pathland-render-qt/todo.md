# pathland-render-qt — todo / remaining work

Status of the "renders practically nothing" fix (Qt Quick Layouts migration) and
the follow-ups still open.

## Done (committed-to-code, needs final verification)

- **Qt Quick Layouts for stacks**: VSTACK/HSTACK (incl. lazy variants) render as a
  wrapper `Item` + inner `ColumnLayout`/`RowLayout`; FILL (`WIDTH/HEIGHT = -1`)
  stretches via `Layout.fillWidth/fillHeight`, fixed sizes become
  `Layout.preferredWidth/Height`, `SPACING`/`PADDING`/`ALIGNMENT` map onto the
  layout/attached props (`src/qt/qt_layer.cpp`).
- **Diff ordering**: `renderer.rs diff()` emits child inserts before property
  deltas, so a widget is attached to its final parent before `WIDTH/HEIGHT/ALIGNMENT`
  land — fixes the stale FILL-anchor-against-contentItem bug.
- **Root fills the window** (anchors.fill contentItem), like GTK's hexpand/vexpand.
- **ScrollView**: content attaches to its `contentItem`; a ScrollView fills its
  parent by default (GTK halign/valign = Fill parity).
- **Fonts**: `FONT_SIZE/FONT_WEIGHT/FONT_FAMILY` via the `font` `QFont` property
  (dotted-path `setProperty` was silently failing).
- **Nav adapter hardened**: `promoteNavSlot` detaches children before the swap and
  clears `pending_fill` (the UAF behind the committed `pump_once` SIGSEGV).
- **Tests**: new `fill_child_stretches_inside_stack` (FILL→fillWidth + root-anchor);
  `ffi::` suite (10/10) and Java `RingEventReaderTest` (5/5) pass. Full workspace
  `cargo test` passed earlier in the session.

## Still missing / needs doing

- [ ] **Clean full-crate run**: `cargo test -p pathland-render-qt` (last runs hung
      >300s — likely a stray-process/Qt-lock artifact, not a code failure; the
      `ffi::` subset passes in ~73s). Verify a clean run completes (~80–140s).
- [ ] **Java reactor**: `cd lib/java && mvn install` (qt-demo tests already green).
- [ ] **`status.md`**: update `lib/rust/crates/pathland-render-qt/status.md` for the
      Layouts/FILL/ScrollView/font changes (AGENTS.md requires status sync).
- [ ] **Visual confirmation**: run `scripts/run-java-qt-demo.sh` and eyeball the
      SplitNavDemo — sidebar + Home/Kitchen/Settings content area should now fill
      the window instead of showing only the ~200px sidebar.
- [ ] **Demo icons (cosmetic)**: the Qt demo references `icons/home.svg`,
      `icons/kitchen.svg`, `icons/settings.svg` which don't exist in
      `lib/java/pathland-qt-demo/` (only the web demos ship them). Copy/point the
      demo at real assets so the sidebar rows show their icons.
- [ ] **Test-hook cleanup (optional)**: `pathland_qt_layer_render_once` /
      `pathland_qt_layer_widget_size` / `pathland_qt_layer_anchors_fill` were added
      as test hooks; keep if useful, drop if not.

## Known gaps (pre-existing, unchanged — from status.md)

- Pointer `hover`/`leave` flags and `KEY_DOWN`/`KEY_UP` not reported; BackSpace as
  native back deferred.
- `BACKGROUND_COLOR`/border on stacks/controls (Qt Quick Controls own their
  background delegate), `SHAPE` path approximation, `MENU` popup item list,
  `DATE_PICKER`/`COLOR_PICKER` native dialogs.
- Lazy views render eagerly; `TRANSITION` animation not applied.