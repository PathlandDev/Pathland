import { describe, expect, it } from "vitest";
import {
  BatchWriter,
  CAT_EVENT,
  CAT_META,
  CMD_ENVIRONMENT,
  CMD_PING,
  CMD_TEXT_CHANGED,
  ENV_ROUTE,
  ENV_VIEWPORT_WIDTH,
  HEADER_SIZE,
  MAGIC,
  OPCODE_SIZE,
  VERSION,
  decodeEvents,
  decodeHostMessage,
  encodePong,
  f32bits,
  parseBatch,
  readList,
  readString,
} from "../src/index";

describe("@pathland/protocol", () => {
  it("writes a self-contained batch with a known header and round-trips it", () => {
    const writer = new BatchWriter();
    writer.begin();
    const hello = writer.string("hello");
    writer.push(0x01, 0x01, 0, 7, 0x01, 0);
    const offset = writer.string("Ada");
    writer.push(0x02, 0x03, 0, 7, hello, 0);
    const bytes = writer.end(3);

    const view = new DataView(bytes.buffer);
    expect(view.getUint32(0, true)).toBe(MAGIC);
    expect(view.getUint16(4, true)).toBe(VERSION);
    expect(view.getUint32(8, true)).toBe(3);
    expect(view.getUint32(12, true)).toBe(2);
    expect(bytes.length).toBe(HEADER_SIZE + 2 * OPCODE_SIZE + 4 + 16);

    const batch = parseBatch(bytes);
    expect(batch.opcodes.map((op) => [op.category, op.command])).toEqual([
      [0x01, 0x01],
      [0x02, 0x03],
    ]);
    expect(readString(batch.strings, hello)).toBe("hello");
    expect(readString(batch.strings, offset)).toBe("Ada");
  });

  it("round-trips list values", () => {
    const writer = new BatchWriter();
    writer.begin();
    const offset = writer.list([1, 2.5, -3]);
    const batch = parseBatch(writer.end());
    expect(readList(batch.strings, offset)).toEqual([1, 2.5, -3]);
  });

  it("decodes TEXT_CHANGED from the string section", () => {
    const writer = new BatchWriter();
    writer.begin();
    const text = writer.string("query");
    writer.push(CAT_EVENT, CMD_TEXT_CHANGED, 0, 4, text, 0);
    const events = decodeEvents(writer.end());
    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ category: CAT_EVENT, command: CMD_TEXT_CHANGED, a: 4, text: "query" });
  });

  it("decodes a META::ENVIRONMENT message", () => {
    const writer = new BatchWriter();
    writer.begin();
    writer.push(CAT_META, CMD_ENVIRONMENT, 0, ENV_VIEWPORT_WIDTH, f32bits(800), 0);
    const route = writer.string("/profile");
    writer.push(CAT_META, CMD_ENVIRONMENT, 0, ENV_ROUTE, route, 0);
    const message = decodeHostMessage(writer.end());
    expect(message.type).toBe("environment");
    if (message.type === "environment") {
      expect(message.env.route).toBe("/profile");
      expect(message.env.viewportWidth).toBe(800);
    }
  });

  it("detects META::PING and encodes META::PONG", () => {
    const writer = new BatchWriter();
    writer.begin();
    writer.push(CAT_META, CMD_PING, 0, 0, 0, 0);
    expect(decodeHostMessage(writer.end()).type).toBe("ping");
    expect(decodeHostMessage(encodePong()).type).toBe("pong");
  });
});
