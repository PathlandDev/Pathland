// WebSocket transport: connects to the server's reserved `/_pathland/ws`,
// applies each received PLPL batch to the DOM renderer, negotiates the protocol
// version, and reports raw-input events back. Reconnect/backoff, heartbeat, and
// sequence-gap recovery (lost batches without a disconnect → META::RESYNC) live
// here.

import type { Batch } from "./plpl";
import { ProtocolError, parseBatch } from "./plpl";
import { applyBatch, type DomRenderer } from "./apply";
import { encodePing, encodeResync } from "./events";
import { FLAG_HOST_TO_GUEST, VERSION } from "./constants";
import { log } from "./log";
import { describeBatch, describeBatchDetail } from "./describe";

export type TransportStatus = "connecting" | "open" | "reconnecting";

export interface TransportOptions {
  /** WebSocket URL, e.g. `ws://host/_pathland/ws`. */
  url: string;
  /** The retained-node registry deltas are applied to. */
  renderer: DomRenderer;
  /** Invoked after a batch is successfully applied. */
  onBatch?: (batch: Batch) => void;
  onStatus?: (status: TransportStatus) => void;
  /**
   * Invoked on every socket open (first connect and reconnect), before any RESYNC —
   * the DOM client sends its `META::ENVIRONMENT` (viewport + route) here as the first
   * message so the server session seeds from it.
   */
  onOpen?: (transport: Transport) => void;
  /** Injectable socket factory (tests). */
  createSocket?: (url: string) => WebSocket;
}

const BASE_DELAY_MS = 500;
const MAX_DELAY_MS = 30000;
const MAX_ATTEMPTS = 30;

// Heartbeat (spec/OPCODE.md §Transport heartbeat): the client probes server
// liveness with META::PING every HEARTBEAT_INTERVAL_MS. It treats the connection
// as dead when NO server batch of any kind (deltas, META::PONG) has arrived for
// HEARTBEAT_TIMEOUT_MS — a stalled-but-"open" socket never fires onclose, so the
// watchdog forces a close to trigger the reconnect flow (which re-sends the
// environment + requests a META::RESYNC). The PING also counts as inbound WS
// traffic, keeping the connection warm through proxies and idle-spin-down.
const HEARTBEAT_INTERVAL_MS = 5000;
const HEARTBEAT_TIMEOUT_MS = 15000;
const WATCHDOG_CHECK_MS = 1000;

// Numeric readyState (the WebSocket global may be absent in test environments).
const WS_OPEN = 1;

export class Transport {
  private ws: WebSocket | null = null;
  private attempt = 0;
  private stopped = false;
  private readonly options: TransportOptions;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private watchdogTimer: ReturnType<typeof setInterval> | null = null;
  private lastActivityAt = 0;
  /**
   * The sequence the next guest → host delta batch must carry, or `null` before
   * the first batch / after a gap or reconnect (re-baseline on the next batch).
   * See spec/OPCODE.md §Sequence gap detection.
   */
  private expectedSequence: number | null = null;

  constructor(options: TransportOptions) {
    this.options = options;
  }

  /** Connect and stay connected (auto-reconnect with exponential backoff). */
  start(): void {
    this.stopped = false;
    this.lastActivityAt = Date.now();
    this.startHeartbeat();
    this.connect();
  }

