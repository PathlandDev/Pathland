import { readFileSync } from "node:fs";
import { createServer, type Server } from "node:http";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { WebSocket, type RawData } from "ws";
import {
  BatchWriter,
  CAT_EVENT,
  CAT_META,
  CMD_ENVIRONMENT,
  CMD_PING,
  CMD_RESYNC,
  CMD_SET_TEXT,
  CMD_TEXT_CHANGED,
  ENV_ROUTE,
  ENV_VIEWPORT_HEIGHT,
  ENV_VIEWPORT_WIDTH,
  OPCODE_SIZE,
  f32bits,
  parseBatch,
  readString,
  type Batch,
} from "@pathland/protocol";
import { loadSsrRenderer } from "@pathland/ssr";
import { signal, type WritableSignal } from "@pathland/signals";
import { MemoryStateStore, State } from "@pathland/state";
import type { AppContext, ViewElement } from "@pathland/view";
import { TextField } from "@pathland/view/primitives";
import { PathlandRegistry } from "../src/registry";
import { DeltaBatcher } from "../src/batcher";
import { attachPathlandWebSocket } from "../src/ws";
import type { PathlandApp } from "../src/app";
import type { PathlandConnection } from "../src/connection";

class QueryApp implements PathlandApp {
  private query = new State<string>("");

  newRoot(context: AppContext): ViewElement {
    context.state.connect(this);
    return TextField({ placeholder: "query", text: this.query.signal });
  }

  get value(): WritableSignal<string> {
    return this.query.signal;
  }
}

async function renderer() {
  const wasm = fileURLToPath(new URL("../../ssr/wasm/pathland_render_html.wasm", import.meta.url));
  return loadSsrRenderer(readFileSync(wasm));
}

function environmentBatch(route: string): Uint8Array {
  const writer = new BatchWriter();
  writer.begin();
  writer.push(CAT_META, CMD_ENVIRONMENT, 0, ENV_VIEWPORT_WIDTH, f32bits(800), 0);
  writer.push(CAT_META, CMD_ENVIRONMENT, 0, ENV_VIEWPORT_HEIGHT, f32bits(600), 0);
  const offset = writer.string(route);
  writer.push(CAT_META, CMD_ENVIRONMENT, 0, ENV_ROUTE, offset, 0);
  return writer.end();
}

function textChangedBatch(target: number, text: string): Uint8Array {
  const writer = new BatchWriter();
  writer.begin();
  const offset = writer.string(text);
  writer.push(CAT_EVENT, CMD_TEXT_CHANGED, 0, target, offset, 0);
  return writer.end();
}

function controlBatch(command: number): Uint8Array {
  const writer = new BatchWriter();
  writer.begin();
  writer.push(CAT_META, command, 0, 0, 0, 0);
  return writer.end();
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function nextBatch(ws: WebSocket, predicate: (batch: Batch) => boolean): Promise<Batch> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.off("message", handler);
      reject(new Error("timed out waiting for a server batch"));
    }, 3000);
    const handler = (data: RawData) => {
      const bytes = new Uint8Array(data as Buffer);
      let batch: Batch;
      try {
        batch = parseBatch(bytes);
      } catch {
        return;
      }
      if (!predicate(batch)) return;
      clearTimeout(timer);
      ws.off("message", handler);
      resolve(batch);
    };
    ws.on("message", handler);
  });
}

