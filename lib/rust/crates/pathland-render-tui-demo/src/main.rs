//! # Pathland TUI (Ratatui) desktop demo
//!
//! A terminal app demonstrating Pathland's **zero-copy shared-memory ring** and
//! the shared **Ratatui renderer** (`pathland-render-tui`).
//!
//! The developer authors the UI with the SwiftUI-style DSL; the diff emitter in
//! `pathland-engine` emits declarative 16-byte opcodes into the shared ring, and
//! the `pathland-render-tui` renderer maps them onto terminal widgets. The demo
//! never touches ratatui widgets directly — it only drives Pathland and the
//! renderer.
//!
//! Quit with `q`, `Esc`, or `Ctrl+C`.

use std::cell::RefCell;
use std::io::{self, stdout};
use std::rc::Rc;

use pathland_core::size;
use pathland_core_transport::{FrameSource, RingTransport};
use pathland_engine::Engine;
use pathland_render_tui::TuiRenderer;
use pathland_view::{assign_ids, button, hstack, text, vstack, Color, Node, View, ViewExt};

use ratatui::backend::CrosstermBackend;
use ratatui::crossterm::event::{self, Event, KeyCode, KeyEventKind, KeyModifiers};
use ratatui::crossterm::execute;
use ratatui::crossterm::terminal::{
    disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen,
};
use ratatui::Terminal;

/// Build the DSL tree for the current counter value. This is the developer's
/// authoring surface.
fn build_tree(count: &Rc<RefCell<u32>>) -> Node {
    vstack![
        hstack![
            text(format!("Count: {}", *count.borrow()).as_str()).frame(Some(size::FILL), None, None),
            button("Increment"),
        ]
        .spacing(8.0)
        .padding(16.0),
        text("Pathland · TUI · shared ring").foreground_style(Color::argb(0xFF_888888)),
        vstack![text("short"), text("a much longer label"), text("x")]
            .spacing(4.0)
            .padding(16.0),
    ]
    .spacing(12.0)
    .padding(24.0)
    .build()
}

fn main() -> io::Result<()> {
    let engine = Rc::new(RefCell::new(Engine::new()));
    let ring = Rc::new(RefCell::new(RingTransport::new()));
    let count = Rc::new(RefCell::new(0u32));

    // Emit the initial frame into the shared ring.
    {
        let mut tree = build_tree(&count);
        assign_ids(&mut tree, &mut 1);
        let mut ring_ref = ring.borrow_mut();
        let mut guest = ring_ref.producer();
        guest.begin_frame();
        engine.borrow_mut().emit(&tree, &mut guest).ok();
        guest.end_frame();
    }

    // Apply the initial frame, then draw.
    let mut renderer = TuiRenderer::new();
    {
        let mut ring_ref = ring.borrow_mut();
        if let Ok(Some(batch)) = ring_ref.next_frame() {
            renderer.apply_frame(batch.frame());
        }
    }

    enable_raw_mode()?;
    let mut terminal = Terminal::new(CrosstermBackend::new(stdout()))?;
    execute!(stdout(), EnterAlternateScreen)?;

    let mut quit = false;
    while !quit {
        terminal.draw(|f| {
            renderer.draw(f, f.area());
        })?;
        if event::poll(std::time::Duration::from_millis(200))? {
            if let Event::Key(key) = event::read()? {
                if key.kind == KeyEventKind::Press {
                    match key.code {
                        KeyCode::Char('q') | KeyCode::Esc => quit = true,
                        KeyCode::Char('c') if key.modifiers.contains(KeyModifiers::CONTROL) => {
                            quit = true
                        }
                        _ => {}
                    }
                }
            }
        }
    }

    execute!(stdout(), LeaveAlternateScreen)?;
    disable_raw_mode()?;
    Ok(())
}