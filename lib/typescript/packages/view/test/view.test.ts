import { describe, expect, it } from "vitest";
import {
  COMPONENT_MEDIA_CONTROLLER,
  COMPONENT_TEXT,
  PROP_BACKGROUND_COLOR,
  PROP_COLOR,
  PROP_PADDING,
  VAL_COLOR,
} from "@pathland/protocol";
import { signal } from "@pathland/signals";
import { MemoryStateStore, PersistentState } from "@pathland/state";
import {
  Text,
  VStack,
  background,
  colorBits,
  element,
  MediaController,
  foregroundStyle,
  padding,
  useAppContext,
  view,
  withAppContext,
} from "../src/index";

describe("@pathland/view", () => {
  it("chains modifiers immutably", () => {
    const base = element({ component: 0x10 });
    const one = base.modifiers(padding(8));
    const two = one.modifiers(background("#ffffff"));
    expect(base.mods).toEqual([]);
    expect(one.mods.map((mod) => mod.property)).toEqual([PROP_PADDING]);
    expect(two.mods.map((mod) => mod.property)).toEqual([PROP_PADDING, PROP_BACKGROUND_COLOR]);
    expect(two.mods[1]).toMatchObject({ valueType: VAL_COLOR, value: 0xffffffff });
  });

  it("parses colors and foreground styles", () => {
    expect(colorBits("#111827")).toBe(0xff111827);
    expect(colorBits("#abc")).toBe(0xffaabbcc);
    expect(foregroundStyle("#10B981")).toMatchObject({ property: PROP_COLOR, value: 0xff10b981 });
  });

  it("builds primitive elements with reactive text", () => {
    const name = signal("Ada");
    const text = Text({ text: name });
    expect(text.component).toBe(COMPONENT_TEXT);
    expect(text.textSignal?.()).toBe("Ada");
    const stack = VStack({ spacing: 12 }, () => [text]);
    expect(typeof stack.children).toBe("function");
  });

  it("exposes the app context through the mount stack", () => {
    const context = {
      state: new PersistentState(new MemoryStateStore(), "/w"),
      platform: { now: () => 1, random: () => 0 },
      route: signal("/"),
    };
    expect(withAppContext(context, () => useAppContext())).toBe(context);
    expect(() => useAppContext()).toThrow(/outside/);
  });

  it("attaches a media controller as a non-visual element", () => {
    const player = new MediaController().source("/audio/track.mp3");
    const marker = player.attach();
    expect(marker.component).toBe(COMPONENT_MEDIA_CONTROLLER);
    expect(marker.mediaController).toBe(player);
    expect(player.sourceSignal()).toBe("/audio/track.mp3");
  });

  it("treats the view decorator as a marker", () => {
    class Example {}
    expect(view(Example)).toBe(Example);
  });
});
