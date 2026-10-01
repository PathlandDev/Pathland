//! Deterministic randomized round-trip tests. A small LCG PRNG (no external
//! dependencies) generates sequences of opcodes and events, written by a
//! `Guest`/`Host` and read back by the other side. It asserts exact integrity.
//!
//! Production and consumption are interleaved in batches (the realistic SPSC
//! pattern: the producer publishes frames, the consumer drains, repeat), so a
//! small ring wraps many times — exercising cursor wraparound and backpressure
//! that fixed conformance vectors cannot.

use pathland_core::{
    component_type, init_memory, property_id, value_type, Event, Frame, Guest, Host, MemoryLayout,
    Opcode, APPEND,
};

/// Simple deterministic LCG (Numerical Recipes). Returns a value in [0, 2^32).
struct Lcg(u64);

impl Lcg {
    fn next_u32(&mut self) -> u32 {
        self.0 = self.0.wrapping_mul(1664525).wrapping_add(1013904223);
        (self.0 >> 32) as u32
    }

    fn below(&mut self, n: u32) -> u32 {
        self.next_u32() % n
    }
}

fn drain_all(mem: &mut [u8], layout: &MemoryLayout, out: &mut Vec<Opcode>) {
    let mut h = Host::new(mem, layout);
    for frame in h.frames() {
        out.extend(frame.opcodes());
    }
}

#[test]
fn random_sequences_round_trip_exactly() {
    // Small ring so the write cursor wraps many times as we produce/drain.
    let layout = MemoryLayout {
        slot_count: 4096,
        arena_bytes: 1 << 16,
        event_arena_bytes: 1 << 16,
    };
    let mut mem = vec![0u8; layout.total_bytes()];
    init_memory(&mut mem, &layout);

    let mut expected: Vec<Opcode> = Vec::new();
    let mut actual: Vec<Opcode> = Vec::new();
    let mut arena_strings: Vec<(u32, String)> = Vec::new();
    let mut rng = Lcg(0xC0FF_EE00);

    // The ring must exceed the total produced opcodes: the POC SPSC contract
    // requires the producer to flush (and the consumer to drain) before the
    // ring wraps, so a single long run drains once at the end.
    const FRAMES: usize = 200;
    const MAX_OPS_PER_FRAME: usize = 10;

    let mut node_counter = 0u32;

    {
        let mut g = Guest::new(&mut mem, &layout);
        for _ in 0..FRAMES {
            g.begin_frame();
            let n = (rng.below(MAX_OPS_PER_FRAME as u32) as usize) + 1;
            for _ in 0..n {
                match rng.below(6) {
                    0 => {
                        node_counter += 1;
                        let comp = match rng.below(4) {
                            0 => component_type::VSTACK,
                            1 => component_type::HSTACK,
                            _ => component_type::TEXT,
                        };
                        g.create_node(node_counter, comp).unwrap();
                        expected.push(Opcode::new(
                            pathland_core::category::TREE,
                            pathland_core::tree::CREATE_NODE,
                            0,
                            node_counter,
                            comp as u32,
                            0,
                        ));
                    }
                    1 => {
                        let id = node_counter.max(1);
                        let value = rng.next_u32();
                        g.set_property(id, property_id::COLOR, value_type::U32, value).unwrap();
                        expected.push(Opcode::new(
                            pathland_core::category::PARAMETER,
                            pathland_core::parameter::SET_PROPERTY,
                            0,
                            id,
                            (value_type::U32 as u32) << 16 | property_id::COLOR as u32,
                            value,
                        ));
                    }
                    2 => {
                        let id = node_counter.max(1);
                        let parent = node_counter.saturating_sub(1);
                        g.insert_child(parent, id, APPEND).unwrap();
                        expected.push(Opcode::new(
                            pathland_core::category::TREE,
                            pathland_core::tree::INSERT_CHILD,
                            0,
                            parent,
                            id,
                            APPEND,
                        ));
                    }
                    3 => {
                        let id = node_counter.max(1);
                        let len = (rng.below(12) as usize) + 1;
                        let text: String = (0..len)
                            .map(|i| char::from(b'a' + ((i as u32 + rng.next_u32()) % 26) as u8))
                            .collect();
                        let arena_ref = g.set_text(id, &text).unwrap();
                        arena_strings.push((arena_ref, text));
                        expected.push(Opcode::new(
                            pathland_core::category::PARAMETER,
                            pathland_core::parameter::SET_TEXT,
                            0,
                            id,
                            arena_ref,
                            0,
                        ));
                    }
                    4 => {
                        if node_counter > 0 {
                            g.delete_node(node_counter).unwrap();
                            expected.push(Opcode::new(
                                pathland_core::category::TREE,
                                pathland_core::tree::DELETE_NODE,
                                0,
                                node_counter,
                                0,
                                0,
                            ));
                        }
                    }
                    _ => {
                        g.reset().unwrap();
                        expected.push(Opcode::new(
                            pathland_core::category::META,
                            pathland_core::meta::RESET,
                            0,
                            0,
                            0,
                            0,
                        ));
                    }
                }
            }
            g.end_frame();
        }
    }

    // Drain everything produced across all frames.
    drain_all(&mut mem, &layout, &mut actual);

    // Full integrity check: opcode count and field-for-field equality.
    assert_eq!(actual.len(), expected.len(), "opcode count mismatch");
    for (i, (a, e)) in actual.iter().zip(expected.iter()).enumerate() {
        assert_eq!(a.category(), e.category(), "category mismatch at {i}");
        assert_eq!(a.command(), e.command(), "command mismatch at {i}");
        assert_eq!(a.flags(), e.flags(), "flags mismatch at {i}");
        assert_eq!(a.a(), e.a(), "a mismatch at {i}");
        assert_eq!(a.b(), e.b(), "b mismatch at {i}");
        assert_eq!(a.c(), e.c(), "c mismatch at {i}");
    }

    // Arena strings must all be readable and match. The arena region is a
    // fixed slice of linear memory; a synthetic frame over it can read any
    // entry the guest allocated.
    let arena_region = &mem[layout.arena_offset()..];
    let frame = Frame::from_parts(&[], arena_region, 0, 0);
    for (offset, text) in &arena_strings {
        assert_eq!(frame.arena_str(*offset).unwrap(), text.as_str());
    }
}

