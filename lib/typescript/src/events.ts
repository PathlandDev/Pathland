// Host → guest event encoders. Each call produces a self-contained PLPL batch
// with a single EVENT opcode (guest → host direction, flags = 0x0000).

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
  CMD_FIT_CHANGED,
  CMD_NAVIGATE,
  CMD_PING,
  CMD_POINTER_DOWN,
  CMD_POINTER_MOVE,
  CMD_POINTER_UP,
  CMD_RESYNC,
  CMD_SCROLL,
  CMD_SUBMIT,
  CMD_TEXT_CHANGED,
  CMD_VALUE_CHANGED,
  CMD_WHEEL,
  ENV_ROUTE,
  ENV_VIEWPORT_HEIGHT,
  ENV_VIEWPORT_WIDTH,
  FLAG_HOST_TO_GUEST,
  FLAG_NAVIGATE_URL,
  HEADER_SIZE,
  MAGIC,
  OPCODE_SIZE,
  VERSION,
} from "./constants";
import { bitsFromF32 } from "./format";

function eventBatch(command: number, a: number, b: number, c: number, opcodeFlags = 0): Uint8Array {
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0, true); // batch flags = guest -> host
  view.setUint32(8, 0, true); // sequence (0: not a delta batch)
  view.setUint32(12, 1, true); // opcodeCount
  const pos = HEADER_SIZE;
  view.setUint8(pos, CAT_EVENT);
  view.setUint8(pos + 1, command);
  view.setUint16(pos + 2, opcodeFlags, true);
  view.setUint32(pos + 4, a, true);
  view.setUint32(pos + 8, b, true);
  view.setUint32(pos + 12, c, true);
  return out;
}

/** A META::RESYNC request (host → guest): ask the server for a full snapshot of the current tree. */
export function encodeResync(): Uint8Array {
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0x0001, true); // batch flags = HOST_TO_GUEST
  view.setUint32(8, 0, true); // sequence (0: not a delta batch)
  view.setUint32(12, 1, true); // opcodeCount
  const pos = HEADER_SIZE;
  view.setUint8(pos, CAT_META);
  view.setUint8(pos + 1, CMD_RESYNC);
  view.setUint32(pos + 4, 0, true);
  view.setUint32(pos + 8, 0, true);
  view.setUint32(pos + 12, 0, true);
  return out;
}

/** A META::PING transport-liveness heartbeat probe (guest → host, spec/OPCODE.md §Transport heartbeat). */
export function encodePing(): Uint8Array {
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0x0000, true); // batch flags = guest -> host
  view.setUint32(8, 0, true); // sequence (0: not a delta batch)
  view.setUint32(12, 1, true); // opcodeCount
  const pos = HEADER_SIZE;
  view.setUint8(pos, CAT_META);
  view.setUint8(pos + 1, CMD_PING);
  view.setUint16(pos + 2, 0, true);
  view.setUint32(pos + 4, 0, true);
  view.setUint32(pos + 8, 0, true);
  view.setUint32(pos + 12, 0, true);
  return out;
}

// --- pointer ---

/** A button press: POINTER_DOWN at viewport-relative x/y (f32). */
export function encodePointerDown(target: number, x: number, y: number, flags = 0): Uint8Array {
  return eventBatch(CMD_POINTER_DOWN, target, bitsFromF32(x), bitsFromF32(y), flags);
}

/** A pointer move / hover enter-leave at viewport-relative x/y. */
export function encodePointerMove(target: number, x: number, y: number, flags: number): Uint8Array {
  return eventBatch(CMD_POINTER_MOVE, target, bitsFromF32(x), bitsFromF32(y), flags);
}

/** A button release: POINTER_UP at viewport-relative x/y. */
export function encodePointerUp(target: number, x: number, y: number, flags = 0): Uint8Array {
  return eventBatch(CMD_POINTER_UP, target, bitsFromF32(x), bitsFromF32(y), flags);
}

// --- keyboard ---

/** KEY_DOWN (A=target, B=keyCode u16 low, C=modifiers u8 low, flag KEY_REPEAT). */
export function encodeKeyDown(target: number, keyCode: number, modifiers: number, repeat = false): Uint8Array {
  return eventBatch(CMD_KEY_DOWN, target, keyCode & 0xffff, modifiers & 0xff, repeat ? 0x0002 : 0);
}

