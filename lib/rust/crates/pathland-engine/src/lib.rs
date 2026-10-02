//! # pathland-engine
//!
//! The retained view tree (`Node`/`Component`) and the diff-based reactive
//! emitter (`Engine`) that turns it into declarative `TREE`/`PARAMETER` opcodes,
//! plus the reactive signals (`SignalStore`/`SignalId`/`SignalValue`) the
//! engine records during emission.
//!
//! This crate is `no_std` + `alloc`. It depends on [`pathland_core`] for the
//! wire types (`Opcode`, `Guest`, `RingError`) and protocol constants.
//!
//! This is the "smart application" half of Pathland; the protocol/transport
//! half lives in `pathland-core` + `pathland-core-transport`.

#![no_std]
#![forbid(unsafe_op_in_unsafe_fn)]

#[cfg(test)]
extern crate std;

extern crate alloc;

mod engine;
mod node;
mod signal;
mod theme;

pub use engine::{EmitResult, Engine};
pub use node::*;
pub use signal::{Dep, SignalId, SignalStore, SignalValue};
pub use theme::{AdaptiveTheme, Theme};
