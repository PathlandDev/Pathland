# Pathland Rust Workspace

The Rust core of Pathland: the **opcode engine** that emits the declarative
view structure as a fixed 16-byte opcode ring buffer into shared linear memory,
consumed by host renderers. Runs natively on desktop (C ABI + GTK renderer).

```
crates/
│  # ── The three elements ────────────────────────────────────────────────
├── pathland-view/          # RETAINED UI — SwiftUI-style view DSL: VStack/HStack/Text/
│                           #   Button + chainable modifiers (spacing/padding/fontSize/
│                           #   color/background) building a retained tree (no_std)
├── pathland-core/          # OPCODE ENGINE — retained tree types (Node/Component) + the
│                           #   diff-based reactive emitter + 16-byte opcode, ring buffer,
│                           #   bump arena, constants, events (no_std, zero-alloc steady state)
├── pathland-core-transport/# OPCODE ENGINE — transport: shared-memory ring owner + network
│                           #   batch encode/decode + batching policy (std)
├── pathland-render-gtk/    # RENDERER — opcode frames -> native GTK4 widgets (incremental);
│                           #   the only crate that touches GTK/glib/pango
│  # ── Retained-UI projection (host/driver surface for other languages) ──
├── pathland-view-native/   # native C-ABI shim + NativeHost over a zero-copy shared ring
│                           #   (Swift/Java/C#; cdylib name stays "pathland_native")
├── pathland-core-capi/     # minimal shared-memory ring C ABI for foreign hosts
│                           #   (libpathland_core): create/destroy, ring push, frame
│                           #   boundaries, zero-copy read, event drain/send
└── pathland-render-gtk-demo/ # GTK4 desktop demo: authors the DSL, renders through
                              #   pathland-render-gtk (uses NO GTK APIs directly)
```

## Java libraries (framework-agnostic)

The reusable Java libraries live in `../java/` (Maven reactor, `dev.pathland`):

- `pathland-view` — SwiftUI-like view DSL + Angular-style signals/computed/effects,
  fine-grained opcode emitter, wire codec, JNA ring interop (`com.pathland.view`)
- `pathland-render-html` — pure-function HTML renderer (SSR) over the opcode stream
- `pathland-state-redis` — Redis-backed `StateStore` (Lettuce)
- `pathland-quarkus-demo` — Quarkus SSR + WebSocket demo consuming the libraries

## Specification

- [`spec/OPCODE.md`](../../spec/OPCODE.md) — the 16-byte opcode protocol
- [`spec/CONFORMANCE.md`](../../spec/CONFORMANCE.md) — golden byte vectors

## Run tests

```bash
PATH="$HOME/.cargo/bin:$PATH" cargo test
```

## Verify the no_std / wasm32 direction guard

`pathland-core` and `pathland-view` must stay `no_std` + `alloc` and compile for
`wasm32-unknown-unknown`, so the core can still target a browser/embedded/remote
projection later. Verify with:

```bash
./check-wasm.sh
```

## Run the GTK desktop demo (native, zero-copy shared ring)

```bash
PATH="$HOME/.cargo/bin:$PATH" cargo run -p pathland-render-gtk-demo
```

Requires GTK4 (`brew install gtk4` on macOS).

## Run the Java libraries (build + test)

The Java libraries build against `libpathland_core` when the native ring path is
used (desktop/JNA). Build and test the whole reactor:

```bash
PATH="$HOME/.cargo/bin:$PATH" cargo build -p pathland-core-capi
cd ../java && mvn -q install
```

The `pathland-view` core (views, signals, emitter, codec, HTML renderer) needs no
native library and runs on the JVM alone; only the JNA ring sink loads
`libpathland_core`.


## Engine example

The engine emits declarative structure (no rects). A signal changes a property;
the next `emit` writes only the delta opcodes.

```rust
use pathland_core::{init_memory, Guest, MemoryLayout};
use pathland_view::{assign_ids, text, vstack, View, ViewExt};

let layout = MemoryLayout::default();
let mut mem = vec![0u8; layout.total_bytes()];
init_memory(&mut mem, &layout);

let mut guest = Guest::new(&mut mem, &layout);
let mut engine = pathland_core::Engine::new();

let mut root = vstack![text("Hello")]
    .spacing(4.0)
    .padding(8.0)
    .build();
assign_ids(&mut root, &mut 1);

guest.begin_frame();
engine.emit(&root, &mut guest).unwrap(); // full structure
guest.end_frame();

root.properties.insert(pathland_core::property_id::SPACING, 12.0f32.to_bits());

guest.begin_frame();
engine.emit(&root, &mut guest).unwrap(); // exactly one SET_PROPERTY
guest.end_frame();
```

Emission in the steady state allocates nothing; only changed nodes produce
opcodes, so an unchanged tree emits zero opcodes. Native renderers map the
structure onto their native elements (GTK4 widgets, DOM elements).

## Network transport example

Frames emitted into the ring can be serialized into network batches and flushed
on a time/size policy (`BatchEncoder` / `Batcher`); a `BatchDecoder` rebuilds a
`Frame`-compatible view so the same `RenderTree::apply_frame` consumes batches
and shared-memory frames identically. See `spec/OPCODE.md#transport`.