  /** Permanently close the socket and cancel reconnects. */
  stop(): void {
    this.stopped = true;
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close();
      this.ws = null;
    }
  }

  get open(): boolean {
    return this.ws !== null && this.ws.readyState === WS_OPEN;
  }

  send(bytes: Uint8Array): void {
    if (!this.open) {
      return;
    }
    // Best-effort description (never drops the bytes, even if undecodable).
    try {
      const batch = parseBatch(bytes);
      log.info("ws", "→ send", describeBatch(batch));
      log.debug("ws", "→ send detail", describeBatchDetail(batch));
    } catch {
      log.info("ws", "→ send", bytes.length, "bytes");
    }
    this.ws!.send(bytes);
  }

  /** Start the META::PING sender + the no-activity watchdog (one-shot, survives reconnects). */
  private startHeartbeat(): void {
    if (this.pingTimer || this.watchdogTimer) {
      return;
    }
    this.pingTimer = setInterval(() => {
      if (this.open) {
        this.send(encodePing());
      }
    }, HEARTBEAT_INTERVAL_MS);
    this.watchdogTimer = setInterval(() => {
      if (!this.open) {
        return;
      }
      if (Date.now() - this.lastActivityAt > HEARTBEAT_TIMEOUT_MS) {
        log.warn("ws", `no server frame for ${HEARTBEAT_TIMEOUT_MS} ms — reconnecting`);
        this.ws?.close();
      }
    }, WATCHDOG_CHECK_MS);
  }

  private stopHeartbeat(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
    if (this.watchdogTimer) {
      clearInterval(this.watchdogTimer);
      this.watchdogTimer = null;
    }
  }

  private connect(): void {
    log.debug("ws", "connecting", this.options.url);
    const ws = this.options.createSocket
      ? this.options.createSocket(this.options.url)
      : new WebSocket(this.options.url);
    this.ws = ws;
    ws.binaryType = "arraybuffer";
    ws.onopen = () => {
      // A reconnect means the client missed deltas while disconnected: request a
      // full snapshot (META::RESYNC). The first connect never does — the client
      // already has the whole UI from the SSR HTML. On EVERY open (before any
      // resync) the client sends its environment (viewport + route).
      this.lastActivityAt = Date.now();
      // Re-baseline the sequence on the next guest → host batch (a fresh
      // connection's stream restarts; see §Sequence gap detection).
      this.expectedSequence = null;
      const reconnected = this.attempt > 0;
      this.attempt = 0;
      log.info("ws", reconnected ? "connected (reconnect)" : "connected");
      this.options.onStatus?.("open");
      this.options.onOpen?.(this);
      if (reconnected) {
        log.debug("ws", "requesting RESYNC (missed deltas while disconnected)");
        this.send(encodeResync());
      }
    };
    ws.onmessage = (event) => this.handleMessage(event.data);
    ws.onerror = () => {
      log.error("ws", "socket error");
      // onclose always follows; nothing to do here.
    };
    ws.onclose = () => {
      this.ws = null;
      if (this.stopped) {
        log.debug("ws", "closed (stopped)");
        return;
      }
      log.info("ws", "closed — reconnecting (attempt", this.attempt + 1 + ")");
      this.options.onStatus?.("reconnecting");
      this.scheduleReconnect();
    };
  }

  private scheduleReconnect(): void {
    if (this.attempt >= MAX_ATTEMPTS) {
      return;
    }
    const delay = Math.min(BASE_DELAY_MS * 2 ** this.attempt, MAX_DELAY_MS);
    this.attempt++;
    setTimeout(() => this.connect(), delay);
  }

  private handleMessage(data: unknown): void {
    // Any server batch (a delta, a META::PONG, anything) proves the connection
    // is alive — reset the heartbeat watchdog.
    this.lastActivityAt = Date.now();
    let bytes: Uint8Array;
    if (data instanceof ArrayBuffer) {
      bytes = new Uint8Array(data);
    } else if (data instanceof Blob) {
      void data.arrayBuffer().then((buffer) => this.handleMessage(buffer));
      return;
    } else {
      bytes = new Uint8Array(0);
    }
    let batch: Batch;
    try {
      batch = parseBatch(bytes);
    } catch (err) {
      if (err instanceof ProtocolError) {
        log.error("ws", "rejected batch:", err.message);
        return;
      }
      throw err;
    }
    log.info("ws", "← recv", describeBatch(batch));
    log.debug("ws", "← recv detail", describeBatchDetail(batch));
    if (batch.version !== VERSION) {
      log.warn("ws", `protocol version ${batch.version} != ${VERSION}; reloading`);
      location.reload();
      return;
    }
    // Sequence gap detection (spec/OPCODE.md §Sequence gap detection): a
    // guest → host delta batch whose sequence breaks the chain means one or more
    // batches were lost while the socket stayed open. Do not apply the gapped
    // batch (its deltas assume the lost state); request a full snapshot and
    // re-baseline on the next batch.
    if (this.isSequenceGap(batch)) {
      log.warn(
        "ws",
        `sequence gap: expected ${this.expectedSequence}, got ${batch.sequence} — requesting RESYNC`,
      );
      this.expectedSequence = null;
      this.send(encodeResync());
      return;
    }
    this.trackSequence(batch);
    try {
      applyBatch(batch, this.options.renderer);
    } catch (err) {
      if (err instanceof ProtocolError) {
        log.error("ws", "rejected delta:", err.message);
        return;
      }
      throw err;
    }
    this.options.onBatch?.(batch);
  }

  /**
   * True when a guest → host delta batch breaks the expected sequence chain.
   * Host → guest batches (heartbeat `PONG`, request echoes) carry sequence 0 and
   * are not part of the guest → host stream, so they are ignored here.
   */
  private isSequenceGap(batch: Batch): boolean {
    if ((batch.flags & FLAG_HOST_TO_GUEST) !== 0) {
      return false;
    }
    return this.expectedSequence !== null && batch.sequence !== this.expectedSequence;
  }

  /** Advance the expected sequence from a guest → host delta batch (baseline or next). */
  private trackSequence(batch: Batch): void {
    if ((batch.flags & FLAG_HOST_TO_GUEST) !== 0) {
      return;
    }
    this.expectedSequence = batch.sequence + 1;
  }
}