/** KEY_UP (A=target, B=keyCode u16 low, C=modifiers u8 low). */
export function encodeKeyUp(target: number, keyCode: number, modifiers: number): Uint8Array {
  return eventBatch(CMD_KEY_UP, target, keyCode & 0xffff, modifiers & 0xff);
}

// --- value / text / date ---

/** A VALUE_CHANGED EVENT with the value as f32 bits (toggle 0/1, slider, select index, stepper). */
export function encodeValueChanged(target: number, value: number): Uint8Array {
  return eventBatch(CMD_VALUE_CHANGED, target, bitsFromF32(value), 0);
}

/** A VALUE_CHANGED EVENT with a raw 32-bit payload (e.g. a packed ARGB color). */
export function encodeValueBits(target: number, bits: number): Uint8Array {
  return eventBatch(CMD_VALUE_CHANGED, target, bits >>> 0, 0);
}

// --- media ---

/** A bound media node's play state changed (B = 0/1). */
export function encodeMediaPlayStateChanged(target: number, playing: boolean): Uint8Array {
  return eventBatch(CMD_MEDIA_PLAY_STATE_CHANGED, target, playing ? 1 : 0, 0);
}

/** A bound media node's current position (B = seconds, f32). */
export function encodeMediaTimeUpdated(target: number, seconds: number): Uint8Array {
  return eventBatch(CMD_MEDIA_TIME_UPDATED, target, bitsFromF32(seconds), 0);
}

/** A bound media node reached the end of its media. */
export function encodeMediaEnded(target: number): Uint8Array {
  return eventBatch(CMD_MEDIA_ENDED, target, 0, 0);
}

/** A bound media node's volume changed (B = 0..1, f32). */
export function encodeMediaVolumeChanged(target: number, volume: number): Uint8Array {
  return eventBatch(CMD_MEDIA_VOLUME_CHANGED, target, bitsFromF32(volume), 0);
}

/** A `SIZE_THAT_FITS` slot's selected candidate changed (B = index, f32). */
export function encodeFittedChanged(target: number, index: number): Uint8Array {
  return eventBatch(CMD_FIT_CHANGED, target, bitsFromF32(index), 0);
}

/** A DATE_CHANGED EVENT (A=target, B=days, C=millis of day). */
export function encodeDateChanged(target: number, days: number, millis: number): Uint8Array {
  return eventBatch(CMD_DATE_CHANGED, target, days >>> 0, millis >>> 0);
}

/** A TEXT_CHANGED EVENT; the text rides in the batch's string section (B = relative offset). */
export function encodeTextChanged(target: number, text: string): Uint8Array {
  const bytes = new TextEncoder().encode(text);
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4 + 4 + bytes.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0, true);
  view.setUint32(8, 0, true);
  view.setUint32(12, 1, true);
  const pos = HEADER_SIZE;
  view.setUint8(pos, CAT_EVENT);
  view.setUint8(pos + 1, CMD_TEXT_CHANGED);
  view.setUint32(pos + 4, target, true);
  view.setUint32(pos + 8, 0, true); // b = relative string offset (first entry)
  view.setUint32(pos + 12, 0, true);
  // string section: [stringsLen u32][entryLen u32][utf8…]
  const stringsAt = HEADER_SIZE + OPCODE_SIZE;
  view.setUint32(stringsAt, 4 + bytes.length, true);
  view.setUint32(stringsAt + 4, bytes.length, true);
  out.set(bytes, stringsAt + 8);
  return out;
}

// --- focus / editing / submit / scroll / wheel ---

/** FOCUS_CHANGED (A=target, B=focused 0/1). */
export function encodeFocusChanged(target: number, focused: boolean): Uint8Array {
  return eventBatch(CMD_FOCUS_CHANGED, target, focused ? 1 : 0, 0);
}

/** EDITING_CHANGED (A=target, B=editing 0/1). */
export function encodeEditingChanged(target: number, editing: boolean): Uint8Array {
  return eventBatch(CMD_EDITING_CHANGED, target, editing ? 1 : 0, 0);
}

