import { afterEach, describe, expect, it, vi } from "vitest";

interface SwEvent {
  data?: unknown;
  source?: { id?: string };
  request?: { mode: string; url: string };
  clientId?: string;
  resultingClientId?: string;
  waitUntil?: (p: Promise<unknown>) => void;
  respondWith?: (p: Promise<unknown>) => void;
}

/** Load the plain-JS worker with fake SW globals; return its event handlers. */
async function loadWorker() {
  vi.resetModules(); // re-execute sw.js so its handlers register against this test's stubs
  const handlers: Record<string, (event: SwEvent) => void> = {};
  const store = new Map<string, string>();
  const cache = {
    put: async (req: { url: string }, res: { text: () => Promise<string> }) => {
      store.set(req.url, await res.text());
    },
    match: async (req: { url: string }) =>
      store.has(req.url) ? { text: async () => store.get(req.url)! } : undefined,
  };
  vi.stubGlobal("self", {
    addEventListener: (type: string, fn: (event: SwEvent) => void) => {
      handlers[type] = fn;
    },
    skipWaiting: () => undefined,
    clients: { claim: () => undefined },
  });
  vi.stubGlobal("caches", { open: async () => cache });
  // @ts-expect-error — a plain-JS worker script, intentionally untyped
  await import("../sw.js");
  return handlers;
}

afterEach(() => vi.unstubAllGlobals());

describe("sw.js", () => {
  it("attaches the registered window id to a top-level navigation", async () => {
    const handlers = await loadWorker();
    // The page registers its id (seeded on boot).
    await handlers.message!({
      data: { type: "pathland-wid", wid: "W1" },
      source: { id: "c1" },
      waitUntil: (p) => void p,
    });

    const fetched: string[] = [];
    vi.stubGlobal("fetch", async (input: unknown) => {
      fetched.push(typeof input === "string" ? input : (input as { url: string }).url);
      return { ok: true };
    });

    let responded: Promise<unknown> | undefined;
    handlers.fetch!({
      request: { mode: "navigate", url: "http://x/app" },
      clientId: "c1",
      resultingClientId: "c2",
      respondWith: (p) => {
        responded = p;
      },
    });
    await responded;
    expect(fetched[0]).toBe("http://x/app?wid=W1");
  });

  it("does not intercept non-navigation requests", async () => {
    const handlers = await loadWorker();
    let responded: Promise<unknown> | undefined;
    handlers.fetch!({
      request: { mode: "cors", url: "http://x/a.js" },
      respondWith: (p) => {
        responded = p;
      },
    });
    expect(responded).toBeUndefined();
  });

  it("passes a navigation through unchanged when the tab has no registered id", async () => {
    const handlers = await loadWorker();
    const fetched: string[] = [];
    vi.stubGlobal("fetch", async (input: unknown) => {
      fetched.push(typeof input === "string" ? input : (input as { url: string }).url);
      return { ok: true };
    });

    const request = { mode: "navigate", url: "http://x/app" };
    let responded: Promise<unknown> | undefined;
    handlers.fetch!({
      request,
      clientId: "unknown",
      respondWith: (p) => {
        responded = p;
      },
    });
    await responded;
    expect(fetched[0]).toBe("http://x/app"); // fail open: the original request
  });
});
