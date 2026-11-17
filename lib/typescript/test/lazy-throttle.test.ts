import { afterEach, describe, expect, it, vi } from "vitest";
import { createLazyRangeThrottle } from "../src/lazy-throttle";

afterEach(() => {
  vi.useRealTimers();
});

describe("lazy visible-range throttle", () => {
  it("reports immediately, then at most once per interval, with a trailing report", () => {
    vi.useFakeTimers();
    const calls: boolean[] = [];
    const throttle = createLazyRangeThrottle((force) => calls.push(force), 100, 150);

    throttle.scroll(); // first report is immediate
    expect(calls).toEqual([false]);

    vi.advanceTimersByTime(10);
    for (let i = 0; i < 20; i++) {
      throttle.scroll(); // suppressed; one trailing report is scheduled
    }
    expect(calls).toEqual([false]);

    vi.advanceTimersByTime(90); // t=100: trailing report
    expect(calls).toEqual([false, false]);

    vi.advanceTimersByTime(60); // t=160: final report after the last scroll
    expect(calls).toEqual([false, false, true]);

    vi.advanceTimersByTime(500);
    expect(calls).toEqual([false, false, true]);
  });

  it("a fast back-and-forth burst ends with a final report", () => {
    vi.useFakeTimers();
    const calls: boolean[] = [];
    const throttle = createLazyRangeThrottle((force) => calls.push(force), 100, 150);

    for (let i = 0; i < 7; i++) {
      throttle.scroll();
      vi.advanceTimersByTime(10);
    }
    vi.advanceTimersByTime(150);

    expect(calls.length).toBeLessThanOrEqual(4);
    expect(calls.at(-1)).toBe(true);
  });

  it("an end-of-gesture report cancels the delayed reports", () => {
    vi.useFakeTimers();
    const calls: boolean[] = [];
    const throttle = createLazyRangeThrottle((force) => calls.push(force), 100, 150);

    throttle.scroll();
    vi.advanceTimersByTime(20);
    throttle.scroll();
    throttle.end();

    expect(calls).toEqual([false, true]);
    vi.advanceTimersByTime(1000);
    expect(calls).toEqual([false, true]);
  });
});
