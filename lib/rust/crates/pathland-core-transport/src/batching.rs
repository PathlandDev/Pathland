//! Batching policy and buffering for network transport.
//!
//! `Batcher` accumulates frames against a [`BatchPolicy`] and flushes a single
//! serialized batch when either the time interval since the first buffered
//! opcode elapses **or** the accumulated byte size reaches the threshold —
//! first trigger wins.

use alloc::vec::Vec;
use std::time::{Duration, Instant};

use pathland_core::Opcode;

use crate::batch::encode_batch;
use crate::direction::GUEST_TO_HOST;

/// Default time interval before a batch is flushed (4 ms).
pub const DEFAULT_MAX_INTERVAL: Duration = Duration::from_millis(4);
/// Default max batch byte size (conservative network frame / MTU).
pub const DEFAULT_MAX_BATCH_BYTES: usize = 1400;

/// Flush thresholds for network batching.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BatchPolicy {
    /// Flush when this much time has elapsed since the first buffered opcode.
    pub max_interval: Duration,
    /// Flush when the accumulated batch reaches this many bytes.
    pub max_batch_bytes: usize,
}

impl BatchPolicy {
    /// Create a policy from a time interval and a max batch size.
    pub const fn new(max_interval: Duration, max_batch_bytes: usize) -> Self {
        Self {
            max_interval,
            max_batch_bytes,
        }
    }

    /// The default policy: 4 ms interval, 1400-byte batches.
    pub const fn default_policy() -> Self {
        Self::new(DEFAULT_MAX_INTERVAL, DEFAULT_MAX_BATCH_BYTES)
    }
}

impl Default for BatchPolicy {
    fn default() -> Self {
        Self::default_policy()
    }
}

/// Accumulates frames into a batch and flushes per [`BatchPolicy`].
///
/// The arena is append-only; the batcher ships the bytes appended since the
/// last flush (tracked by arena length). If the arena shrank (a `META::RESET`),
/// the pending arena is reset so the next flush starts fresh.
#[derive(Debug)]
pub struct Batcher {
    policy: BatchPolicy,
    /// Sequence number assigned to the next flushed batch.
    next_sequence: u32,
    /// Opcodes buffered since the last flush.
    opcodes: Vec<Opcode>,
    /// Arena bytes appended since the last flush.
    pending_arena: Vec<u8>,
    /// Arena length at the last flush.
    last_arena_len: usize,
    /// When the first opcode of the current batch was buffered.
    started: Option<Instant>,
    /// Size trigger latched on push.
    size_triggered: bool,
}

impl Batcher {
    /// Create a batcher with the given policy.
    pub fn new(policy: BatchPolicy) -> Self {
        Self {
            policy,
            next_sequence: 1,
            opcodes: Vec::new(),
            pending_arena: Vec::new(),
            last_arena_len: 0,
            started: None,
            size_triggered: false,
        }
    }

    /// Create a batcher with the default policy (4 ms / 1400 B).
    pub fn default_policy() -> Self {
        Self::new(BatchPolicy::default())
    }

    /// Whether no opcodes are currently buffered.
    pub fn is_empty(&self) -> bool {
        self.opcodes.is_empty()
    }

    /// Number of opcodes buffered since the last flush (diagnostics).
    pub fn pending_opcodes(&self) -> usize {
        self.opcodes.len()
    }

    /// Approximate bytes that would be shipped on flush (diagnostics).
    pub fn pending_bytes(&self) -> usize {
        self.opcodes.len() * Opcode::SIZE + self.pending_arena.len() + crate::batch::BATCH_HEADER + 4
    }

    /// Record a frame (opcodes + the arena's used portion `[0..cursor)`),
    /// accumulating the arena delta. Returns `true` if the size trigger fired.
    ///
    /// The batch's sequence number is assigned at [`flush`](Self::flush), not
    /// here: a batch is the network unit, and several frames may coalesce into
    /// one.
    pub fn push_frame(&mut self, opcodes: &[Opcode], arena_used: &[u8]) -> bool {
        if self.started.is_none() {
            self.started = Some(Instant::now());
        }
        if arena_used.len() < self.last_arena_len {
            // Arena was reset; restart the pending buffer.
            self.pending_arena.clear();
            self.last_arena_len = 0;
        }
        self.pending_arena.extend_from_slice(&arena_used[self.last_arena_len..]);
        self.last_arena_len = arena_used.len();
        self.opcodes.extend_from_slice(opcodes);

        self.size_triggered |= self.pending_bytes() >= self.policy.max_batch_bytes;
        self.size_triggered
    }

