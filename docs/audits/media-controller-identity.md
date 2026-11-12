# Media-controller identity traceability pilot

**Audited:** 2026-11-12
**Source baseline:** `e6f89f5f8b03da49f4a6c10488e2512489701a56`
**Scope:** Rust/Kotlin controller projection, ID assignment, surface binding,
and existing engine/DSL tests. Subsequent working-tree changes at audit time
add documentation and reference comments, not media runtime behavior.

This is a dated evidence report, not implementation-status authority or a claim
that every media contract is satisfied. Open gaps also live in the affected
projects' `status.md` files.

## Governing documents

- [ADR-0006](../decisions/0006-media-controller-identity.md).
- [Controller/surface model](../../spec/MEDIA.md#1-the-model): the reference is
  not a parent/child edge; relative position carries no meaning.
- [Controller lifetime](../../spec/MEDIA.md#22-layout-must).
- [Structural swaps](../../spec/MEDIA.md#5-structural-swaps-the-motivating-contract):
  stable identity, no controller opcodes for an unchanged controller.

## Traceability map

| Requirement | Rust implementation / tests | Kotlin implementation / tests |
|-------------|-----------------------------|-------------------------------|
| Stable controller ID across tree changes | `assign_ids`, `assign_ids_pass` in [node.rs](../../lib/rust/crates/pathland-engine/src/node.rs); `controller_id_is_pinned_across_a_structural_swap` in [DSL tests](../../lib/rust/crates/pathland-view/src/lib.rs) | `appendRootControllers`, `newControllerNode` in [Emitter.kt](../../lib/java/pathland-view-core/src/main/kotlin/dev/pathland/view/engine/Emitter.kt); `controllerIdSurvivesRootReRendersAndPropertyDeltasTargetIt` in [MediaProjectionTest](../../lib/java/pathland-view-core/src/test/kotlin/dev/pathland/view/dsl/MediaProjectionTest.kt) |
| Non-visual projection outside swapping branch | `MediaControllerExt::attach` in [Rust DSL](../../lib/rust/crates/pathland-view/src/lib.rs), explicitly placed by the app | `registerMediaController` and root projection; `registeredAudioControllerGetsAPinnedRootNode` |
| Video references controller regardless of order | `Video::build_env`, `assign_ids_pass`; existing `video_surface_links_its_controller` covers controller-first only | `bindVideoControllers`; `videoStaysInPlaceAndBindsTheRootController` covers surface-first |

The implementations and focused existing tests now carry spec references.
Binding code's `[implements]` annotation states the governing requirement, not
that all valid inputs work; the Rust counterexample below demonstrates why this
distinction matters.

## MEDIA-001 — Rust surface-first binding is absent on initial assignment

**Finding:** Demonstrated implementation violation; unresolved in this audit.

**Evidence:** `assign_ids_pass` walks the tree pre-order and binds a VIDEO only
if its controller already has a nonzero pinned ID. It assigns a new controller's
ID when visiting its marker. A VIDEO preceding that marker therefore has no
binding on its first assignment. The existing reservation pass collects IDs
already pinned; it does not preassign new controllers or backfill earlier videos.

The media model explicitly says relative tree position carries no meaning.
Both nodes in this example are stable root children, outside swapping branches;
this is not a placement-lifetime violation by the application.

### Reproduction

Build with `cargo build -p pathland-view` from `lib/rust`, then compile this
standalone probe against `target/debug/libpathland_view.rlib`:

```rust
use std::rc::Rc;
use pathland_view::{assign_ids, vstack, Engine, MediaController,
                    MediaControllerExt, Video, View};

fn main() {
    let engine = Engine::new();
    let player = Rc::new(MediaController::with_source(
        &engine.runtime(), "/clip.mp4"));
    let mut root = vstack![Video::new(player.clone()), player.attach()].build();
    assign_ids(&mut root, &mut 1);
    let controller_id = root.children[1].id;
    let actual = root.children[0].properties.get(&0x103A).copied();
    assert_eq!(actual, Some(controller_id));
}
```

For example, save the probe outside the checkout and run from `lib/rust`:

```sh
rustc --edition=2021 -L dependency=target/debug/deps \
  --extern pathland_view=target/debug/libpathland_view.rlib \
  /path/to/probe.rs -o /path/to/probe
/path/to/probe
```

**Executed result:** controller ID `3`, surface property `None`; assertion
failed with expected `Some(3)` (exit 101). This demonstrates missing engine
binding; native playback/rendering was not exercised by the probe.

**Existing coverage:** Rust's surface-binding and structural-swap tests both
place `attach()` before VIDEO. All four Rust `media_tests` pass. Kotlin's
surface-first test passes in the five-test `MediaProjectionTest` suite.

**Proposed resolution:** assign all controller IDs before linking surfaces, or
perform a separate binding pass after assignment. Add a surface-first regression
test covering initial mount and reordered/rebuilt trees. Preserve existing ID
reservation and uniqueness guarantees. Do not change the spec to require
controller-first authoring. This proposal is not implemented by this audit.

## MEDIA-COVERAGE-001 — Focused tests do not establish full swap conformance

**Finding:** Coverage limitation, not a second demonstrated runtime violation.

The Rust swap test checks IDs and properties in rebuilt nodes, not the emitted
frame. Kotlin's focused test exercises a root rerender without a controller
create/delete and verifies a later transport update's target; it does not cover
every swapping subtree shape or prohibit every unchanged controller property
opcode. Neither focused suite proves actual native playback continuity.

**Proposed follow-up:** add frame-level tests around replacement of VIDEO/control
subtrees while a controller remains mounted. Assert no controller mutations when
unchanged, stable references in replacement views, and correct event targets.
Audit renderer playback reuse separately before claiming end-to-end continuity.

## Verification performed

- Reference checker unit tests: 9 passed at pilot verification (including CLI
  tracked/untracked inventory, ignored-file handling, and failure exit status).
- `python3 scripts/check-references.py`: 15 references across 4 source files
  resolved at pilot verification; coverage informational.
- `cargo test -p pathland-view media_tests`: 4 passed.
- `mvn -q -pl pathland-view-core -am test -Dtest=MediaProjectionTest
  -Dsurefire.failIfNoSpecifiedTests=false`: passed, 5 focused tests.
- Standalone Rust counterexample above: failed as described, confirming MEDIA-001.

No renderer/GStreamer/Qt/browser playback integration test was run for this audit.
