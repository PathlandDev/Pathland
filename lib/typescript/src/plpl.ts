import { HEADER_SIZE, MAGIC, OPCODE_SIZE, VERSION } from "./constants";

export interface Opcode {
  readonly category: number;
  readonly command: number;
  readonly flags: number;
  readonly a: number;
  readonly b: number;
  readonly c: number;
}

export interface Batch {
  readonly version: number;
  readonly flags: number;
  /** Per-stream monotonic message sequence (guest → host delta batches; 0 for heartbeat/requests). */
  readonly sequence: number;
  readonly opcodes: Opcode[];
  readonly strings: Uint8Array;
}

/** A protocol-level decode failure (truncation, bad magic, unsupported version). */
export class ProtocolError extends Error {}

function dv(bytes: Uint8Array): DataView {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

/**
 * Parse a self-contained PLPL batch:
 * `[magic u32][version u16][flags u16][sequence u32][opcodeCount u32]`,
 * then `opcodeCount × 16-byte opcodes`, then `[stringsLen u32][strings…]`.
 * Every read is bounds-checked.
 */
export function parseBatch(bytes: Uint8Array): Batch {
  if (bytes.length < HEADER_SIZE) {
    throw new ProtocolError("batch truncated");
  }
  const view = dv(bytes);
  if (view.getUint32(0, true) !== MAGIC) {
    throw new ProtocolError("bad batch magic");
  }
  const version = view.getUint16(4, true);
  if (version !== VERSION) {
    throw new ProtocolError(`unsupported batch version ${version}`);
  }
  const flags = view.getUint16(6, true);
  const sequence = view.getUint32(8, true);
  const opcodeCount = view.getUint32(12, true);
  const opcodesEnd = HEADER_SIZE + opcodeCount * OPCODE_SIZE;
  if (opcodesEnd + 4 > bytes.length) {
    throw new ProtocolError("batch truncated (opcodes)");
  }
  const opcodes: Opcode[] = [];
  for (let i = 0; i < opcodeCount; i++) {
    const pos = HEADER_SIZE + i * OPCODE_SIZE;
    opcodes.push({
      category: view.getUint8(pos),
      command: view.getUint8(pos + 1),
      flags: view.getUint16(pos + 2, true),
      a: view.getUint32(pos + 4, true),
      b: view.getUint32(pos + 8, true),
      c: view.getUint32(pos + 12, true),
    });
  }
  const stringsLen = view.getUint32(opcodesEnd, true);
  const stringsStart = opcodesEnd + 4;
  if (stringsStart + stringsLen > bytes.length) {
    throw new ProtocolError("batch truncated (strings)");
  }
  return {
    version,
    flags,
    sequence,
    opcodes,
    strings: bytes.subarray(stringsStart, stringsStart + stringsLen),
  };
}

/** Read a length-prefixed string (`[u32 len][utf8…]`) at a relative offset into the string section. */
export function readString(strings: Uint8Array, offset: number): string {
  if (offset + 4 > strings.length) {
    throw new ProtocolError("string offset out of bounds");
  }
  const view = dv(strings);
  const len = view.getUint32(offset, true);
  const start = offset + 4;
  if (start + len > strings.length) {
    throw new ProtocolError("string length out of bounds");
  }
  return new TextDecoder().decode(strings.subarray(start, start + len));
}

/** Read a length-prefixed f32 array (`[u32 count][f32 × count]`) at a relative
 *  offset into the string section (the `LIST` value type — e.g. a `FIT_QUERY`
 *  threshold table). */
export function readList(strings: Uint8Array, offset: number): number[] {
  if (offset + 4 > strings.length) {
    throw new ProtocolError("list offset out of bounds");
  }
  const view = dv(strings);
  const count = view.getUint32(offset, true);
  const start = offset + 4;
  if (start + count * 4 > strings.length) {
    throw new ProtocolError("list length out of bounds");
  }
  const out: number[] = new Array(count);
  for (let i = 0; i < count; i++) {
    out[i] = view.getFloat32(start + i * 4, true);
  }
  return out;
}