describe("@pathland/server", () => {
  let server: Server | undefined;
  let sockets: WebSocket[] = [];

  beforeEach(() => {
    sockets = [];
  });

  afterEach(async () => {
    for (const ws of sockets) ws.close();
    if (server) await new Promise<void>((resolve) => server!.close(() => resolve()));
    server = undefined;
  });

  it("renders SSR through the Rust wasm renderer", async () => {
    const registry = new PathlandRegistry({
      app: new QueryApp(),
      store: new MemoryStateStore(),
      ssrRenderer: await renderer(),
      assetDir: undefined,
    });
    const html = await registry.renderHtml("/");
    expect(html).toContain("<!DOCTYPE html>");
    expect(html).toContain('data-pathland-base="/_pathland"');
    expect(html).toContain("dom-renderer.js");
  });

  it("creates a live session from the environment, routes text, resyncs, and answers PING", async () => {
    const registry = new PathlandRegistry({
      app: new QueryApp(),
      store: new MemoryStateStore(),
      ssrRenderer: await renderer(),
    });
    server = createServer();
    attachPathlandWebSocket(server, registry);
    await new Promise<void>((resolve) => server!.listen(0, "127.0.0.1", () => resolve()));
    const address = server.address();
    if (!address || typeof address === "string") throw new Error("no port");
    const ws = new WebSocket(`ws://127.0.0.1:${address.port}/_pathland/ws?wid=w1`);
    sockets.push(ws);
    await new Promise<void>((resolve) => ws.once("open", () => resolve()));

    ws.send(environmentBatch("/"));
    await sleep(80);

    const deltaPromise = nextBatch(ws, (batch) => batch.opcodes.some((op) => op.command === CMD_SET_TEXT));
    ws.send(textChangedBatch(1, "ali"));
    const delta = await deltaPromise;
    expect(delta.sequence).toBe(1);
    expect(delta.opcodes).toHaveLength(1);
    expect(delta.opcodes[0]!.command).toBe(CMD_SET_TEXT);
    expect(readString(delta.strings, delta.opcodes[0]!.b)).toBe("ali");

    const pongPromise = nextBatch(ws, (batch) =>
      batch.opcodes.some((op) => op.category === CAT_META && op.command === 0x05),
    );
    ws.send(controlBatch(CMD_PING));
    expect((await pongPromise).opcodes[0]!.command).toBe(0x05);

    const resyncPromise = nextBatch(ws, (batch) => batch.opcodes.length >= 3);
    ws.send(controlBatch(CMD_RESYNC));
    const snapshot = await resyncPromise;
    expect(snapshot.opcodes.length).toBeGreaterThanOrEqual(3);
    expect(snapshot.sequence).toBeGreaterThan(delta.sequence);
  });

  it("loads persisted state before a live session mounts", async () => {
    const store = new MemoryStateStore();
    store.save("/w1", "query", "saved");
    const registry = new PathlandRegistry({
      app: new QueryApp(),
      store,
      ssrRenderer: await renderer(),
    });
    const sent: Batch[] = [];
    const connection: PathlandConnection = {
      send(bytes) {
        sent.push(parseBatch(bytes));
      },
      isOpen: () => true,
    };
    registry.open("ui1", "w1", connection);
    registry.resync("ui1");
    await sleep(30);
    expect(sent.length).toBeGreaterThanOrEqual(1);
    const text = sent[0]!.opcodes
      .filter((op) => op.command === CMD_SET_TEXT)
      .map((op) => readString(sent[0]!.strings, op.b));
    expect(text).toContain("saved");
  });

  it("assigns monotonic sequence numbers to live batches", async () => {
    const registry = new PathlandRegistry({
      app: new QueryApp(),
      store: new MemoryStateStore(),
      ssrRenderer: await renderer(),
    });
    const batches: Batch[] = [];
    const connection: PathlandConnection = {
      send(bytes) {
        batches.push(parseBatch(bytes));
      },
      isOpen: () => true,
    };
    registry.open("ui1", "w1", connection);
    await registry.environment("ui1", { route: "/" });
    registry.resync("ui1");
    registry.resync("ui1");
    await sleep(30);
    const sequences = batches.map((batch) => batch.sequence);
    expect(sequences).toEqual(sequences.map((_, index) => index + 1));
  });

  it("rebases string offsets when merging delta frames", () => {
    const frames: Batch[] = [];
    const batcher = new DeltaBatcher((bytes) => frames.push(parseBatch(bytes)), 1000);
    for (const text of ["a", "bb"]) {
      const writer = new BatchWriter();
      writer.begin();
      const offset = writer.string(text);
      writer.push(0x02, 0x03, 0, 1, offset, 0);
      batcher.append(writer.end());
    }
    batcher.flush();
    expect(frames).toHaveLength(1);
    expect(frames[0]!.opcodes).toHaveLength(2);
    expect(readString(frames[0]!.strings, frames[0]!.opcodes[0]!.b)).toBe("a");
    expect(readString(frames[0]!.strings, frames[0]!.opcodes[1]!.b)).toBe("bb");
    expect(frames[0]!.sequence).toBe(1);
    batcher.close();
  });

  it("keeps the wire opcode size fixed", () => {
    expect(OPCODE_SIZE).toBe(16);
    expect(signal("/")()).toBe("/");
  });
});
