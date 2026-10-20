//! # pathland-core-transport
//!
//! How Pathland opcodes are transported from the guest engine to host
//! renderers. There are two peer backends:
//!
//! - **Shared memory** (desktop/native/WASM): the ring buffer + arena in
//!   `pathland-core` (`Guest`/`Host`). Zero-copy. This crate treats it as
//!   the local transport — no duplication.
//! - **Network batch** (SSR → browser): opcodes + string data serialized into
//!   **batches**.
//!
//! There are two network codecs, for two different relationships between the
//! producer and the consumer:
//!
//! - **Self-contained frames** ([`encode_frame`] / [`decode_frame`]) — each
//!   batch carries its own string section and `SET_TEXT` references strings by
//!   a *relative* offset into that section. The consumer needs **no prior
//!   state**: every message is independent. This is the streaming codec used
//!   for live deltas over WebSocket.
//! - **Arena-delta batches** ([`encode_batch`] / [`BatchEncoder`] /
//!   [`BatchDecoder`]) — the batch carries only the bytes *appended* to the
//!   arena since the previous batch, and `SET_TEXT` references absolute arena
//!   offsets. The consumer mirrors the arena so those offsets stay valid. This
//!   mirrors the shared-memory arena over a wire and is the ring-compatible
//!   codec (kept for a future WASM/embedded guest).
//!
//! ## Batch wire format (both codecs share the header)
//!
//! ```text
//! magic "PLPL" (u32 LE) | version (u16) | flags (u16) | sequence (u32)
//! | opcodeCount (u32) | opcodes (16 B × opcodeCount) | stringsLen (u32)
//! | string bytes (length-prefixed entries: [u32 len][bytes])
//! ```
//!
//! `sequence` is the per-stream monotonic message sequence (incremented once
//! per encoded guest → host batch; `0` for heartbeat/request batches). A
//! consumer detects lost batches by a sequence gap and recovers with a full
//! snapshot — see `spec/OPCODE.md` §Sequence gap detection.
//!
//! For a self-contained frame the trailing section is that frame's own string
//! table; for an arena-delta batch it is the arena bytes appended since the
//! last batch.
//!
//! ## Example (producer)
//!
//! ```no_run
//! use pathland_core_transport::{Batcher, BatchPolicy, BatchEncoder};
//! use std::time::Duration;
//!
//! let policy = BatchPolicy::new(Duration::from_millis(4), 1400);
//! let mut batcher = Batcher::new(policy);
//! let mut encoder = BatchEncoder::new();
//!
//! // batcher.push_frame(&frame) accumulates opcodes + arena delta;
//! // batcher.flush() -> Option<Vec<u8>> produces the serialized batch.
//! ```

#![forbid(unsafe_op_in_unsafe_fn)]

extern crate alloc;

mod batch;
mod batching;
mod conformance;
mod net;
mod ring;
mod traits;

pub use batch::{
    decode_events, decode_frame, encode_batch, encode_events, encode_frame, encode_resync, frame_from_slices,
    Batch, BatchDecoder, BatchEncoder,
};
pub use batching::{Batcher, BatchPolicy};
pub use net::NetBatchEncoder;
pub use ring::RingTransport;
pub use traits::{
    DriverTransport, FrameSource, InputSink, OpcodeBatch, ProtocolError, TransportError,
};

/// Wire magic for network batches (`PLPL`).
pub const BATCH_MAGIC: u32 = 0x504C_504C;
/// Wire version for the batch format.
pub const BATCH_VERSION: u16 = 1;

/// Direction of a batch. Events are not yet implemented; this bit is reserved.
pub mod direction {
    /// The batch carries guest → host tree/style opcodes.
    pub const GUEST_TO_HOST: u16 = 0x0000;
    /// The batch carries host → guest raw-input events (reserved).
    pub const HOST_TO_GUEST: u16 = 0x0001;
}
