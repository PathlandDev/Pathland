//! Cross-crate integration: a frame emitted by the engine, serialized as a
//! network batch, decoded, and applied to the GTK renderer's `RenderTree` —
//! proving the network batch path is interchangeable with the shared-memory ring.
//!
//! It lives here (not in `pathland-core-transport`) because it needs the GTK
//! renderer, which is `publish = false`: a dev-dependency on it would block
//! publishing `pathland-core-transport` to crates.io.

use pathland_core::{init_memory, Guest, Host, MemoryLayout, Opcode};
use pathland_core_transport::{BatchDecoder, BatchEncoder};
use pathland_engine::Engine;
use pathland_gtk::RenderTree;
use pathland_view::{assign_ids, text, vstack, View};

#[test]
fn decoded_batch_feeds_render_tree() {
    let layout = MemoryLayout::default();
    let mut mem = vec![0u8; layout.total_bytes()];
    init_memory(&mut mem, &layout);

    let mut root = vstack![text("hello"), text("world")].build();
    assign_ids(&mut root, &mut 1);

    let mut engine = Engine::new();
    let opcodes;
    let arena_used;
    {
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        engine.emit(&root, &mut guest).unwrap();
        guest.end_frame();

        let mut host = Host::new(&mut mem, &layout);
        let frame = &host.frames()[0];
        opcodes = frame.opcodes().collect::<Vec<Opcode>>();
        arena_used = {
            // The arena's used portion is everything written so far.
            let arena = frame.arena();
            arena.to_vec()
        };
    }

    let mut enc = BatchEncoder::new();
    let batch_bytes = enc.encode(7, &opcodes, &arena_used);

    let mut decoder = BatchDecoder::new();
    let mut tree = RenderTree::default();
    {
        let batch = decoder.decode(&batch_bytes).unwrap();
        tree.apply_frame(batch.as_frame());
    }

    // Root VStack + two texts, text "hello" resolved via the mirrored arena.
    assert_eq!(tree.nodes.len(), 3);
    let child = tree.node(2).unwrap();
    assert_eq!(child.text.as_deref(), Some("hello"));
}