    /// Whether the time interval has elapsed since the first buffered opcode.
    pub fn interval_elapsed(&self) -> bool {
        match self.started {
            Some(t) => t.elapsed() >= self.policy.max_interval,
            None => false,
        }
    }

    /// Serialize and reset the current buffer, returning the batch bytes.
    ///
    /// Flushes when `force` is true or either policy trigger fired. Returns
    /// `None` when nothing is buffered.
    pub fn flush(&mut self, force: bool) -> Option<Vec<u8>> {
        if self.opcodes.is_empty() {
            return None;
        }
        let should_flush = force || self.size_triggered || self.interval_elapsed();
        if !should_flush {
            return None;
        }
        let bytes = encode_batch(
            self.next_sequence,
            GUEST_TO_HOST,
            &self.opcodes,
            &self.pending_arena,
        );
        self.next_sequence = self.next_sequence.wrapping_add(1);
        self.opcodes.clear();
        self.pending_arena.clear();
        self.last_arena_len = 0;
        self.started = None;
        self.size_triggered = false;
        Some(bytes)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::{category, Opcode};

    fn op(n: u32) -> Opcode {
        Opcode::new(category::TREE, 0x01, 0, n, 0x0010, 0)
    }

    fn empty_arena() -> &'static [u8] {
        &[]
    }

    #[test]
    fn size_trigger_flushes() {
        // Tiny batch size: one opcode (16 B) exceeds it.
        let policy = BatchPolicy::new(Duration::from_secs(60), 8);
        let mut batcher = Batcher::new(policy);
        batcher.push_frame(&[op(1)], empty_arena());
        assert!(batcher.size_triggered);
        let bytes = batcher.flush(false).expect("size trigger should flush");
        assert!(!bytes.is_empty());
        assert!(batcher.is_empty());
    }

    #[test]
    fn no_flush_when_under_policy() {
        let policy = BatchPolicy::new(Duration::from_secs(60), 10_000);
        let mut batcher = Batcher::new(policy);
        batcher.push_frame(&[op(1)], empty_arena());
        assert!(!batcher.size_triggered);
        assert!(batcher.flush(false).is_none());
        // Force flush drains it.
        let bytes = batcher.flush(true).expect("force flush");
        assert!(!bytes.is_empty());
    }

    #[test]
    fn time_trigger_flushes() {
        let policy = BatchPolicy::new(Duration::from_millis(1), 10_000);
        let mut batcher = Batcher::new(policy);
        batcher.push_frame(&[op(1)], empty_arena());
        std::thread::sleep(Duration::from_millis(5));
        assert!(batcher.interval_elapsed());
        let bytes = batcher.flush(false).expect("time trigger should flush");
        assert!(!bytes.is_empty());
    }

    #[test]
    fn flush_empty_returns_none() {
        let mut batcher = Batcher::new(BatchPolicy::default());
        assert!(batcher.flush(true).is_none());
    }

    #[test]
    fn multiple_frames_coalesce_into_one_batch() {
        let policy = BatchPolicy::new(Duration::from_secs(60), 10_000);
        let mut batcher = Batcher::new(policy);
        batcher.push_frame(&[op(1)], empty_arena());
        batcher.push_frame(&[op(2), op(3)], empty_arena());
        assert_eq!(batcher.pending_opcodes(), 3);
        let bytes = batcher.flush(true).unwrap();
        // Header(16) + 3 opcodes(48) + arenaLen(4) + 0 arena.
        assert_eq!(bytes.len(), 16 + 48 + 4);
        assert!(batcher.is_empty());
    }

    #[test]
    fn flushed_batches_get_incrementing_sequences() {
        let policy = BatchPolicy::new(Duration::from_secs(60), 10_000);
        let mut batcher = Batcher::new(policy);
        batcher.push_frame(&[op(1)], empty_arena());
        let first = batcher.flush(true).unwrap();
        batcher.push_frame(&[op(2)], empty_arena());
        let second = batcher.flush(true).unwrap();
        // An empty flush must not consume a sequence number.
        assert!(batcher.flush(true).is_none());

        let mut decoder = crate::batch::BatchDecoder::new();
        assert_eq!(decoder.decode(&first).unwrap().sequence(), 1);
        assert_eq!(decoder.decode(&second).unwrap().sequence(), 2);
    }
}
