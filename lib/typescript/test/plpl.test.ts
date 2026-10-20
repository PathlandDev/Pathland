import { describe, expect, it } from "vitest";
import { ProtocolError, parseBatch, readString } from "../src/plpl";
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
});