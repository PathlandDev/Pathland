# Pathland Engine Semantics

**Status:** Draft
**Wire protocol version:** 1
**Last Updated:** November 1, 2026

`OPCODE.md` defines the 16-byte wire format. `CONFORMANCE.md` defines individual
opcode byte vectors. This document defines the **retained-tree engine behavior**
that produces frames: what gets emitted, in what order, and what must emit
nothing.

The shared trace fixtures live in [`engine-traces/`](./engine-traces/). They are
self-contained PLPL batches (opcodes + frame strings), emitted by the canonical
Rust engine and compared byte-for-byte by the JVM engine test.

---

## 1. Scope

The engine:

- owns the application's retained tree and signal bindings;
- compares the current tree with the last emitted snapshot;
- emits only the `TREE` / `PARAMETER` deltas needed to reach the current tree;
- never computes layout and never emits rectangles.

The renderer owns native elements, layout, styling defaults, and input
recognition.

---

## 2. Terms

| Term | Meaning |
|------|---------|
| **Mount** | First emission of a tree. Emits the complete structure and content. |
| **Snapshot** | A full re-emission of the current tree (`META::RESYNC` / reconnect). |
| **Delta** | Opcodes for nodes that changed since the previous emission. |
| **Frame** | One bounded batch of opcodes plus its string data. |
| **Zero-op** | A recompute that emits no opcodes and produces no new frame. |

A renderer may receive any number of deltas after the initial mount/snapshot.
It must converge to the same rendered tree as a fresh snapshot of the same
retained tree.

---

## 3. Node identity

Node ids are assigned **pre-order**, root first, starting at `1`.

```text
VStack(id=1)
├── Text(id=2)
└── Text(id=3)
```

Rules:

- A frame MUST NOT contain two nodes with the same id.
- Structural reconciliation keeps ids stable for position+type-stable nodes.
- A removed node gets `TREE::DELETE_NODE`.
- A new node gets `TREE::CREATE_NODE`.
- A child relationship is established with `TREE::INSERT_CHILD`.
- `MEDIA_CONTROLLER` nodes keep their **pinned** playback id across subtree
  re-emissions (spec/MEDIA.md).

---

## 4. Canonical mount order

For each node, before its children:

1. `TREE::CREATE_NODE`
2. `TREE::INSERT_CHILD` (when the node has a parent)
3. node properties (`PARAMETER::SET_PROPERTY`, including token/string/list
   properties)
4. node text (`PARAMETER::SET_TEXT`) and date (`PARAMETER::SET_DATE`)
5. recurse into children, in child order

The child is attached **before** its own content/properties, so an id-keyed
renderer can create and place the native element before applying content.

The trace fixtures assert this order. Property ordering within a node is
deterministic and follows the engine's stored property order.

---

## 5. Reactive emission

The engine records which nodes read each signal during emission:

- a text binding (`node.text_binding`);
- a property binding (`node.property_bindings`);
- a string-property binding (`node.string_bindings`).

When a signal changes, only the bound nodes re-emit:

- text → `PARAMETER::SET_TEXT`
- property → `PARAMETER::SET_PROPERTY`
- string property → `PARAMETER::SET_PROPERTY` with the `STRING` value type

An unrelated signal change emits nothing. A write that does not change the
signal value emits nothing.

### Zero-op recompute

An unchanged tree or unchanged signal MUST emit **zero** opcodes and MUST NOT
produce a new frame. This is the steady-state contract that makes the ring
zero-allocation during idle re-renders.

---

## 6. Structural deltas

When a tree changes structurally:

| Change | Required opcodes |
|--------|------------------|
| Node added | `CREATE_NODE` + `INSERT_CHILD` + node content |
| Node removed | `REMOVE_CHILD` (when parented) + `DELETE_NODE` |
| Child reordered | `MOVE_CHILD` (same node ids) |
| Parent changed | remove from old parent + insert under new parent |

The engine does not resend unchanged subtrees. A structural change that leaves
a descendant identical emits no descendant opcodes.

---

## 7. Fit, media, navigation

These are engine-owned surfaces with the same diff discipline:

- **`SIZE_THAT_FITS`**: only the selected candidate is normally transmitted;
  snapshot/SSR emission may include every candidate for first paint.
  `FIT_CHANGED` re-renders only the fit component.
- **Media**: one non-visual `MEDIA_CONTROLLER` node per controller, stable id;
  registered controllers are pinned as root children (so audio needs no view),
  and a `VIDEO` surface references its controller via `MEDIA_CONTROLLER_ID`.
  Transport changes emit only the changed controller property.
- **Navigation**: a `NavigationContainer` is an ordinary tracked component; a
  route change re-renders only its destination slot.

Detailed wire contracts remain in `PRIMITIVES.md`, `MEDIA.md`, and `DSL.md`.

---

## 8. Shared engine traces

Fixtures live under `spec/engine-traces/<scenario>/<step>.plpl.hex`.

- Each file is one **self-contained PLPL batch** (`FrameCodec` format).
- The hex is lowercase; comments start with `#`.
- The batch includes the opcode list and the exact frame string table.
- The fixture is emitted by the canonical Rust engine and compared byte-for-byte
  by the JVM engine test.

### Current scenarios

| Scenario | Steps | Covers |
|----------|-------|--------|
| `mount_text_stack` | `000-mount` | Create/insert order, `SPACING`, `SET_TEXT` |
| `signal_text_delta` | `000-mount`, `001-set-text` | Mount + text-signal delta only |
| `property_signal_delta` | `000-mount`, `001-set-spacing` | Mount + property-signal delta only |
| `styled_text_mount` | `000-mount` | Property-before-text emission order |
| `structural_add_remove` | `000-mount`, `001-add-child`, `002-remove-child` | Create/insert append, remove+delete |

### Generate

From `lib/rust`:

```bash
cargo run -p pathland-engine-golden -- --emit
```

### Verify

```bash
cd lib/rust && cargo run -p pathland-engine-golden -- --check
cd lib/java && mvn -q -pl pathland-view-core -am test -Dtest=EngineTraceTest
```

CI regenerates the fixtures from Rust and fails on drift; the Java reactor runs
`EngineTraceTest` as part of `mvn test`.

### Adding a scenario

1. Add the scenario to `pathland-engine-golden`.
2. Run `--emit` and commit the new fixtures with the behavior change.
3. Add the equivalent scenario to the JVM `EngineTraceTest`.
4. Update the table above.

---

## 9. Not covered yet

The trace set is intentionally small at this stage. Future traces should cover:

- structural insert/remove/move deltas;
- unchanged recompute (zero-op) as a frame-level fixture;
- design-token and string/list property deltas;
- window metadata and theme frames;
- fit-slot selection and snapshot emission;
- media transport changes;
- navigation destination swaps.
