//! # pathland-render-tui
//!
//! The Pathland **Ratatui (TUI) renderer**. It consumes opcode frames (via the
//! shared [`pathland_host::RenderTree`]) and maps them **incrementally** onto
//! native terminal widgets — `Layout` for stacks, `Paragraph` for text,
//! `Gauge`/`LineGauge` for progress, `Block` for buttons.
//!
//! Ratatui is immediate-mode: every terminal frame redraws the whole UI from
//! the retained tree. The renderer stays a **pure function of the opcode
//! stream** (AGENTS.md Principle 1): it retains only its rendered-output tree
//! plus the per-frame geometry cache (`id → Rect`) used for hit-testing, and
//! input focus/scroll/cursor state. All application state lives in the guest.
//!
//! ## Driving the renderer
//!
//! ```no_run
//! use pathland_render_tui::TuiRenderer;
//!
//! let mut renderer = TuiRenderer::new();
//! let mut terminal = ratatui::init();
//! terminal.draw(|f| renderer.draw(f, f.area())).unwrap();
//! ratatui::restore();
//! ```
//!
//! The host consumes frames through [`pathland_core_transport::FrameSource`]
//! (e.g. a shared-memory [`RingTransport`](pathland_core_transport::RingTransport))
//! and applies them with [`TuiRenderer::apply_frame`] before each draw pass.

pub mod layout;
pub mod render;
mod capi;
mod run;

pub use pathland_host::{HostNode, RenderTree};
pub use render::TuiRenderer;
pub use run::{run, Pump};