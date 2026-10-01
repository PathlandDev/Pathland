//! Host → guest event-ring tests over the public API: backpressure (full ring),
//! FIFO ordering of multiple events, and drain-twice-is-empty.

use pathland_core::{init_memory, Event, Host, MemoryLayout, RingError};

struct Memory {
    mem: Vec<u8>,
    layout: MemoryLayout,
}

impl Memory {
    fn new(layout: MemoryLayout) -> Self {
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);
        Self { mem, layout }
    }

    fn guest(&mut self) -> pathland_core::Guest<'_> {
        pathland_core::Guest::new(&mut self.mem, &self.layout)
    }

    fn host(&mut self) -> Host<'_> {
        Host::new(&mut self.mem, &self.layout)
    }
}

fn pointer_up(target: u32, x: f32, y: f32) -> Event {
    Event::PointerUp {
        target,
        x,
        y,
        secondary: false,
    }
}

#[test]
fn events_are_delivered_in_fifo_order() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut h = m.host();
        h.send_event(&pointer_up(1, 1.0, 2.0)).unwrap();
        h.send_event(&pointer_up(2, 3.0, 4.0)).unwrap();
        h.send_event(&pointer_up(3, 5.0, 6.0)).unwrap();
    }

    let mut g = m.guest();
    let events = g.drain_events();
    assert_eq!(
        events,
        vec![pointer_up(1, 1.0, 2.0), pointer_up(2, 3.0, 4.0), pointer_up(3, 5.0, 6.0)]
    );

    // Second drain is empty.
    assert!(g.drain_events().is_empty());
}

#[test]
fn event_ring_reports_full() {
    // Tiny ring: slot_count=2 -> usable capacity is slot_count-1 = 1 event.
    let layout = MemoryLayout {
        slot_count: 2,
        arena_bytes: 1 << 10,
        event_arena_bytes: 1 << 10,
    };
    let mut m = Memory::new(layout);

    {
        let mut h = m.host();
        assert!(h.send_event(&pointer_up(1, 0.0, 0.0)).is_ok());
        assert_eq!(
            h.send_event(&pointer_up(2, 0.0, 0.0)).unwrap_err(),
            RingError::Full
        );
    }

    // Draining frees a slot so the host can write again.
    {
        let mut g = m.guest();
        assert_eq!(g.drain_events().len(), 1);
    }
    {
        let mut h = m.host();
        assert!(h.send_event(&pointer_up(3, 0.0, 0.0)).is_ok());
    }
}

#[test]
    fn text_changed_round_trips_over_the_shared_ring() {
        use pathland_core::component_type;

        let mut m = Memory::new(MemoryLayout::default());

        {
            let mut h = m.host();
            h.send_event(&Event::TextChanged {
                target: 7,
                value: "hello".into(),
            })
            .unwrap();
        }

        let mut g = m.guest();
        assert_eq!(
            g.drain_events(),
            vec![Event::TextChanged {
                target: 7,
                value: "hello".into()
            }]
        );
        assert!(g.drain_events().is_empty());

        // The event arena cursor advanced past the entry.
        assert_eq!(g.event_arena_cursor(), 4 + 5, "4-byte length prefix + 5 bytes of text");

        // Sanity: tree frames and the event arena coexist.
        {
            let mut g2 = m.guest();
            g2.begin_frame();
            g2.create_node(1, component_type::VSTACK).unwrap();
            g2.end_frame();
        }
        {
            let mut h2 = m.host();
            assert_eq!(h2.frames().len(), 1);
        }
    }

    #[test]
    fn navigate_round_trips_over_the_shared_ring() {
        let mut m = Memory::new(MemoryLayout::default());

        {
            let mut h = m.host();
            h.send_event(&Event::Navigate {
                url: Some("/users/42".into()),
            })
            .unwrap();
            h.send_event(&Event::Navigate { url: None }).unwrap();
        }

        let mut g = m.guest();
        assert_eq!(
            g.drain_events(),
            vec![
                Event::Navigate {
                    url: Some("/users/42".into())
                },
                Event::Navigate { url: None },
            ]
        );
        assert!(g.drain_events().is_empty());

        // The event arena cursor advanced past the URL entry (4-byte length prefix + 9 bytes).
        assert_eq!(g.event_arena_cursor(), 4 + 9);
    }

    #[test]
    fn guest_set_date_emits_the_set_date_command() {
        use pathland_core::{component_type, parameter};

        let mut m = Memory::new(MemoryLayout::default());
        {
            let mut g = m.guest();
            g.begin_frame();
            g.create_node(1, component_type::DATE_PICKER).unwrap();
            g.set_date(1, -1, 3_600_000).unwrap();
            g.end_frame();
        }
        let ops: Vec<_> = {
            let mut h = m.host();
            h.frames()[0].opcodes().collect()
        };
        assert_eq!(ops.len(), 2);
        let date = &ops[1];
        assert_eq!(date.category(), pathland_core::category::PARAMETER);
        assert_eq!(date.command(), parameter::SET_DATE);
        assert_eq!(date.a(), 1);
        assert_eq!(date.b() as i32, -1);
        assert_eq!(date.c(), 3_600_000);
    }

    #[test]
    fn reset_recycles_the_event_arena() {
        let mut m = Memory::new(MemoryLayout::default());

        {
            let mut h = m.host();
            h.send_event(&Event::TextChanged {
                target: 1,
                value: "aaa".into(),
            })
            .unwrap();
        }
        {
            let mut g = m.guest();
            assert_eq!(g.drain_events().len(), 1);
        }

        // A guest META::RESET frame resets the host's event arena cursor.
        {
            let mut g = m.guest();
            g.begin_frame();
            g.reset().unwrap();
            g.end_frame();
        }
        {
            let mut h = m.host();
            assert_eq!(h.frames().len(), 1);
        }
        {
            let g = m.guest();
            assert_eq!(g.event_arena_cursor(), 0, "event arena cursor resets on META::RESET");
        }

        // The host can write fresh text into the recycled arena.
        {
            let mut h = m.host();
            h.send_event(&Event::TextChanged {
                target: 2,
                value: "bb".into(),
            })
            .unwrap();
        }
        {
            let mut g = m.guest();
            assert_eq!(
                g.drain_events(),
                vec![Event::TextChanged {
                    target: 2,
                    value: "bb".into()
                }]
            );
        }
    }

#[test]
    fn events_and_tree_rings_are_independent() {
    let mut m = Memory::new(MemoryLayout::default());

    // Interleave a tree frame and events; each direction must not corrupt the other.
    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, pathland_core::component_type::VSTACK).unwrap();
        g.end_frame();
    }
    {
        let mut h = m.host();
        h.send_event(&pointer_up(9, 1.0, 2.0)).unwrap();
    }

    {
        let mut g = m.guest();
        assert_eq!(g.drain_events(), vec![pointer_up(9, 1.0, 2.0)]);
    }

    {
        let mut h = m.host();
        let ops: Vec<_> = h.frames()[0].opcodes().collect();
        assert_eq!(ops.len(), 1);
        assert_eq!(ops[0].a(), 1);
    }
}
