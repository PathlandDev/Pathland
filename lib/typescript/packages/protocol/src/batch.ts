import {
  HEADER_SIZE,
  MAGIC,
  OPCODE_SIZE,
  VERSION,
} from "./constants";

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
  readonly sequence: number;
  readonly opcodes: Opcode[];
  readonly strings: Uint8Array;
}

export class ProtocolError extends Error {}

function viewOf(bytes: Uint8Array): DataView {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

function u32(value: number): number {
  return value >>> 0;
}

function f32bits(value: number): number {
  return new Uint32Array(new Float32Array([value]).buffer)[0]!;
}

function writeU32(out: number[], value: number): void {
  const v = u32(value);
  out.push(v & 0xff, (v >>> 8) & 0xff, (v >>> 16) & 0xff, (v >>> 24) & 0xff);
}

/**
 * Builds a self-contained PLPL batch: opcodes plus a frame-local string section.
 * String-valued opcodes reference the section by the offset returned from
 * `string()` / `list()`.
 */
export class BatchWriter {
  private opcodes: Opcode[] = [];
  private strings: number[] = [];

  begin(): void {
    this.opcodes = [];
    this.strings = [];
  }

  push(category: number, command: number, flags: number, a: number, b: number, c: number): void {
    this.opcodes.push({
      category: category & 0xff,
      command: command & 0xff,
      flags: flags & 0xffff,
      a: u32(a),
      b: u32(b),
      c: u32(c),
    });
  }

  /** Append a length-prefixed UTF-8 string and return its section offset. */
  string(value: string): number {
    const offset = this.strings.length;
    const bytes = new TextEncoder().encode(value);
    writeU32(this.strings, bytes.length);
    for (const byte of bytes) this.strings.push(byte);
    return offset;
  }

  /** Append a length-prefixed f32 array and return its section offset. */
  list(values: readonly number[]): number {
    const offset = this.strings.length;
    writeU32(this.strings, values.length);
    for (const value of values) writeU32(this.strings, f32bits(value));
    return offset;
  }

  /** Encode the batch. Delta frames pass sequence 0; the server batcher assigns the stream sequence. */
  end(sequence = 0): Uint8Array {
    const out = new Uint8Array(
      HEADER_SIZE + this.opcodes.length * OPCODE_SIZE + 4 + this.strings.length,
    );
    const view = viewOf(out);
    view.setUint32(0, MAGIC, true);
    view.setUint16(4, VERSION, true);
    view.setUint16(6, 0, true);
    view.setUint32(8, u32(sequence), true);
    view.setUint32(12, this.opcodes.length, true);
    let pos = HEADER_SIZE;
    for (const op of this.opcodes) {
      view.setUint8(pos, op.category);
      view.setUint8(pos + 1, op.command);
      view.setUint16(pos + 2, op.flags, true);
      view.setUint32(pos + 4, op.a, true);
      view.setUint32(pos + 8, op.b, true);
      view.setUint32(pos + 12, op.c, true);
      pos += OPCODE_SIZE;
    }
    view.setUint32(pos, this.strings.length, true);
    out.set(this.strings, pos + 4);
    return out;
  }
}

/** Encode already-built opcodes and a raw string section. */
export function encodeBatch(
  opcodes: readonly Opcode[],
  strings: Uint8Array,
  sequence = 0,
): Uint8Array {
  const out = new Uint8Array(HEADER_SIZE + opcodes.length * OPCODE_SIZE + 4 + strings.length);
  const view = viewOf(out);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0, true);
  view.setUint32(8, u32(sequence), true);
  view.setUint32(12, opcodes.length, true);
  let pos = HEADER_SIZE;
  for (const op of opcodes) {
    view.setUint8(pos, op.category);
    view.setUint8(pos + 1, op.command);
    view.setUint16(pos + 2, op.flags, true);
    view.setUint32(pos + 4, op.a, true);
    view.setUint32(pos + 8, op.b, true);
    view.setUint32(pos + 12, op.c, true);
    pos += OPCODE_SIZE;
  }
  view.setUint32(pos, strings.length, true);
  out.set(strings, pos + 4);
  return out;
}

/** Parse a self-contained PLPL batch, bounds-checking every read. */
export function parseBatch(bytes: Uint8Array): Batch {
  if (bytes.length < HEADER_SIZE) throw new ProtocolError("batch truncated");
  const view = viewOf(bytes);
  if (view.getUint32(0, true) !== MAGIC) throw new ProtocolError("bad batch magic");
  const version = view.getUint16(4, true);
  if (version !== VERSION) throw new ProtocolError(`unsupported batch version ${version}`);
  const flags = view.getUint16(6, true);
  const sequence = view.getUint32(8, true);
  const opcodeCount = view.getUint32(12, true);
  const opcodesEnd = HEADER_SIZE + opcodeCount * OPCODE_SIZE;
  if (opcodesEnd + 4 > bytes.length) throw new ProtocolError("batch truncated (opcodes)");
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
  if (stringsStart + stringsLen > bytes.length) throw new ProtocolError("batch truncated (strings)");
  return {
    version,
    flags,
    sequence,
    opcodes,
    strings: bytes.subarray(stringsStart, stringsStart + stringsLen),
  };
}

/** Read a length-prefixed UTF-8 string at a relative string-section offset. */
export function readString(strings: Uint8Array, offset: number): string {
  if (offset + 4 > strings.length) throw new ProtocolError("string offset out of bounds");
  const view = viewOf(strings);
  const length = view.getUint32(offset, true);
  const start = offset + 4;
  if (start + length > strings.length) throw new ProtocolError("string length out of bounds");
  return new TextDecoder().decode(strings.subarray(start, start + length));
}

/** Read a length-prefixed f32 array at a relative string-section offset. */
export function readList(strings: Uint8Array, offset: number): number[] {
  if (offset + 4 > strings.length) throw new ProtocolError("list offset out of bounds");
  const view = viewOf(strings);
  const count = view.getUint32(offset, true);
  const start = offset + 4;
  if (start + count * 4 > strings.length) throw new ProtocolError("list length out of bounds");
  const out: number[] = new Array(count);
  for (let i = 0; i < count; i++) out[i] = view.getFloat32(start + i * 4, true);
  return out;
}

export { f32bits };
