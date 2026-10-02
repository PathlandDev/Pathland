import { describe, expect, it } from "vitest";
import {
  encodeDateChanged,
  encodeEditingChanged,
  encodeEnvironment,
  encodeFocusChanged,
  encodeKeyDown,
  encodeKeyUp,
  encodeMediaEnded,
  encodeMediaPlayStateChanged,
  encodeMediaTimeUpdated,
  encodeMediaVolumeChanged,
  encodeNavigate,
  encodeNavigateBack,
  encodePing,
  encodePointerDown,
  encodePointerMove,
  encodePointerUp,
  encodeResync,
  encodeScroll,
  encodeSubmit,
  encodeTextChanged,
  encodeValueBits,
  encodeValueChanged,
  encodeWheel,
} from "../src/events";
import {
  CAT_EVENT,
  CAT_META,
  CMD_DATE_CHANGED,
  CMD_EDITING_CHANGED,
  CMD_ENVIRONMENT,
  CMD_FOCUS_CHANGED,
  CMD_KEY_DOWN,
  CMD_KEY_UP,
  CMD_MEDIA_ENDED,
  CMD_MEDIA_PLAY_STATE_CHANGED,
  CMD_MEDIA_TIME_UPDATED,
  CMD_MEDIA_VOLUME_CHANGED,
  CMD_NAVIGATE,
  CMD_POINTER_DOWN,
  CMD_POINTER_UP,
  CMD_SCROLL,
  CMD_SUBMIT,
  CMD_TEXT_CHANGED,
  CMD_VALUE_CHANGED,
  CMD_WHEEL,
  ENV_ROUTE,
  ENV_VIEWPORT_HEIGHT,
  ENV_VIEWPORT_WIDTH,
  FLAG_NAVIGATE_URL,
  HEADER_SIZE,
  MAGIC,
  VERSION,
} from "../src/constants";
import { parseBatch, readString } from "../src/plpl";

function eventOf(bytes: Uint8Array): { category: number; command: number; flags: number; a: number; b: number; c: number } {
  return parseBatch(bytes).opcodes[0]!;
}

