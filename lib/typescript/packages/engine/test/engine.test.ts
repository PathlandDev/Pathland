import { describe, expect, it } from "vitest";
import {
  CAT_PARAMETER,
  CMD_MEDIA_TIME_UPDATED,
  CMD_SET_TEXT,
  COMPONENT_TEXT,
  COMPONENT_VSTACK,
  PROP_FONT_SIZE,
  PROP_MEDIA_SOURCE,
  PROP_PLAYBACK_STATE,
  PROP_SPACING,
  f32bits,
  parseBatch,
  readString,
} from "@pathland/protocol";
import { signal } from "@pathland/signals";
import { MemoryStateStore, PersistentState } from "@pathland/state";
import { Button, MediaController, Slider, VStack, Text, TextField, fontSize, type AppContext } from "@pathland/view";
import { Emitter, FrameOpcodeSink, InputDispatcher } from "../src/index";

function context(route = "/"): AppContext {
  return {
    state: new PersistentState(new MemoryStateStore(), "/test"),
    platform: { now: () => 0, random: () => 0 },
    route: signal(route),
  };
}

function sinkWithFrames(): { sink: FrameOpcodeSink; frames: Uint8Array[] } {
  const frames: Uint8Array[] = [];
  const sink = new FrameOpcodeSink();
  sink.onFrame((frame) => frames.push(frame));
  return { sink, frames };
}

describe("@pathland/engine", () => {
  it("assigns deterministic ids and emits the snapshot in canonical order", () => {
    const { sink, frames } = sinkWithFrames();
    const emitter = new Emitter(sink);
    const name = signal("Ada");
    const result = emitter.mount(
      VStack({ spacing: 12 }, () => [Text({ text: name }).modifiers(fontSize(18))]),
      context(),
    );

    expect(result.rootId).toBe(1);
    expect(frames).toHaveLength(1);
    const batch = parseBatch(frames[0]!);
    expect(batch.opcodes.map((op) => [op.category, op.command, op.a, op.b, op.c])).toEqual([
      [0x01, 0x01, 1, COMPONENT_VSTACK, 0],
      [0x02, 0x01, 1, (0x04 << 16) | PROP_SPACING, f32bits(12)],
      [0x01, 0x01, 2, COMPONENT_TEXT, 0],
      [0x02, 0x01, 2, (0x04 << 16) | PROP_FONT_SIZE, f32bits(18)],
      [0x02, 0x03, 2, 0, 0],
      [0x01, 0x03, 1, 2, 0xffffffff],
    ]);
    expect(readString(batch.strings, 0)).toBe("Ada");
  });

  it("emits only SET_TEXT for a text signal change and zero opcodes when unchanged", () => {
    const { sink, frames } = sinkWithFrames();
    const emitter = new Emitter(sink);
    const name = signal("Ada");
    emitter.mount(Text({ text: name }), context());

    name.set("Grace");
    expect(frames).toHaveLength(2);
    const delta = parseBatch(frames[1]!);
    expect(delta.opcodes).toHaveLength(1);
    expect(delta.opcodes[0]).toMatchObject({ category: CAT_PARAMETER, command: CMD_SET_TEXT, a: 1 });
    expect(readString(delta.strings, delta.opcodes[0]!.b)).toBe("Grace");

    name.set("Grace");
    expect(frames).toHaveLength(2);
  });

  it("re-emits the whole tree on renderFull", () => {
    const { sink, frames } = sinkWithFrames();
    const emitter = new Emitter(sink);
    emitter.mount(VStack({ spacing: 4 }, () => [Text({ text: "hi" })]), context());
    emitter.renderFull();
    expect(frames).toHaveLength(2);
    expect(parseBatch(frames[1]!).opcodes).toHaveLength(5);
  });

  it("binds TextField text and routes TEXT_CHANGED into it", () => {
    const { sink, frames } = sinkWithFrames();
    const emitter = new Emitter(sink);
    const query = signal("");
    const result = emitter.mount(TextField({ placeholder: "Search", text: query }), context());
    expect(result.textInputs.get(1)).toBe(query);

    const sender = new InputDispatcher(result, signal("/"));
    sender.dispatch([
      { category: 0x03, command: 0x07, flags: 0, a: 1, b: 0, c: 0, text: "pa" },
    ]);
    expect(query()).toBe("pa");
    expect(frames).toHaveLength(2);
    const delta = parseBatch(frames[1]!);
    expect(delta.opcodes).toHaveLength(1);
    expect(delta.opcodes[0]!.command).toBe(CMD_SET_TEXT);
    expect(readString(delta.strings, delta.opcodes[0]!.b)).toBe("pa");
  });

  it("materializes a media controller and routes MEDIA_* reports", () => {
    const { sink, frames } = sinkWithFrames();
    const emitter = new Emitter(sink);
    const player = new MediaController().source("/audio/track.mp3");
    const result = emitter.mount(player.attach(), context());
    expect(result.mediaControllers.get(1)).toBe(player);
    const snapshot = parseBatch(frames[0]!);
    const source = snapshot.opcodes.find((op) => op.b >>> 16 === 0x05 && (op.b & 0xffff) === PROP_MEDIA_SOURCE);
    expect(source).toBeDefined();
    expect(readString(snapshot.strings, source!.c)).toBe("/audio/track.mp3");

    player.playing.set(true);
    expect(frames).toHaveLength(2);
    const delta = parseBatch(frames[1]!);
    expect(delta.opcodes[0]).toMatchObject({ b: (0x02 << 16) | PROP_PLAYBACK_STATE, c: 1 });

    const sender = new InputDispatcher(result, signal("/"));
    sender.dispatch([
      { category: 0x03, command: CMD_MEDIA_TIME_UPDATED, flags: 0, a: 1, b: f32bits(12.5), c: 0 },
    ]);
    expect(player.time()).toBe(12.5);
  });

  it("binds slider values to a writable signal", () => {
    const { sink } = sinkWithFrames();
    const emitter = new Emitter(sink);
    const value = signal(0.5);
    const result = emitter.mount(Slider({ value }), context());
    expect(result.valueInputs.get(1)).toBe(value);
    const sender = new InputDispatcher(result, signal("/"));
    sender.dispatch([
      { category: 0x03, command: 0x06, flags: 0, a: 1, b: f32bits(0.8), c: 0 },
    ]);
    expect(value()).toBeCloseTo(0.8, 5);
  });

  it("routes button taps", () => {
    const { sink } = sinkWithFrames();
    const emitter = new Emitter(sink);
    let taps = 0;
    const result = emitter.mount(Button({ label: "Go", action: () => taps++ }), context());
    const sender = new InputDispatcher(result, signal("/"));
    sender.dispatch([{ category: 0x03, command: 0x03, flags: 0, a: result.rootId, b: 0, c: 0 }]);
    expect(taps).toBe(1);
  });
});
