import {
  CAT_EVENT,
  CAT_META,
  CMD_ENVIRONMENT,
  CMD_PING,
  CMD_PONG,
  CMD_NAVIGATE,
  CMD_RESYNC,
  CMD_TEXT_CHANGED,
  ENV_ROUTE,
  ENV_VIEWPORT_HEIGHT,
  ENV_VIEWPORT_WIDTH,
  FLAG_NAVIGATE_URL,
  HEADER_SIZE,
  MAGIC,
  OPCODE_SIZE,
  VERSION,
} from "./constants";
import { parseBatch, readString, type Batch, type Opcode } from "./batch";

export interface EventOpcode extends Opcode {
  text?: string;
}

export interface EnvironmentData {
  route: string;
  viewportWidth?: number;
  viewportHeight?: number;
}

export type HostMessage =
  | { type: "environment"; env: EnvironmentData }
  | { type: "resync" }
  | { type: "ping" }
  | { type: "pong" }
  | { type: "events"; events: EventOpcode[] };

/** Decode every EVENT opcode in a batch, resolving TEXT_CHANGED's string. */
export function decodeEvents(bytes: Uint8Array): EventOpcode[] {
  return decodeEventsFromBatch(parseBatch(bytes));
}

function decodeEventsFromBatch(batch: Batch): EventOpcode[] {
  const events: EventOpcode[] = [];
  for (const op of batch.opcodes) {
    if (op.category !== CAT_EVENT) continue;
    if (op.command === CMD_TEXT_CHANGED) {
      events.push({ ...op, text: readString(batch.strings, op.b) });
    } else if (op.command === CMD_NAVIGATE && (op.flags & FLAG_NAVIGATE_URL) !== 0) {
      events.push({ ...op, text: readString(batch.strings, op.b) });
    } else {
      events.push({ ...op });
    }
  }
  return events;
}

/** Decode a host → guest message: environment, resync, heartbeat, or raw events. */
export function decodeHostMessage(bytes: Uint8Array): HostMessage {
  const batch = parseBatch(bytes);
  const meta = batch.opcodes.find((op) => op.category === CAT_META);
  if (meta) {
    switch (meta.command) {
      case CMD_ENVIRONMENT:
        return { type: "environment", env: decodeEnvironment(batch) };
      case CMD_RESYNC:
        return { type: "resync" };
      case CMD_PING:
        return { type: "ping" };
      case CMD_PONG:
        return { type: "pong" };
      default:
        break;
    }
  }
  return { type: "events", events: decodeEventsFromBatch(batch) };
}

function decodeEnvironment(batch: Batch): EnvironmentData {
  let route = "/";
  let viewportWidth: number | undefined;
  let viewportHeight: number | undefined;
  for (const op of batch.opcodes) {
    if (op.category !== CAT_META || op.command !== CMD_ENVIRONMENT) continue;
    if (op.a === ENV_ROUTE) route = readString(batch.strings, op.b);
    else if (op.a === ENV_VIEWPORT_WIDTH) viewportWidth = f32FromBits(op.b);
    else if (op.a === ENV_VIEWPORT_HEIGHT) viewportHeight = f32FromBits(op.b);
  }
  return { route, viewportWidth, viewportHeight };
}

/** Encode a META::PONG reply (server → client heartbeat). */
export function encodePong(): Uint8Array {
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0x0001, true);
  view.setUint32(8, 0, true);
  view.setUint32(12, 1, true);
  const pos = HEADER_SIZE;
  view.setUint8(pos, CAT_META);
  view.setUint8(pos + 1, CMD_PONG);
  return out;
}

export function f32FromBits(bits: number): number {
  return new Float32Array(new Uint32Array([bits >>> 0]).buffer)[0]!;
}
