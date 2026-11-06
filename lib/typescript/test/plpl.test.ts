import { describe, expect, it } from "vitest";
import {
  MAX_ATTRIBUTED_RUNS,
  MAX_OPCODES,
  MAX_STRING_BYTES,
  ProtocolError,
  parseBatch,
  readAttributed,
  readString,
} from "../src/plpl";
import { HEADER_SIZE, MAGIC, OPCODE_SIZE, VERSION } from "../src/constants";

/** Build a self-contained PLPL batch (header + opcodes + string section). */
export function buildBatch(opcodes: number[][], strings?: Uint8Array): Uint8Array {
  const str = strings ?? new Uint8Array(0);
  const out = new Uint8Array(HEADER_SIZE + opcodes.length * OPCODE_SIZE + 4 + str.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0, true);
  view.setUint32(8, 3, true); // sequence
  view.setUint32(12, opcodes.length, true);
  opcodes.forEach((op, i) => {
    const pos = HEADER_SIZE + i * OPCODE_SIZE;
    view.setUint8(pos, op[0]!);
    view.setUint8(pos + 1, op[1]!);
    view.setUint16(pos + 2, op[2] ?? 0, true);
    view.setUint32(pos + 4, op[3] ?? 0, true);
    view.setUint32(pos + 8, op[4] ?? 0, true);
    view.setUint32(pos + 12, op[5] ?? 0, true);
  });
  view.setUint32(HEADER_SIZE + opcodes.length * OPCODE_SIZE, str.length, true);
  out.set(str, HEADER_SIZE + opcodes.length * OPCODE_SIZE + 4);
  return out;
}

/** A `[u32 len][utf8]` string entry for the string section. */
export function stringEntry(text: string): Uint8Array {
  const bytes = new TextEncoder().encode(text);
  const out = new Uint8Array(4 + bytes.length);
  new DataView(out.buffer).setUint32(0, bytes.length, true);
  out.set(bytes, 4);
  return out;
}

describe("parseBatch", () => {
  it("parses a batch with a string section", () => {
    const batch = parseBatch(buildBatch([[2, 3, 0, 7, 0, 0]], stringEntry("hi")));
    expect(batch.version).toBe(VERSION);
    expect(batch.flags).toBe(0);
    expect(batch.sequence).toBe(3);
    expect(batch.opcodes).toHaveLength(1);
    expect(batch.opcodes[0]).toEqual({ category: 2, command: 3, flags: 0, a: 7, b: 0, c: 0 });
    expect(readString(batch.strings, 0)).toBe("hi");
  });

  it("rejects a truncated batch", () => {
    expect(() => parseBatch(new Uint8Array(8))).toThrow(ProtocolError);
  });

  it("rejects a bad magic", () => {
    const bytes = buildBatch([]);
    bytes[0] = 0x00;
    expect(() => parseBatch(bytes)).toThrow(ProtocolError);
  });

  it("rejects an unsupported version", () => {
    const bytes = buildBatch([]);
    new DataView(bytes.buffer).setUint16(4, 2, true);
    expect(() => parseBatch(bytes)).toThrow(/unsupported batch version 2/);
  });

  it("rejects truncated opcodes", () => {
    const bytes = buildBatch([]);
    new DataView(bytes.buffer).setUint32(12, 1000, true);
    expect(() => parseBatch(bytes)).toThrow(ProtocolError);
  });

  it("rejects a truncated string section", () => {
    const batch = parseBatch(buildBatch([[2, 3, 0, 7, 0, 0]], new Uint8Array([10, 0, 0, 0])));
    expect(() => readString(batch.strings, 0)).toThrow(ProtocolError);
  });

  it("rejects a string offset out of bounds", () => {
    const batch = parseBatch(buildBatch([]));
    expect(() => readString(batch.strings, 1)).toThrow(ProtocolError);
  });

  it("rejects an opcode count above the decoder limit", () => {
    const bytes = buildBatch([]);
    new DataView(bytes.buffer).setUint32(12, MAX_OPCODES + 1, true);
    expect(() => parseBatch(bytes)).toThrow(/opcode count/);
  });

  it("rejects a string section above the decoder limit", () => {
    const bytes = buildBatch([]);
    new DataView(bytes.buffer).setUint32(HEADER_SIZE, MAX_STRING_BYTES + 1, true);
    expect(() => parseBatch(bytes)).toThrow(/string section/);
  });

  it("rejects an attributed run beyond the decoder run limit", () => {
    const payload = new Uint8Array(8);
    const view = new DataView(payload.buffer);
    view.setUint32(0, 0, true); // textLen
    view.setUint32(4, MAX_ATTRIBUTED_RUNS + 1, true); // runCount
    const strings = new Uint8Array(4 + payload.length);
    new DataView(strings.buffer).setUint32(0, payload.length, true);
    strings.set(payload, 4);
    expect(() => readAttributed(strings, 0)).toThrow(/run count/);
  });

  it("rejects an attributed run whose range exceeds the text", () => {
    const text = new TextEncoder().encode("Hi");
    const payload = new Uint8Array(4 + text.length + 4 + 12 + 4);
    const view = new DataView(payload.buffer);
    let p = 0;
    view.setUint32(p, text.length, true); p += 4;
    payload.set(text, p); p += text.length;
    view.setUint32(p, 1, true); p += 4; // runCount
    view.setUint32(p, 0, true); p += 4; // start
    view.setUint32(p, 99, true); p += 4; // end beyond text
    view.setUint32(p, 0, true); // attrCount
    const strings = new Uint8Array(4 + payload.length);
    new DataView(strings.buffer).setUint32(0, payload.length, true);
    strings.set(payload, 4);
    expect(() => readAttributed(strings, 0)).toThrow(/range out of bounds/);
  });
});