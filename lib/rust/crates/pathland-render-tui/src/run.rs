//! The TUI event loop: apply frames, redraw, route raw input.
//!
//! Raw terminal input (mouse + keyboard) is mapped to protocol [`Event`]s and
//! written through the pump; the host drains them itself (the wire is the
//! opcode engine, never a side-channel callback — AGENTS.md Principle 3).

use std::cell::RefCell;
use std::io::stdout;
use std::rc::Rc;
use std::time::Duration;

use pathland_core::Event;
use pathland_core_transport::{DriverTransport, FrameSource};
use ratatui::backend::CrosstermBackend;
use ratatui::crossterm::event::{self, Event as TermEvent, KeyCode, KeyEventKind, KeyModifiers};
use ratatui::crossterm::execute;
use ratatui::crossterm::terminal::{
    disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen,
};
use ratatui::Terminal;

use crate::TuiRenderer;

/// The bidirectional transport surface the TUI event loop pumps: frames in
/// (guest → host) and events out (host → guest).
pub trait Pump: FrameSource + DriverTransport {}

impl<T: FrameSource + DriverTransport> Pump for T {}

/// Run the TUI renderer to completion over `pump`.
///
/// Applies pending frames, redraws the terminal each loop iteration, routes
/// raw input into protocol `Event`s written through the pump, and calls `wake`
/// whenever events were written (the host drains the event ring itself).
///
/// Controls: `q`/`Ctrl+C` quits (a focused text input consumes `q`); `Esc`
/// clears focus or, when nothing is focused, sends a `NAVIGATE` back request;
/// `Tab`/`Shift+Tab` cycle focus.
pub fn run<P: Pump>(pump: Rc<RefCell<P>>, mut wake: impl FnMut()) {
    let mut renderer = TuiRenderer::new();
    {
        let mut p = pump.borrow_mut();
        apply_pending(&mut *p, &mut renderer);
    }

    let _ = enable_raw_mode();
    let mut terminal = Terminal::new(CrosstermBackend::new(stdout())).expect("terminal backend");
    let _ = execute!(stdout(), EnterAlternateScreen);

    let mut quit = false;
    while !quit {
        {
            let mut p = pump.borrow_mut();
            apply_pending(&mut *p, &mut renderer);
        }
        let _ = terminal.draw(|f| renderer.draw(f, f.area()));
        renderer.tick();

        if !event::poll(Duration::from_millis(100)).unwrap_or(false) {
            continue;
        }
        let Ok(ev) = event::read() else {
            continue;
        };
        match &ev {
            TermEvent::Key(k) if k.kind == KeyEventKind::Press => {
                let ctrl_c =
                    k.code == KeyCode::Char('c') && k.modifiers.contains(KeyModifiers::CONTROL);
                let quit_q = k.code == KeyCode::Char('q') && !renderer.has_text_focus();
                if ctrl_c || quit_q {
                    quit = true;
                } else if k.code == KeyCode::Esc {
                    if renderer.has_text_focus() {
                        renderer.set_focus(None);
                    } else {
                        let _ = pump
                            .borrow_mut()
                            .send_input(&Event::Navigate { url: None });
                        wake();
                    }
                } else {
                    handle(&pump, &mut renderer, &mut wake, ev);
                }
            }
            TermEvent::Resize(..) => {}
            _ => handle(&pump, &mut renderer, &mut wake, ev),
        }
    }

    let _ = execute!(stdout(), LeaveAlternateScreen);
    let _ = disable_raw_mode();
}

/// Apply every pending frame from the pump to the renderer.
fn apply_pending<P: FrameSource>(pump: &mut P, renderer: &mut TuiRenderer) {
    loop {
        match pump.next_frame() {
            Ok(Some(batch)) => renderer.apply_frame(batch.frame()),
            _ => break,
        }
    }
}

/// Map a terminal event to protocol events, write them through the pump, and
/// wake the host once if any were written.
fn handle<P: Pump>(
    pump: &Rc<RefCell<P>>,
    renderer: &mut TuiRenderer,
    wake: &mut dyn FnMut(),
    ev: TermEvent,
) {
    let events = renderer.on_input(&ev);
    if !events.is_empty() {
        let mut p = pump.borrow_mut();
        for e in &events {
            let _ = p.send_input(e);
        }
        drop(p);
        wake();
    }
}