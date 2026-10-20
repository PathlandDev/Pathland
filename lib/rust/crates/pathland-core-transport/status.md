# pathland-core-transport — implementation status

**Last updated:** September 3, 2026

The **transport** layer: the shared-memory ring owner and the network batch
codec (both directions). Protocol contract: `spec/OPCODE.md` (Transport).

## Implemented

- **`RingTransport`**: zero-copy SPSC shared-memory transport over the
  `pathland-core` linear memory — `next_frame` (guest→host frames as
  `OpcodeBatch`) and `send_input`/`drain_events` (host→guest raw inputs,
  including shared-ring `TEXT_CHANGED` text via the host event arena).
- **Network batch codec** (`batch.rs`, `net.rs`): `encode_batch`/`decode_batch`
  for guest→host frames and `encode_events`/`decode_events` for host→guest
  event batches (`HOST_TO_GUEST` direction flag, `TEXT_CHANGED` text in the
  batch string section, `Navigate` URLs in the string section, plus all typed
  draft events — focus/edit/submit/scroll/wheel/date-changed). Both directions
  are fully wired at the codec level.
- **Message sequence** (`sequence`, header offset 8): the batch header carries a
  per-stream monotonic **message sequence** (the network transport's loss
  detector; `Batch::sequence()`). The `Batcher` owns the counter and increments
  it once per flushed batch; heartbeat/request batches carry `0`. Consumers
  detect a gap and recover with a full snapshot (spec/OPCODE.md §Sequence gap
  detection).
- **Batching policy** (`batching.rs`): time/size flush thresholds with arena
  delta tracking.
- **Conformance vectors 13/14/19** (`conformance.rs`): golden network-batch
  bytes — incl. **vector 19** (a `DESIGN_TOKEN`-typed `SET_PROPERTY` with the
  `"color.primary"` arena delta).

## Not implemented / gaps

- No out-of-the-box duplex *server transport* (WebSocket/session plumbing) in
  this crate — the codecs exist; the Java demos (Quarkus/Spring) wire the
  browser↔server duplex.

## Verified by

`cargo test -p pathland-core-transport` — ring round-trips (incl. zero-copy),
batch encode/decode both directions, batching policy, conformance vectors.