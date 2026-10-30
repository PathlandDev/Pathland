# Pathland Structural Reactivity + Router — Implementation Plan

**Status:** Approved — in progress (Phases 0–3, 5a/5b/5c, 6 complete)
**Branch:** `feat/router-structural-reactivity`
**Last Updated:** October 29, 2026

> **Superseded API note (Oct 2026):** the Java `Conditional`/`Case` surface was
> replaced by per-component reactive bodies (#88) + `View.of(Supplier<View>)`.
> The router design below is unchanged; read `Conditional.when` as "a component
> whose tracked render selects the branch" and `ConditionalTest` as `ViewOfTest`.

---

## Goal

A cross-platform router for Pathland: browser-path parity (Angular Router /
react-router style) **and** native navigation compatibility (SwiftUI
NavigationStack, Jetpack Compose NavHost, WinUI NavigationView, LVGL screens),
built as a DSL-level construct that respects the protocol's non-negotiables:
stateless renderers, app-owned tree, diff-based emission, "WHAT never WHERE".

## Foundation-first design

**Conditional rendering (structural reactivity) is the reusable primitive.**
A structural container owns a subtree selected by a signal and reconciles it on
change, emitting `TREE` deltas. The router is a structural container whose
content function is route-table matching, plus router-specific extras
(back-stack, URL sync, the `NAVIGATE` event, the `ROUTE`/`TRANSITION`
properties).

## Locked decisions

| Decision | Choice |
|---|---|
| Foundation | Conditional rendering **first** (shared subtree-swap machinery); router built on it |
| Conditional DSL surface (Java) | `Conditional.when(...)` + `Case.of(...)` / `Case.otherwise(...)`, statically importable — mirrors `Signals` (final class, private ctor, lowercase factories) |
| Container | Group-backed slot (bare `VSTACK` node, `Group.java`), not ZStack |
| Protocol surface (router only) | `ROUTE` property, `TRANSITION` hint, `NAVIGATE` event — conditional rendering adds **zero** wire surface |
| State ownership | App owns state; web mirrors history (`popstate` → `NAVIGATE` back to app) |
| Spec ordering | `spec/DSL.md` first (structural-reactivity exception + router), then wire specs |
| Languages | Java + Rust in parallel |
| Event name | `NAVIGATE` (`0x0E`, subsumes back/forward/popstate/deep-link) |
| v1 URL sync | Always `pushState`; `replaceState` for `replace()` = documented follow-up |

## Java conditional API

```java
import static com.pathland.view.Conditional.when;
import static com.pathland.view.Conditional.Case;

when(showLogin, LoginView.of(), HomeView.of());                 // if / else
when(mode, Case.of(HOME, HomeView.of()),
          Case.of(USERS, UsersView.of()),
          Case.otherwise(NotFoundView.of()));                   // switch + default
```

(`if`/`switch`/`case`/`else` are Java keywords — `when` is Kotlin's `switch`
analog and a valid Java identifier.)

## Architecture

1. **Structural container (Java emitter):** owns a subtree selected by a
   signal; on change, rebuilds content and **reconciles** against the retained
   `PathlandNode` snapshot, emitting `TREE` deltas; identical structure → zero
   opcodes.
2. **RouterCore (DSL):** `Router` (`Signal<Route>` + back-stack,
   `navigate/push/pop/replace/back`), `Route`, `RouteTable`, `NavigationContainer`
   (structural container + `ROUTE`/`TRANSITION`), `NavigationLink`, `Location`
   (`Browser`/`InMemory`).
3. **Protocol hooks:** `ROUTE` (URL sync in-stream), `TRANSITION` (native-feel
   transitions), `NAVIGATE` (platform back/gesture → app).

## Native compatibility

SwiftUI `NavigationStack` / Compose `NavHost` → slot + child swap +
`TRANSITION`, back → `NAVIGATE` → `pop()`; WinUI `NavigationView` → `HStack`
sidebar+detail, per-outlet slots; LVGL screens → whole-tree swap (app's
back-stack supplies the stack LVGL lacks). A slot carrying `ROUTE` is a
navigation slot and **may** be promoted onto the platform's native navigation
container; the renderer keeps the back-stack app-owned and only maps child
swaps → native push/pop, `ROUTE` → native path, `TRANSITION` → native
transition, and native back affordances → `NAVIGATE`. See `spec/DSL.md §4.5`.

---

## Phase 0 — Specs first

### 0.1 `spec/DSL.md`

1. **Structural reactivity section (§3.4):** formalize the body-once exception
   — `Conditional.when` re-evaluates content on signal change; the emitter
   reconciles the retained subtree into `TREE` deltas; identical structure →
   zero opcodes; nested containers; value-parametrized content.
2. **Navigation section (§4.5):** `Router`, `RouteTable`, `NavigationContainer`,
   `NavigationLink`, `Location`, back-stack semantics, params, redirects, web
   URL-sync contract, native back — framed as a structural-container use case.
3. **§1 body-once principle** and **§6 convention 7**: cite the exception.
4. **§9.1 checklist:** conditional rendering + navigation entries.
5. **§10 appendix:** `if`/`switch`, `NavigationStack`, `NavigationLink` rows;
   Rust delta note.

### 0.2 Wire specs

6. `PRIMITIVES.md`: composite-notes fix → Group-slot description; `0x17`–`0x1A`
   stay retired.
7. `MODIFIERS.md`: `ROUTE = 0x2019` (STRING), `TRANSITION = 0x1031` (ENUM:
   none/platform-default/fade/slide/scale) + value-type table.
8. `EVENTS.md`: `NAVIGATE = 0x0E` (host→guest, not node-keyed; optional URL
   payload on web, none = back-step on native).
9. `OPCODE.md`: value types + `NAVIGATE` payload note. Version stays 1.
10. `CONFORMANCE.md`: golden vectors — `SET_PROPERTY ROUTE` frame; `NAVIGATE`
    with URL; structural-swap `TREE` deltas.

## Phase 1 — Core (`pathland-core`) ✅ complete

- `constants.rs`: `ROUTE = 0x2019`, `TRANSITION = 0x1031`, `NAVIGATE = 0x0E`,
  `flag::NAVIGATE_URL`; `value_type_for(ROUTE) = STRING` (TRANSITION rides the
  F32-enum fallthrough).
- `Event::Navigate { url: Option<String> }` end-to-end: `encode`/`try_from`
  (bare opcode = back request; URL flag needs string section/event arena),
  shared-memory `decode_event` (event-arena resolution) and `send_event`
  (event-arena alloc), network `encode_events`/`decode_events` (batch string
  section).
- Conformance vectors 21–24 enforced in `conformance.rs`; unit + integration
  tests for both transports.
- **Finding:** `pathland-engine` has **no general STRING-property diff path**
  (numeric `properties` + design-token refs only). `TRANSITION` (F32) is
  handled generically today; `ROUTE` (STRING) needs a small string-property
  capability when the Rust router lands (Phase 4). Recorded in
  `pathland-core/status.md`.
- **Env:** `pkg-config` + GTK4 and the `wasm32-unknown-unknown` target are now
  installed locally, so the full workspace builds/tests and the wasm guard
  runs — nothing is build-blocked anymore.

## Phase 2 — Java structural reactivity ✅ complete

- `Emitter` subtree reconcile: `reconcileSlot`/`reconcileChildren`/
  `reconcileNode`/`emitSubtreeInto` — position+type-stable ids, recorded-op
  replay within one frame, skipped when unchanged (zero opcodes, no frame).
  Slot subtrees own their bindings (per-node `nodeBindings` map, destroyed on
  replace); `RenderResult` routing maps are now live unmodifiable views so a
  swap updates tap/text/value/date routing after mount.
- `Conditional` (`when(Signal<Boolean>, then, else)` +
  `when(Signal<T>, Case<T>...)` with `Case.of`/`Case.otherwise`) + a Group-backed
  `ConditionalWhen` slot view (bare `VSTACK`).
- `ConditionalTest` (7 tests): branch-swap `TREE` deltas (vector-25 shape),
  zero-opcode identical recompute, switch by key, empty slot, nested containers
  with destroyed inner effects, fine-grained reactive content. `mvn -pl
  pathland-view test` — 47 tests green.

## Phase 3 — Java router ✅ complete

- `com.pathland.view.router`: `Route` (`pathOnly()` strips query/fragment),
  `RouteTable` (literal + `:param` matching, guards → `replace()` redirect,
  `fallback` 404), `Router` (`Signal<Route>` + back-stack;
  `navigate/push/pop/replace/back`, `handlePlatformNavigation` strips the URL to
  a path, `handleEvent`), `NavigationContainer` (structural slot; **ROUTE
  property coalesced into the same frame** as the destination swap via a generic
  slot STRING property), `NavigationLink` (BUTTON that pushes).
  **Design revision (final):** no `Location` abstraction and no environment-carried
  route. The route is a **plain `WritableSignal<Route>`** owned by the `Router`
  (not persisted — the URL is the web's persistence layer; native is per-session),
  and the **host seeds it before mount** with `router.navigate(requestUrl)` so the
  initial URL flows through the same route-table/guard matching and the first SSR
  frame is correct. History adaptation is renderer-owned.
- `RenderResult.navigateHandler` — a live global sink hosts forward raw
  `NAVIGATE` events into the router; `Event.navigate`/`navigateBack` wire
  round-trip through `FrameCodec`.
- `RouterTest` (11 tests): initial route + ROUTE emission, destination-swap
  deltas, back-stack, params, guard redirect, fallback, NAVIGATE routing,
  NavigationLink, equality suppression. `mvn -pl pathland-view test` — 58 tests
  green.

## Phase 4 — Rust (on deck)

16. `if`/`match` in `build()` (free), optional `switch!` macro;
    `Route`/`RouteTable`/`Router` over `pathland-core::signal`; GTK demo (Home →
    Users → UserDetail `:id`). **Plus:** a small string-property capability in
    `pathland-engine` so the slot can emit `ROUTE` (STRING) — `TRANSITION`
    (F32) needs nothing.

## Phase 5 — Renderers + DOM client (5a/5b ✅, 5c pending)

17. ✅ `pathland-render-html`: a slot's `ROUTE` (STRING) renders as
    `data-pathland-route` and `TRANSITION` as `data-pathland-transition`
    (`platform`/`fade`/`slide`/`scale`) — verified by cargo test (37 tests).
    The Java `NavigationContainer` now emits a `TRANSITION` PlatformDefault hint.
18. ✅ `lib/typescript`: `onRoute` hook → `history.pushState` (never on hydrate);
    `popstate` → `NAVIGATE` event over `/ws` (`encodeNavigate`/`encodeNavigateBack`);
    fade/slide/scale swap animation for transition-hinted slots; tiny
    zero-dependency logger with opcode/event receive-emit logging; runtime
    element shells mirror the Rust renderer (buttons keep `pathland-button`
    after a navigation swap — drift-guard test). 138 tests + typecheck + build.
19. ✅ `pathland-render-gtk`: a window-level `EventControllerKey` (capture
    phase) maps Escape — and BackSpace when no text entry has focus — to
    `Event::Navigate { url: None }` through the event ring; the demo surfaces
    it in the console (`NAVIGATE back requested`). Verified by
    `cargo test -p pathland-render-gtk` (27 tests) + full workspace + wasm
    guard.

## Phase 6 — Demos + status ✅ (runnable in the browser)

20. ✅ The initial route is part of the **platform environment** (user-driven
    design revision): `META::ENVIRONMENT` became the extensible field family
    (`VIEWPORT_WIDTH`/`VIEWPORT_HEIGHT`/`ROUTE`, spec/OPCODE.md §Environment
    fields). **SSR** synthesizes it from the HTTP request (catch-alls in both
    demos); the **DOM client** sends it (viewport + route) as its first WS
    message and **enriches** it after connect (window-resize re-emits viewport).
    Sessions are created **lazily on the first message** so the router seeds
    from the `ROUTE` field before mount — deep links render correctly on the
    first frame and the WS tree stays consistent with the SSR HTML.
21. ✅ `pathland-demo-views`: `SplitNavDemo` is the demo root (sidebar + content;
    `/kitchen` keeps the showcase); both `SessionApp` apply the environment
    (seed + `applyEnvironment` enrichment) and forward `NAVIGATE` events.
22. ✅ **Verified in the browser**: both demos run; `curl /kitchen` → the
    kitchen-sink content + `data-pathland-route="/kitchen"`; `/home`/`/settings`
    content; unknown paths → sidebar + "Not Found"; JS bundle served. 76 Java
    tests + 61 TS tests + core vectors 26–27 green.
23. ✅ **status.md updates** (per AGENTS.md): `pathland-core`,
    `pathland-core-transport`, `lib/java/pathland-view`, `pathland-render-html`,
    `lib/typescript`, `pathland-demo-views` — in their landing changes.

### Fix (post-Phase-6): SPA catch-all no longer shadows `/ws`

The deep-link catch-all initially matched `/ws`, so the WebSocket handshake was
answered with HTML 200 instead of a 101 upgrade — clicks went nowhere. Fixed:
Spring `IndexController` `@GetMapping(value = "/{*path}", headers = "!Upgrade")`
(WebSocket upgrades carry `Upgrade: websocket` and bypass the catch-all to reach
the registered handler); Quarkus `IndexResource` `@Path("{path:(?!ws).*}")`
(negative lookahead excludes the literal `ws` path). Verified headlessly: a
WebSocket client gets a **101** handshake, and a synthetic tap on a link id makes
the server reply with `ROUTE "/users"` + the destination swap — in both demos.

## Phase 7 — Native renderer adapters (GTK first: `AdwNavigationView`) ✅

A navigation slot (a `NavigationContainer` — a container carrying `ROUTE`) may
be promoted onto a platform's native navigation container instead of an
in-place child swap. GTK is the first adapter, over `AdwNavigationView`
(`libadwaita`). The renderer stays stateless: app owns route + back-stack;
renderer maps child swaps → `push`/`pop`, `ROUTE` → native path parity,
`TRANSITION` → native transition, and the native back button → `NAVIGATE`
(no URL). Full contract: `spec/DSL.md §4.5` "Native integration".

- [x] `pathland-render-gtk`: `libadwaita` (0.7, `v1_4`) dep added; a stack node
  carrying `ROUTE` builds as `AdwNavigationView` (pages reconciled **by depth**:
  `NAV_DEPTH` 0x201A U32 — pop-down to the app's depth, push when deeper,
  replace-the-top when depth is unchanged, refresh in place on the same route;
  `ToolbarView`+`HeaderBar` page chrome, header-bar back `popped` → the
  existing `Event::Navigate { url: None }` sink, suppressed during
  renderer-driven pops).
- [x] Headless tests: ROUTE-slot detection + the pure `nav_action` depth
  decision (Refresh/Push/Replace) + `NAV_CHROME` mode — 30 GTK-crate tests green.
- [x] **Chrome mode (`NAV_CHROME` 0x201B)**: `NavigationContainer.of(router)`
  = `PlatformDefault` (renderer supplies chrome — native container where one
  exists, renderer-drawn back button on DOM); `NavigationContainer.of(router,
  Chrome.CUSTOM)` = developer owns all nav UI (GTK page = bare destination, no
  header bar; DOM never injects the back button). DOM default back button:
  injected above the slot's destination at depth > 1 (excluded from reconcile
  indexing), `onNavigateBack` → `NAVIGATE` back; hydrated from SSR
  `data-pathland-depth`. Navigation is **opt-in**: no `NavigationContainer` in
  the tree → no navigation, the renderer adds none.
- [ ] **Known limits (documented):** duplicate same-route pushes collapse
  (refresh instead of stacking); a multi-step jump deeper in one frame pushes a
  single page. Per-`TRANSITION` native animations are a follow-up.
- [ ] SwiftUI `NavigationStack(path:)` renderer (new crate) — path binding +
  swipe-back → `NAVIGATE`. **Not built; documented only** (`spec/DSL.md §4.5`).
- [ ] Compose `NavHost` renderer (new crate) — route → `NavHostController`,
  predictive back → `NAVIGATE`. **Documented only.**
- [ ] WinUI `NavigationView`/`Frame` renderer (new crate). **Documented only.**

## Open follow-ups

- `replaceState` for `replace()` (needs a wire distinction from `pushState`).
- Per-destination enter/exit transitions; multi-outlet/nested routers; a true
  passthrough `Group` protocol component; typed route params.
- **STEPPER SSR/runtime divergence**: the Rust renderer emits a native
  `<input type="number">`, the DOM client builds the custom `pathland-stepper`
  shell — a design decision to reconcile.

---

## Session handoff (September 8, 2026)

**Branch:** `feat/router-structural-reactivity` — 14 commits, working tree
clean. **Phase 5c complete**: the GTK renderer maps Escape/back (window-level
capture-phase `EventControllerKey`, BackSpace only when no text entry has
focus) → `Event::Navigate { url: None }` through the event ring, and the demo
surfaces it in the console. Verified: `cargo test` (full workspace, incl. GTK
27 tests), `check-wasm.sh`. Phases 0–3, 5a/5b/5c, 6 all done.

### Continuation todos (recommended order)

- [ ] **Rust engine string-property diff (Prerequisite B)** — add
  `string_properties: BTreeMap<u16, String>` to the engine `Node` + diff
  emission → `SET_PROPERTY` STRING (lets a Rust `NavigationContainer` emit
  `ROUTE`; generalizes `LABEL`/`PROMPT`/`FONT_FAMILY`).
- [ ] **Rust DSL signal surface (Prerequisite A)** — `pathland-view` is
  structural-only today (DSL.md §10): add `Text(SignalId)`, signal modifier
  overloads, `TextField` writable binding, `Button` action wiring over
  `pathland-core::signal`.
- [ ] **Phase 4 — Rust router** — `pathland-view::router`: `Route`/`RouteTable`/
  `Router` over `pathland-core::signal`, `NavigationContainer` (Group slot
  emitting `ROUTE`), `NavigationLink`; GTK demo (Home → Users → UserDetail `:id`).
- [ ] **STEPPER SSR/runtime reconciliation** (design decision: native number
  input vs the custom `pathland-stepper` shell).
- [ ] **`replaceState` wire distinction** so `replace()` → `replaceState`.
- [ ] **`.transition(...)` Java modifier** — expose the `TRANSITION` hint.
- [ ] **Java conformance byte-parity** for vectors 21–27 (structural swap,
  `ROUTE`, `NAVIGATE`, `ENVIRONMENT`).
- [ ] **`/ws` plain-GET consistency** (Spring serves HTML; make 404).
- [ ] **Web back-button history refinement** — app-initiated `pop()` currently
  pushes a new history entry; use `replaceState`/`history.back()` for pops.
- [ ] **Per-`TRANSITION` native animations** — map the `TRANSITION` (0x1031)
  hint (fade/slide/scale) onto `AdwNavigationView`/native transition choices.

Next: **Prerequisite B** (engine string-property diff) then **Phase 4** — the
Rust router — the natural continuation that builds on the now-complete desktop
`NAVIGATE` path.

---

## Session handoff (September 4, 2026)

**Branch:** `feat/router-structural-reactivity` — 13 commits, working tree
clean. Phases 0–3, 5a/5b, 6 are complete and verified (Rust full workspace
incl. GTK + wasm guard, `mvn install`, TS 138 tests). `pkg-config` + GTK4 and
the `wasm32-unknown-unknown` target are installed — nothing is build-blocked.

Phase 5c landed in the following session (see the September 8 handoff above).