/** SUBMIT (A=target). */
export function encodeSubmit(target: number): Uint8Array {
  return eventBatch(CMD_SUBMIT, target, 0, 0);
}

/** SCROLL (A=target, B=offsetX f32, C=offsetY f32). */
export function encodeScroll(target: number, offsetX: number, offsetY: number): Uint8Array {
  return eventBatch(CMD_SCROLL, target, bitsFromF32(offsetX), bitsFromF32(offsetY));
}

/** WHEEL (A=target, B=deltaX f32, C=deltaY f32). */
export function encodeWheel(target: number, deltaX: number, deltaY: number): Uint8Array {
  return eventBatch(CMD_WHEEL, target, bitsFromF32(deltaX), bitsFromF32(deltaY));
}

// --- navigation (global, not node-keyed) ---

/** NAVIGATE to a URL (browser back/forward `popstate`, a deep link); the URL rides the string section. */
export function encodeNavigate(url: string): Uint8Array {
  const bytes = new TextEncoder().encode(url);
  const out = new Uint8Array(HEADER_SIZE + OPCODE_SIZE + 4 + 4 + bytes.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, 0, true);
  view.setUint32(8, 0, true);
  view.setUint32(12, 1, true);
  const pos = HEADER_SIZE;
  view.setUint8(pos, CAT_EVENT);
  view.setUint8(pos + 1, CMD_NAVIGATE);
  view.setUint16(pos + 2, FLAG_NAVIGATE_URL, true);
  view.setUint32(pos + 4, 0, true); // no target — global
  view.setUint32(pos + 8, 0, true); // b = relative string offset (first entry)
  view.setUint32(pos + 12, 0, true);
  const stringsAt = HEADER_SIZE + OPCODE_SIZE;
  view.setUint32(stringsAt, 4 + bytes.length, true);
  view.setUint32(stringsAt + 4, bytes.length, true);
  out.set(bytes, stringsAt + 8);
  return out;
}

/** NAVIGATE with no URL: a native back request (the app pops its own back-stack). */
export function encodeNavigateBack(): Uint8Array {
  return eventBatch(CMD_NAVIGATE, 0, 0, 0);
}

// --- environment (host → guest; META::ENVIRONMENT field family, spec/OPCODE.md) ---

/**
 * The platform's environment: viewport + initial route, as a batch of
 * `META::ENVIRONMENT` field opcodes (the route rides the batch's string section).
 * The DOM client sends this as its **first** message on connect and re-sends it to
 * **enrich** the environment after connect (e.g. a window resize re-emits the
 * viewport fields). The SSR host synthesizes the same message from the HTTP request.
 */
export function encodeEnvironment(width: number, height: number, route: string): Uint8Array {
  const routeBytes = new TextEncoder().encode(route);
  const out = new Uint8Array(HEADER_SIZE + 3 * OPCODE_SIZE + 4 + 4 + routeBytes.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, MAGIC, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, FLAG_HOST_TO_GUEST, true); // host (renderer) -> app
  view.setUint32(8, 0, true);
  view.setUint32(12, 3, true); // VIEWPORT_WIDTH, VIEWPORT_HEIGHT, ROUTE
  let pos = HEADER_SIZE;
  const field = (id: number, b: number) => {
    view.setUint8(pos, CAT_META);
    view.setUint8(pos + 1, CMD_ENVIRONMENT);
    view.setUint16(pos + 2, 0, true);
    view.setUint32(pos + 4, id, true);
    view.setUint32(pos + 8, b, true);
    view.setUint32(pos + 12, 0, true);
    pos += OPCODE_SIZE;
  };
  field(ENV_VIEWPORT_WIDTH, bitsFromF32(width));
  field(ENV_VIEWPORT_HEIGHT, bitsFromF32(height));
  field(ENV_ROUTE, 0); // route string at relative offset 0
  const stringsAt = pos;
  view.setUint32(stringsAt, 4 + routeBytes.length, true);
  view.setUint32(stringsAt + 4, routeBytes.length, true);
  out.set(routeBytes, stringsAt + 8);
  return out;
}