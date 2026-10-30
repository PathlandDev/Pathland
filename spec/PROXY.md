# Pathland Proxy Views

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** October 15, 2026

---

## Introduction

A **proxy view** ([`PROXY_VIEW`](./PRIMITIVES.md#proxyview--proxy_view-0x1e) `0x1E`)
lets one Pathland application compose views served by **several** Pathland
servers into a **single unified document**. The application that owns the proxy
node (the **proxying server**, "A") connects to another Pathland server (the
**origin server**, "B") as a Pathland **host** (client), receives B's subtree,
and splices it into its own opcode stream. The end client (a browser or a native
renderer) still talks to **one** server over **one** connection and applies
**one** opcode stream.

This document specifies the proxy contract: node-id namespacing, the snapshot
handshake, SSR embedding, event routing, lifecycle, and flow control. It uses
**only existing protocol surface** — there is no proxy-specific opcode.

> **Scope.** Nested proxies (B itself proxying C) are **not** part of this
> version; the id-range model below generalizes to them, but recursion, depth
> limits, and cycle detection beyond the direct case are deferred.

### Relationship to the other specs

| File | Role |
|------|------|
| [OPCODE.md](./OPCODE.md) | Wire format (categories, commands, 16-byte opcode, PLPL batches, `META::RESYNC`) |
| [PRIMITIVES.md](./PRIMITIVES.md) | The `PROXY_VIEW` component and its layout semantics |
| [DSL.md](./DSL.md) | The `RemoteView` authoring surface |
| [EVENTS.md](./EVENTS.md) | Raw-input events routed across a proxy boundary |
| **PROXY.md** (this file) | The end-to-end proxy contract |

---

## 1. Roles

A proxying server occupies **two protocol roles at once**:

| Peer | A's role | Direction of A's traffic |
|------|----------|--------------------------|
| End client (browser / native renderer) | **guest** (app) | emits `TREE`/`PARAMETER` to the client; receives `EVENT` from the client |
| Origin server (B) | **host** (renderer) | receives `TREE`/`PARAMETER` from B; sends `EVENT` to B; sends `META::RESYNC` to B |

The protocol directions line up without modification: `TREE`/`PARAMETER` are
guest → host, `EVENT` is host → guest, and `META::RESYNC` is host → guest.

The origin server needs **no proxy awareness**: it serves a normal session (SSR
+ `/ws`) and cannot tell a proxying server from an ordinary client.

---

## 2. Node-id namespacing (MUST)

Node ids are per-emitter `u32` values; two independent emitters both start at
`1` and **collide**. A renderer that receives `CREATE_NODE` for an existing id
**merges** onto the existing node. A proxying server therefore MUST remap every
id in B's stream into a **disjoint high range** before emitting it to the client.

### 2.1 Reserved proxy id range

| Property | Value |
|----------|-------|
| Range | `[0x4000_0000, 0x8000_0000)` |
| Block size | `0x0010_0000` (`2^20`, 1,048,576 ids) |
| Blocks | 1024 (one per proxy instance) |

A proxying server MUST NOT allocate its own node ids at or above `0x4000_0000`
(a conforming emitter asserts this).

### 2.2 Remap rule

For proxy instance `k` (0-based) and an origin node id `bId`:

```
base(k) = 0x4000_0000 + k * 0x0010_0000
aId     = base(k) + bId
```

Reverse (for event egress and range tests):

```
isProxyId(id) = 0x4000_0000 <= id < 0x8000_0000
k             = (id - 0x4000_0000) >> 20
bId           = (id - 0x4000_0000) & 0x000F_FFFF
```

### 2.3 Which fields are remapped

Every opcode field carrying a **node id** is remapped:

| Opcode | Remapped fields |
|--------|-----------------|
| `TREE::CREATE_NODE` | `A` (node id) |
| `TREE::DELETE_NODE` | `A` (node id) |
| `TREE::INSERT_CHILD` | `A` (parent), `B` (child) |
| `TREE::REMOVE_CHILD` | `A` (parent), `B` (child) |
| `TREE::MOVE_CHILD` | `A` (parent), `B` (child) |
| `PARAMETER::SET_TEXT`, `SET_PROPERTY`, `SET_DATE` | `A` (node id) |
| `PARAMETER::SET_DESIGN_TOKEN` | — **not remapped**: `A` is a token-path **arena offset**, not a node id |
| `EVENT::*` | `A` (target id) — in the reverse direction (egress) |

`B`/`C` fields that are **not** node ids (component type, property id, value,
index) are left unchanged. `APPEND` (`u32::MAX`) is never a node id and is never
remapped. Only the `PARAMETER` ops above carry a node id in `A`; a remapper MUST
switch on the command, not treat every `PARAMETER` op alike (remapping
`SET_DESIGN_TOKEN`'s offset as an id yields a huge out-of-bounds string offset).

### 2.4 Origin id stability

Origin ids are stable for the lifetime of an origin **session**: mount assigns
`1..N` pre-order, and later structural changes allocate fresh ids monotonically.
The remap is therefore a pure offset for the whole session.

If the origin session is **replaced** (the proxying server reconnects and the
origin hands out a new session whose ids restart at `1`), the proxying server
MUST first emit `DELETE_NODE` for the old remapped subtree (freeing those client
elements), then re-seed the new origin snapshot into the **same block**. It MUST
NOT reuse a block across two live origin sessions.

### 2.5 Block capacity

A block holds `2^20` ids. An origin whose tree exceeds `2^20` live nodes is out
of contract for this version; a proxying server MUST fail the proxy (placeholder
/ error state) rather than wrap or alias ids.

---

## 3. Snapshot handshake (`META::RESYNC`)

There is **no proxy-specific snapshot opcode**. The proxying server obtains the
origin's subtree with the standard host → guest `META::RESYNC`:

1. A opens a connection to B's `/<base>/ws?wid=<A-scoped-wid>` (a WS handshake
   with A's service credentials — see §7).
2. A sends `META::ENVIRONMENT` (viewport + the route B should render).
3. A sends `META::RESYNC`.
4. B replies with a full `TREE + PARAMETER` snapshot of its retained tree.
5. A remaps the snapshot's ids (§2), attaches the origin root under the proxy
   node with `TREE::INSERT_CHILD(proxyId, base(k)+rootId, APPEND)`, and forwards
   it.
6. B then streams live deltas, which A remaps and forwards (§4).

The `wid` A passes to B is A's **session-scoped** window id, so B's persisted
state (`StateStore`) is scoped per end-user window exactly as it is for a direct
client.

### 3.1 SSR (synchronous snapshot)

Server-side rendering runs on a request thread and must produce the **complete**
document — including the origin subtree — in one pass. The proxying server
therefore performs a **blocking** `META::RESYNC` round-trip per proxy
(`requestSnapshot(timeout)`): send `RESYNC`, await B's snapshot batch or time
out. On timeout the proxy renders a placeholder node and the origin subtree
hydrates over the live connection. Blocking is bounded by the timeout and is
safe on the SSR worker thread (one round-trip per page load).

Order of operations at SSR:

1. Mount the proxying app's own tree (yielding the proxy node ids).
2. For each proxy, blocking-`RESYNC` B and remap the snapshot.
3. Render the document with the origin subtrees embedded (§5).

---

## 4. Live deltas

- **Origin → client:** A decodes each batch from B
  (`FrameCodec.decodeFrame`), remaps ids (§2.3), re-encodes it
  (`FrameCodec.encodeFrame`) with B's string section, and sends it over A's
  connection to the client. A re-stamps the batch `sequence` from A's own
  per-connection counter so the client's gap detection sees a single stream.
- **`META::RESET` from B** MUST NOT be forwarded as a global reset — it would
  wipe the client's whole document. A MUST translate it into a scoped teardown
  of that proxy's remapped subtree (`DELETE_NODE` per origin root) and re-seed.
- **Origin resync:** when the end client requests `META::RESYNC` (reconnect,
  reload, sequence gap), A replays its own tree **and** sends B a fresh
  `META::RESYNC`, forwarding the resulting origin snapshot so the proxy region
  is current.

---

## 5. SSR embedding

The web SSR renderer composes the origin subtree into A's document via the
renderer **embeds** API:

```
render_document_with_embeds(mainFrame, root, embeds: [(hostNodeId, frame, root)])
```

For each embed the renderer:

1. inlines the embed's subtree at the host (proxy) node, and
2. merges the embed's **head extras** into the document head — the
   `<style data-pathland-fit>` `@container pf-<id>` band rules.

The origin's **global token overrides** (`SET_DESIGN_TOKEN`) are **dropped**
(§9): a proxied app renders under the **host's** theme, so the embed contributes
fit band rules (whose `pf-<id>` names are remapped and thus disjoint from A's)
but no token rules. A node's token **reference** (a `DESIGN_TOKEN`-typed
`SET_PROPERTY`) is kept — it resolves against the host theme.

Because A remaps the origin ids (§2) **before** rendering, the embed's
`pf-<id>` container names and `[data-pathland-id]` selectors are disjoint from
A's own — no cross-contamination. String handling stays per-frame; there is no
arena rebasing.

The origin's window metadata (`META::SET_WINDOW_METADATA`) is **dropped** with
the other `META` opcodes: an embedded app must never retitle the host window or
overwrite the host's description/locale. Only the top-level app owns the
presentation metadata of the surface.

The client hydrates the composed document by `data-pathland-id` as usual and
applies A's single live stream — it requires **no proxy awareness**.

---

## 6. Event routing

- **Client → origin:** for each inbound raw `EVENT` opcode, if
  `isProxyId(target)` (§2.2), A remaps the target back to `bId`, re-encodes the
  event, and forwards it to the owning origin connection. All other events
  dispatch to A's own input routing as usual.
- **Viewport / fit:** A forwards the client viewport and any `FIT_CHANGED`
  event whose target is a proxy id to B, so B's `SizeThatFits` slots measure
  correctly (B's fit is per-container and reported at runtime).
- **Origin → client:** origin events (e.g. `NAVIGATE`) are forwarded to the
  client with ids remapped. An origin `NAVIGATE` drives the **client's** route
  unless A chooses to intercept it; A's own route signal is unaffected.

---

## 7. Lifecycle, security, and failure

- **Connect:** on mount (SSR: blocking; live: async), with exponential backoff
  on failure.
- **Origin session restart:** tear down the old remapped subtree, then re-seed
  (§2.4).
- **Proxy lifetime = node lifetime (MUST).** A proxy lives **exactly while its
  `PROXY_VIEW` node is in A's retained tree**. A **structural reconcile** that
  removes the node — e.g. a `SIZE_THAT_FITS` slot dropping the candidate that
  held it, a component-slot branch swap — MUST close the origin connection (stop
  forwarding, release the id block). Re-adding the node (a fresh reconcile) MUST
  open a **fresh** origin connection and re-seed it from a `META::RESYNC`. The
  proxying server therefore reconciles its origin connections against the live
  set of `PROXY_VIEW` nodes after every structural reconcile. Released id blocks
  are **reused**, so repeated removal/re-add cycles never exhaust the range.
  (The renderer already removes the origin subtree from the client when the
  proxy node is deleted — a `DELETE_NODE` covers the whole subtree, §5.)
- **Session close:** A closes every origin connection.
- **Origin unreachable:** A renders a placeholder/error node for the proxy and
  retries; the rest of A's document is unaffected.
- **Credentials:** A authenticates to B with **A's own service credentials** (a
  service token / mTLS) presented on the WS handshake; B trusts A as a service.
  The end user's identity is **not** propagated in this version.
- **SSRF:** the origin endpoint is **app-configured**, never client-supplied. A
  MUST reject a client-influenced endpoint.
- **Cycles:** A sends an **instance-id** on the handshake; an origin that sees
  its own instance-id (or a configured denylist hit) refuses the connection, so
  A→B→A misconfiguration cannot loop.
- **Caps:** A caps the number of proxies, the origin batch size, and the origin
  tree size, and drops a proxy that exceeds them.

---

## 8. Backpressure & flow control

There are two links — `origin → A → client` and `client → A → origin`. The
client's link is usually the bottleneck. Because Pathland has **no flow-control
opcode** and A's actor thread MUST NOT block (it is shared with A's own
emission), A bounds its buffers and recovers with the existing gap → resync
path:

- **Origin → client (tree direction):** bound the per-proxy send queue (default
  256 KB / 64 batches). On overflow, discard pending deltas and mark the proxy
  **dirty**; when the socket drains, send B a `META::RESYNC` and forward the
  fresh snapshot. A snapshot supersedes dropped deltas, and the client's
  sequence-gap detection drives its own resync.
- **Client → origin (event direction):** bound the per-proxy queue. **Coalesce**
  continuous events (pointer-move/scroll keep only the latest); **never drop**
  discrete events (click, text-changed, value-changed).
- **Telemetry:** queue depth, drop count, and resync count are reported through
  the host's telemetry seam. A "chatty origin" breaker caps origin inbound
  bytes/sec.

Rationale: Pathland deltas are **idempotent property sets**, so coalescing or
replacing them with a snapshot never corrupts the rendered result.

---

## 9. Known limitations (this version)

- **The origin's global tokens are dropped.** A proxied app renders under the
  **host's** theme: the origin's `SET_DESIGN_TOKEN` overrides are neither
  forwarded in the live stream nor merged into the SSR head, while the origin's
  token **references** resolve against the host theme. Per-proxy theme scoping
  (letting a proxied subtree carry its own token overrides) is deferred.
- **No nested proxies** and **no end-user identity propagation** (§7).
- **Origin tree size** bounded by the `2^20` block (§2.5).

---

## 10. Conformance

- `TREE::CREATE_NODE` with `PROXY_VIEW` (`0x1E`) has a golden vector in
  [CONFORMANCE.md](./CONFORMANCE.md).
- Remapping (§2.2) is a pure offset and is unit-testable without a network: for
  any origin opcode, remap then unmap is the identity, and no remapped id ever
  falls below `0x4000_0000`.
- The proxy path MUST reuse the existing `FrameCodec`/PLPL codec and the
  `META::RESYNC` handshake; a conforming implementation introduces **no new
  opcode**.
