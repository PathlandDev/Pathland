import { describe, expect, it } from "vitest";
import { InputSignal, computed, effect, isSignal, signal, untracked } from "../src/index";

describe("@pathland/signals", () => {
  it("is callable and writable", () => {
    const count = signal(1);
    expect(count()).toBe(1);
    count.set(2);
    expect(count.get()).toBe(2);
    count.update((value) => value + 3);
    expect(count()).toBe(5);
    expect(isSignal(count)).toBe(true);
  });

  it("memoizes computed values and suppresses equal writes", () => {
    const count = signal(1);
    let runs = 0;
    const doubled = computed(() => {
      runs++;
      return count() * 2;
    });
    expect(doubled()).toBe(2);
    expect(doubled()).toBe(2);
    expect(runs).toBe(1);
    count.set(1);
    expect(doubled()).toBe(2);
    expect(runs).toBe(1);
    count.set(2);
    expect(doubled()).toBe(4);
    expect(runs).toBe(2);
  });

  it("flushes effects synchronously and supports cleanup", () => {
    const count = signal(0);
    const seen: number[] = [];
    const cleanups: number[] = [];
    const ref = effect(() => {
      const value = count();
      seen.push(value);
      return () => cleanups.push(value);
    });
    count.set(1);
    count.set(2);
    ref.destroy();
    count.set(3);
    expect(seen).toEqual([0, 1, 2]);
    expect(cleanups).toEqual([0, 1, 2]);
  });

  it("does not track untracked reads", () => {
    const count = signal(0);
    let runs = 0;
    effect(() => {
      runs++;
      untracked(() => count());
    });
    count.set(1);
    expect(runs).toBe(1);
  });

  it("wraps raw values and passes signals through", () => {
    const existing = signal("x");
    expect(InputSignal.from(existing)).toBe(existing);
    const fallback = InputSignal.from<string | undefined>(undefined, "offline");
    expect(fallback()).toBe("offline");
    const literal = InputSignal.from("hello");
    expect(literal()).toBe("hello");
  });

  it("detects circular computed dependencies", () => {
    let a: ReturnType<typeof computed<number>>;
    a = computed(() => a());
    expect(() => a()).toThrow(/circular/);
  });
});