#[test]
fn random_events_round_trip_exactly() {
    let layout = MemoryLayout::default();
    let mut mem = vec![0u8; layout.total_bytes()];
    init_memory(&mut mem, &layout);

    // Compare events by their encoded bytes, so NaN payloads (possible from
    // random f32 bits) compare bit-exactly instead of via f32 PartialEq.
    let mut expected: Vec<[u8; 16]> = Vec::new();
    let mut rng = Lcg(0xDEAD_BEEF);

    const BATCHES: usize = 20;
    const EVENTS_PER_BATCH: usize = 50;

    for _ in 0..BATCHES {
        {
            let mut h = Host::new(&mut mem, &layout);
            for _ in 0..EVENTS_PER_BATCH {
                let target = rng.next_u32();
                let x = f32::from_bits(rng.next_u32());
                let y = f32::from_bits(rng.next_u32());
                let event = match rng.below(3) {
                    0 => Event::PointerDown {
                        target,
                        x,
                        y,
                        secondary: rng.below(2) == 1,
                    },
                    1 => Event::PointerMove {
                        target,
                        x,
                        y,
                        hovering: rng.below(2) == 1,
                        leaving: rng.below(2) == 1,
                    },
                    _ => Event::PointerUp {
                        target,
                        x,
                        y,
                        secondary: rng.below(2) == 1,
                    },
                };
                h.send_event(&event).unwrap();
                expected.push(event.encode().to_bytes());
            }
        }

        // Drain the events.
        {
            let mut g = Guest::new(&mut mem, &layout);
            for event in g.drain_events() {
                let actual = event.encode().to_bytes();
                assert_eq!(actual, expected.remove(0));
            }
        }
    }

    assert!(expected.is_empty(), "not all events were drained");
}
