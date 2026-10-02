import { beforeEach, describe, expect, it, vi } from "vitest";
import { Transport } from "../src/transport";
import {
  CAT_META,
  CAT_PARAMETER,
  CMD_PING,
  CMD_PONG,
  CMD_SET_TEXT,
  HEADER_SIZE,
  MAGIC,
  OPCODE_SIZE,
  VERSION,
} from "../src/constants";
import { parseBatch } from "../src/plpl";
import type { DomRenderer } from "../src/apply";

/** Minimal fake WebSocket for transport tests (happy-dom has no WebSocket global). */
class FakeSocket {
  static instances: FakeSocket[] = [];
  readyState: number = 0; // CONNECTING
  binaryType = "";
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readonly sent: Uint8Array[] = [];
  constructor(readonly url: string) {
    FakeSocket.instances.push(this);
  }
  send(data: Uint8Array): void {
    this.sent.push(data);
  }
  close(): void {
    this.readyState = 3; // CLOSED
    this.onclose?.();
  }
  emitOpen(): void {
    this.readyState = 1; // OPEN
    this.onopen?.();
  }
  emitMessage(data: unknown): void {
    this.onmessage?.({ data });
  }
}

function fakeFactory(url: string): WebSocket {
  return new FakeSocket(url) as unknown as WebSocket;
}

function applyBatchHeader(batch: Uint8Array, opcodeCount: number): void {
  const view = new DataView(batch.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0, true);
  view.setUint32(12, opcodeCount, true);
}

/** A valid host → guest batch carrying a single META::PONG opcode. */
function pongBatch(): Uint8Array {
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0x0001, true); // HOST_TO_GUEST
  view.setUint32(12, 1, true);
  view.setUint8(HEADER_SIZE, CAT_META);
  view.setUint8(HEADER_SIZE + 1, CMD_PONG);
  return out;
}

describe("Transport", () => {
  beforeEach(() => {
    FakeSocket.instances = [];
  });

  it("applies batches received over the socket", () => {
    const renderer: DomRenderer = { byId: new Map() };
    const span = document.createElement("span");
    renderer.byId.set(1, span);
    const transport = new Transport({ url: "ws://host/ws", renderer, createSocket: fakeFactory });
    transport.start();
    const socket = FakeSocket.instances[0]!;
    socket.emitOpen();

    const batch = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4);
    applyBatchHeader(batch, 1);
    const view = new DataView(batch.buffer);
    view.setUint8(HEADER_SIZE, CAT_PARAMETER);
    view.setUint8(HEADER_SIZE + 1, CMD_SET_TEXT);
    view.setUint32(HEADER_SIZE + 4, 1, true);
    view.setUint32(HEADER_SIZE + 8, 0, true);
    socket.emitMessage(batch);
    expect(span.textContent).toBe("");

    transport.stop();
  });

  it("ignores malformed batches without throwing", () => {
    const transport = new Transport({
      url: "ws://host/ws",
      renderer: { byId: new Map() },
      createSocket: fakeFactory,
    });
    transport.start();
    const socket = FakeSocket.instances[0]!;
    expect(() => socket.emitMessage(new Uint8Array(4))).not.toThrow();
    transport.stop();
  });

  it("reconnects after the socket closes", async () => {
    const transport = new Transport({
      url: "ws://host/ws",
      renderer: { byId: new Map() },
      createSocket: fakeFactory,
    });
    transport.start();
    expect(FakeSocket.instances).toHaveLength(1);
    FakeSocket.instances[0]!.close();
    await new Promise((resolve) => setTimeout(resolve, 700));
    expect(FakeSocket.instances.length).toBeGreaterThanOrEqual(2);
    transport.stop();
  });

  it("requests a resync (META::RESYNC) on reconnect but not on first connect", async () => {
    const transport = new Transport({
      url: "ws://host/ws",
      renderer: { byId: new Map() },
      createSocket: fakeFactory,
    });
    transport.start();
    FakeSocket.instances[0]!.emitOpen();
    expect(FakeSocket.instances[0]!.sent).toHaveLength(0); // no resync on first connect

    FakeSocket.instances[0]!.close();
    await new Promise((resolve) => setTimeout(resolve, 700));
    const reconnected = FakeSocket.instances[FakeSocket.instances.length - 1]!;
    reconnected.emitOpen();
    expect(reconnected.sent).toHaveLength(1);
    const op = parseBatch(reconnected.sent[0]!).opcodes[0]!;
    expect(op.category).toBe(0x04); // META
    expect(op.command).toBe(0x03); // RESYNC
    transport.stop();
  });

  it("reports open status and sends encoded bytes", () => {
    const statuses: string[] = [];
    const renderer: DomRenderer = { byId: new Map() };
    const transport = new Transport({
      url: "ws://host/ws",
      renderer,
      createSocket: fakeFactory,
      onStatus: (status) => statuses.push(status),
    });
    transport.start();
    FakeSocket.instances[0]!.emitOpen();
    expect(statuses).toEqual(["open"]);
    transport.send(new Uint8Array([1, 2, 3]));
    expect(FakeSocket.instances[0]!.sent).toHaveLength(1);
    transport.stop();
  });

  describe("heartbeat watchdog", () => {
    it("sends a META::PING heartbeat on the ping cadence", () => {
      vi.useFakeTimers();
      try {
        const transport = new Transport({
          url: "ws://host/ws",
          renderer: { byId: new Map() },
          createSocket: fakeFactory,
        });
        transport.start();
        FakeSocket.instances[0]!.emitOpen();
        vi.advanceTimersByTime(5000);
        expect(FakeSocket.instances[0]!.sent).toHaveLength(1);
        const op = parseBatch(FakeSocket.instances[0]!.sent[0]!).opcodes[0]!;
        expect(op.category).toBe(CAT_META);
        expect(op.command).toBe(CMD_PING);
        vi.advanceTimersByTime(5000);
        expect(FakeSocket.instances[0]!.sent).toHaveLength(2);
        transport.stop();
      } finally {
        vi.useRealTimers();
      }
    });

    it("forces a reconnect when no server frame arrives for the watchdog timeout", () => {
      vi.useFakeTimers();
      try {
        const transport = new Transport({
          url: "ws://host/ws",
          renderer: { byId: new Map() },
          createSocket: fakeFactory,
        });
        transport.start();
        expect(FakeSocket.instances).toHaveLength(1);
        FakeSocket.instances[0]!.emitOpen();
        vi.advanceTimersByTime(16000); // past the 15 s no-activity timeout
        vi.advanceTimersByTime(700); // first reconnect backoff (500 ms)
        expect(FakeSocket.instances.length).toBeGreaterThanOrEqual(2);
        transport.stop();
      } finally {
        vi.useRealTimers();
      }
    });

    it("does not reconnect while server frames keep arriving (PONG resets the watchdog)", () => {
      vi.useFakeTimers();
      try {
        const transport = new Transport({
          url: "ws://host/ws",
          renderer: { byId: new Map() },
          createSocket: fakeFactory,
        });
        transport.start();
        const socket = FakeSocket.instances[0]!;
        socket.emitOpen();
        for (let t = 5000; t <= 30000; t += 5000) {
          vi.advanceTimersByTime(5000);
          socket.emitMessage(pongBatch().buffer); // the browser delivers ArrayBuffer
        }
        expect(FakeSocket.instances).toHaveLength(1);
        transport.stop();
      } finally {
        vi.useRealTimers();
      }
    });
  });
});