describe("event encoders", () => {
  it("encodePointerUp: header + single EVENT opcode, guest->host, x/y in B/C", () => {
    const bytes = encodePointerUp(42, 12.5, 33.25);
    expect(bytes.length).toBe(HEADER_SIZE + 16 + 4);
    const view = new DataView(bytes.buffer);
    expect(view.getUint32(0, true)).toBe(MAGIC);
    expect(view.getUint16(4, true)).toBe(VERSION);
    expect(view.getUint16(6, true)).toBe(0); // guest -> host
    expect(view.getUint32(12, true)).toBe(1); // opcodeCount
    const op = eventOf(bytes);
    expect(op.category).toBe(CAT_EVENT);
    expect(op.command).toBe(CMD_POINTER_UP);
    expect(op.a).toBe(42);
    expect(op.flags).toBe(0);
    expect(new Float32Array(new Uint32Array([op.b]).buffer)[0]).toBe(12.5);
    expect(new Float32Array(new Uint32Array([op.c]).buffer)[0]).toBe(33.25);
  });

  it("encodePointerDown carries the secondary flag", () => {
    const op = eventOf(encodePointerDown(7, 0, 0, 0x0001));
    expect(op.command).toBe(CMD_POINTER_DOWN);
    expect(op.flags).toBe(0x0001);
  });

  it("encodePointerMove carries hover enter/leave flags", () => {
    expect(eventOf(encodePointerMove(7, 0, 0, 0x0001)).flags).toBe(0x0001);
    expect(eventOf(encodePointerMove(7, 0, 0, 0x0002)).flags).toBe(0x0002);
  });

  it("encodeKeyDown packs keyCode + modifiers + repeat flag", () => {
    const op = eventOf(encodeKeyDown(5, 0x41, 0x01, true)); // 'A', Shift, repeat
    expect(op.command).toBe(CMD_KEY_DOWN);
    expect(op.b).toBe(0x41);
    expect(op.c).toBe(0x01);
    expect(op.flags).toBe(0x0002);
    expect(eventOf(encodeKeyUp(5, 0x41, 0x01)).command).toBe(CMD_KEY_UP);
  });

  it("encodeValueChanged packs the f32 value into B", () => {
    const op = eventOf(encodeValueChanged(7, 0.5));
    expect(op.command).toBe(CMD_VALUE_CHANGED);
    expect(new Float32Array(new Uint32Array([op.b]).buffer)[0]).toBe(0.5);
  });

  it("encodeValueBits passes raw bits through B", () => {
    const op = eventOf(encodeValueBits(3, 0xff000000 | 0xabcdef));
    expect(op.b).toBe(0xffabcdef);
  });

  it("encodeDateChanged packs days + millis into B/C", () => {
    const op = eventOf(encodeDateChanged(9, 20000, 3600000));
    expect(op.command).toBe(CMD_DATE_CHANGED);
    expect(op.b).toBe(20000);
    expect(op.c).toBe(3600000);
  });

  it("encodeTextChanged rides text in the string section (round-trips)", () => {
    const batch = parseBatch(encodeTextChanged(11, "hello"));
    expect(batch.opcodes[0]?.category).toBe(CAT_EVENT);
    expect(batch.opcodes[0]?.command).toBe(CMD_TEXT_CHANGED);
    expect(batch.opcodes[0]?.a).toBe(11);
    expect(readString(batch.strings, batch.opcodes[0]!.b)).toBe("hello");
  });

  it("focus / editing / submit carry their 0/1 payload", () => {
    expect(eventOf(encodeFocusChanged(4, true)).command).toBe(CMD_FOCUS_CHANGED);
    expect(eventOf(encodeFocusChanged(4, true)).b).toBe(1);
    expect(eventOf(encodeEditingChanged(4, false)).command).toBe(CMD_EDITING_CHANGED);
    expect(eventOf(encodeEditingChanged(4, false)).b).toBe(0);
    expect(eventOf(encodeSubmit(4)).command).toBe(CMD_SUBMIT);
  });

  it("scroll / wheel pack f32 offsets into B/C", () => {
    const scroll = eventOf(encodeScroll(2, 100, 250));
    expect(scroll.command).toBe(CMD_SCROLL);
    expect(new Float32Array(new Uint32Array([scroll.b]).buffer)[0]).toBe(100);
    expect(new Float32Array(new Uint32Array([scroll.c]).buffer)[0]).toBe(250);
    const wheel = eventOf(encodeWheel(2, -3, 9));
    expect(wheel.command).toBe(CMD_WHEEL);
    expect(new Float32Array(new Uint32Array([wheel.b]).buffer)[0]).toBe(-3);
  });

  it("encodeResync is a host->guest META::RESYNC batch", () => {
    const bytes = encodeResync();
    expect(bytes.length).toBe(HEADER_SIZE + 16 + 4);
    const view = new DataView(bytes.buffer);
    expect(view.getUint32(0, true)).toBe(MAGIC);
    expect(view.getUint16(4, true)).toBe(VERSION);
    expect(view.getUint16(6, true)).toBe(0x0001); // HOST_TO_GUEST
    expect(view.getUint32(12, true)).toBe(1); // opcodeCount
    const batch = parseBatch(bytes);
    expect(batch.opcodes[0]?.category).toBe(0x04); // META
    expect(batch.opcodes[0]?.command).toBe(0x03); // RESYNC
    expect(batch.opcodes[0]?.a).toBe(0);
    expect(batch.opcodes[0]?.b).toBe(0);
    expect(batch.opcodes[0]?.c).toBe(0);
  });

  it("encodePing is a guest->host META::PING heartbeat batch (spec vector 16a)", () => {
    const bytes = encodePing();
    expect(bytes.length).toBe(HEADER_SIZE + 16 + 4);
    const view = new DataView(bytes.buffer);
    expect(view.getUint32(0, true)).toBe(MAGIC);
    expect(view.getUint16(4, true)).toBe(VERSION);
    expect(view.getUint16(6, true)).toBe(0x0000); // GUEST_TO_HOST
    expect(view.getUint32(12, true)).toBe(1); // opcodeCount
    const batch = parseBatch(bytes);
    expect(batch.opcodes[0]?.category).toBe(0x04); // META
    expect(batch.opcodes[0]?.command).toBe(0x04); // PING
    expect(batch.opcodes[0]?.a).toBe(0);
    expect(batch.opcodes[0]?.b).toBe(0);
    expect(batch.opcodes[0]?.c).toBe(0);
    // byte-exact vs spec/CONFORMANCE.md vector 16a
    expect([...bytes]).toEqual([
      0x4c, 0x50, 0x4c, 0x50, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
      0x04, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
    ]);
  });

  it("encodeNavigate rides the URL in the string section (no target, global)", () => {
    const batch = parseBatch(encodeNavigate("https://example.com/users/7"));
    const op = batch.opcodes[0]!;
    expect(op.category).toBe(CAT_EVENT);
    expect(op.command).toBe(CMD_NAVIGATE);
    expect(op.flags).toBe(FLAG_NAVIGATE_URL);
    expect(op.a).toBe(0); // global — not node-keyed
    expect(readString(batch.strings, op.b)).toBe("https://example.com/users/7");
  });

  it("encodeNavigateBack is a NAVIGATE with no URL flag", () => {
    const op = eventOf(encodeNavigateBack());
    expect(op.command).toBe(CMD_NAVIGATE);
    expect(op.flags).toBe(0);
    expect(op.a).toBe(0);
  });

  it("encodeEnvironment carries viewport + route as a META::ENVIRONMENT field batch", () => {
    const batch = parseBatch(encodeEnvironment(800, 600, "/users/42"));
    const ops = batch.opcodes;
    expect(ops).toHaveLength(3);
    expect(ops.every((op) => op.category === CAT_META && op.command === CMD_ENVIRONMENT)).toBe(true);

    const width = ops.find((op) => op.a === ENV_VIEWPORT_WIDTH)!;
    const height = ops.find((op) => op.a === ENV_VIEWPORT_HEIGHT)!;
    const route = ops.find((op) => op.a === ENV_ROUTE)!;
    expect(new Float32Array(new Uint32Array([width.b]).buffer)[0]).toBe(800);
    expect(new Float32Array(new Uint32Array([height.b]).buffer)[0]).toBe(600);
    expect(readString(batch.strings, route.b)).toBe("/users/42");
  });

  it("media event encoders carry the media node id + payload", () => {
    const play = eventOf(encodeMediaPlayStateChanged(7, true));
    expect(play.command).toBe(CMD_MEDIA_PLAY_STATE_CHANGED);
    expect(play.a).toBe(7);
    expect(play.b).toBe(1);

    const time = eventOf(encodeMediaTimeUpdated(7, 12.5));
    expect(time.command).toBe(CMD_MEDIA_TIME_UPDATED);
    expect(time.a).toBe(7);
    expect(new Float32Array(new Uint32Array([time.b]).buffer)[0]).toBe(12.5);

    const ended = eventOf(encodeMediaEnded(7));
    expect(ended.command).toBe(CMD_MEDIA_ENDED);
    expect(ended.a).toBe(7);

    const volume = eventOf(encodeMediaVolumeChanged(7, 0.5));
    expect(volume.command).toBe(CMD_MEDIA_VOLUME_CHANGED);
    expect(new Float32Array(new Uint32Array([volume.b]).buffer)[0]).toBe(0.5);
  });
});