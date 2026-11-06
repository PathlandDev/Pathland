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

/** A scalar text-format attribute in an attributed-text run. */
export interface TextRunAttribute {
  readonly property: number;
  readonly valueType: number;
  readonly value: number;
}

/** A range-scoped style run. */
export interface AttributedRun {
  readonly start: number;
  readonly end: number;
  readonly attributes: TextRunAttribute[];
}

/** A range-scoped link. */
export interface AttributedLink {
  readonly start: number;
  readonly end: number;
  readonly url: string;
}

/** Decoded `SET_ATTRIBUTED_TEXT` payload. Offsets are UTF-8 byte offsets. */
export interface AttributedText {
  readonly textBytes: Uint8Array;
  readonly runs: AttributedRun[];
  readonly links: AttributedLink[];
}

/** Read a `[u32 len][bytes]` entry (binary-safe) at a relative string offset. */
export function readEntryBytes(strings: Uint8Array, offset: number): Uint8Array {
  if (offset + 4 > strings.length) {
    throw new ProtocolError("entry offset out of bounds");
  }
  const len = dv(strings).getUint32(offset, true);
  const start = offset + 4;
  if (start + len > strings.length) {
    throw new ProtocolError("entry length out of bounds");
  }
  return strings.subarray(start, start + len);
}

/**
 * Decode a `SET_ATTRIBUTED_TEXT` payload:
 * `[u32 textLen][utf8][u32 runCount][runs][u32 linkCount][links]`.
 */
export function readAttributed(strings: Uint8Array, offset: number): AttributedText {
  const bytes = readEntryBytes(strings, offset);
  const view = dv(bytes);
  let p = 0;
  const need = (n: number): void => {
    if (p + n > bytes.length) {
      throw new ProtocolError("attributed text payload truncated");
    }
  };
  need(4);
  const textLen = view.getUint32(p, true);
  p += 4;
  need(textLen);
  const textBytes = bytes.subarray(p, p + textLen);
  p += textLen;

  need(4);
  const runCount = view.getUint32(p, true);
  p += 4;
  const runs: AttributedRun[] = [];
  for (let i = 0; i < runCount; i++) {
    need(12);
    const start = view.getUint32(p, true);
    const end = view.getUint32(p + 4, true);
    const attrCount = view.getUint32(p + 8, true);
    p += 12;
    const attributes: TextRunAttribute[] = [];
    for (let j = 0; j < attrCount; j++) {
      need(8);
      const property = view.getUint16(p, true);
      const valueType = view.getUint8(p + 2);
      const value = view.getUint32(p + 4, true);
      p += 8;
      attributes.push({ property, valueType, value });
    }
    runs.push({ start, end, attributes });
  }

  need(4);
  const linkCount = view.getUint32(p, true);
  p += 4;
  const links: AttributedLink[] = [];
  for (let i = 0; i < linkCount; i++) {
    need(12);
    const start = view.getUint32(p, true);
    const end = view.getUint32(p + 4, true);
    const urlLen = view.getUint32(p + 8, true);
    p += 12;
    need(urlLen);
    const url = new TextDecoder().decode(bytes.subarray(p, p + urlLen));
    p += urlLen;
    links.push({ start, end, url });
  }
  return { textBytes, runs, links };
}

/** A non-overlapping slice with its effective attributes and link. */
export interface AttributedSegment {
  readonly start: number;
  readonly end: number;
  readonly attributes: TextRunAttribute[];
  readonly link: string | null;
}

/** Split attributed text into non-overlapping segments (later runs/links win). */
export function attributedSegments(value: AttributedText): AttributedSegment[] {
  const boundaries = new Set<number>([0, value.textBytes.length]);
  for (const run of value.runs) {
    boundaries.add(run.start);
    boundaries.add(run.end);
  }
  for (const link of value.links) {
    boundaries.add(link.start);
    boundaries.add(link.end);
  }
  const sorted = [...boundaries].sort((a, b) => a - b);
  const out: AttributedSegment[] = [];
  for (let i = 0; i + 1 < sorted.length; i++) {
    const start = sorted[i]!;
    const end = sorted[i + 1]!;
    if (start === end) {
      continue;
    }
    const attributes: TextRunAttribute[] = [];
    for (const run of value.runs) {
      if (run.start <= start && run.end >= end) {
        for (const attr of run.attributes) {
          const at = attributes.findIndex((a) => a.property === attr.property);
          if (at >= 0) {
            attributes[at] = attr;
          } else {
            attributes.push(attr);
          }
        }
      }
    }
    let link: string | null = null;
    for (const candidate of value.links) {
      if (candidate.start <= start && candidate.end >= end) {
        link = candidate.url;
      }
    }
    out.push({ start, end, attributes, link });
  }
  return out;
}