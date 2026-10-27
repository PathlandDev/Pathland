import { describe, expect, it } from "vitest";
import { MemoryStateStore, PersistentState, State } from "../src/index";

describe("@pathland/state", () => {
  it("loads a preloaded value before the initializer is used", () => {
    const state = new PersistentState(new MemoryStateStore(), "/w1", { count: 7 });
    expect(state.state("count", 0)()).toBe(7);
  });

  it("saves on change and scopes keys by session", () => {
    const store = new MemoryStateStore();
    const first = new PersistentState(store, "/w1");
    const count = first.state("count", 0);
    count.set(3);
    expect(store.load("/w1")).toEqual({ count: 3 });
    expect(store.load("/w2")).toEqual({});
  });

  it("auto-connects State fields by property name", () => {
    const store = new MemoryStateStore();
    store.save("/w1", "query", "hello");
    const view = new class {
      query = new State("");
      other = new State(1, "explicit");
    }();
    const state = new PersistentState(store, "/w1", { query: "hello" });
    state.connect(view);
    expect(view.query.signal()).toBe("hello");
    expect(view.query.key).toBe("query");
    expect(view.other.key).toBe("explicit");
  });

  it("persists explicit keys", () => {
    const store = new MemoryStateStore();
    const state = new PersistentState(store, "/w1");
    const value = state.state("theme", "light");
    value.set("dark");
    expect(store.load("/w1")).toEqual({ theme: "dark" });
  });

  it("stops saving after close", () => {
    const store = new MemoryStateStore();
    const state = new PersistentState(store, "/w1");
    const count = state.state("count", 0);
    state.close();
    count.set(9);
    expect(store.load("/w1")).toEqual({ count: 0 });
  });
});
