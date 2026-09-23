//! # pathland-render-qt
//!
//! The Pathland **Qt Quick renderer**. It consumes opcode frames (via
//! `pathland_render_core::RenderTree`) and maps them **incrementally** onto a
//! Qt Quick scene graph — `Row`/`Column` for stacks, `Text` for text, Qt Quick
//! Controls for buttons/fields/sliders.
//!
//! The crate is split across languages on purpose:
//! - **Rust** owns the shared decode core (`pathland-render-core`), the
//!   sent-state **delta diff** ([`renderer::QtRenderer`]), the `Pump`
//!   transport seam, wire encoding, and the C ABI for foreign hosts
//!   ([`capi`]).
//! - **C++** (in `src/qt/`) owns the Qt application shell and maps the delta
//!   command batch onto QML items. It reports inputs back through a wake
//!   callback; the renderer **never drains events** — the host drains its own
//!   ring, symmetric with frames flowing in.
//!
//! The Qt layer is the only place that touches Qt; the protocol core
//! (`pathland-core`/`pathland-view`) stays `no_std`/wasm-safe.

pub mod capi;
mod ffi;
pub mod renderer;
mod run;

pub use renderer::{QtRenderer, CONTENT_ITEM};
pub use run::{run, run_with_pump_sized, EventCallback, Pump};