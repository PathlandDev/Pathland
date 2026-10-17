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
async function loadWorker(
  opts: {
    failStore?: boolean;
    windowClients?: Array<{ id: string; url: string }>;
  } = {},
) {
  vi.resetModules(); // re-execute sw.js so its handlers register against this test's stubs
  const handlers: Record<string, (event: SwEvent) => void> = {};
  const store = new Map<string, string>();
  const keys: string[] = []; // every cache key the worker touches
  const cache = {
    put: async (req: { url: string }, res: { text: () => Promise<string> }) => {
      keys.push(req.url);
      store.set(req.url, await res.text());
    },
    match: async (req: { url: string }) => {
      keys.push(req.url);
      return store.has(req.url) ? { text: async () => store.get(req.url)! } : undefined;
    },
  };
  vi.stubGlobal("self", {
    // A real worker always has a location; the worker builds absolute cache keys from it.
    location: { origin: "http://x" },
    addEventListener: (type: string, fn: (event: SwEvent) => void) => {
      handlers[type] = fn;
    },
    skipWaiting: () => undefined,
    clients: {
      claim: () => undefined,
      matchAll: async () => opts.windowClients ?? [],
    },
  });
  vi.stubGlobal("caches", {
    open: async () => {
      if (opts.failStore) throw new Error("cache unavailable");
      return cache;
    },
  });
  // @ts-expect-error — a plain-JS worker script, intentionally untyped
  await import("../sw.js");
  return { handlers, keys };
}

afterEach(() => vi.unstubAllGlobals());

describe("sw.js", () => {
  it("attaches the registered window id to a top-level navigation", async () => {
    const { handlers, keys } = await loadWorker();
    // The page registers its id (seeded on boot).
    await handlers.message!({
      data: { type: "pathland-wid", wid: "W1" },
      source: { id: "c1" },
      waitUntil: (p) => void p,
    });
    // The id is stored under an ABSOLUTE key — Safari rejects a relative Request URL.
    expect(keys[0]).toBe("http://x/__pathland-wid/c1");

    const fetched: string[] = [];
    const inits: Array<{ mode?: string } | undefined> = [];
    vi.stubGlobal("fetch", async (input: unknown, init?: { mode?: string }) => {
      // Mirror the browser: a script cannot construct a request with mode "navigate".
      // Passing the navigation Request as the init (the bug) throws here.
      if (init && init.mode === "navigate") {
        throw new TypeError("The 'navigate' mode is not allowed");
      }
      inits.push(init);
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
    expect(inits[0]?.mode).toBe("same-origin");
  });

  it("does not intercept non-navigation requests", async () => {
    const { handlers } = await loadWorker();
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
    const { handlers } = await loadWorker();
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

  it("resolves the id via the only window client when the engine reports no clientId (WebKit)", async () => {
    const { handlers } = await loadWorker({ windowClients: [{ id: "w1", url: "http://x/app" }] });
    await handlers.message!({
      data: { type: "pathland-wid", wid: "W1" },
      source: { id: "w1" },
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
      clientId: "", // WebKit leaves clientId empty for navigations
      resultingClientId: "w2",
      respondWith: (p) => {
        responded = p;
      },
    });
    await responded;
    expect(fetched[0]).toBe("http://x/app?wid=W1");
  });

  it("disambiguates between tabs by matching the request URL", async () => {
    const { handlers } = await loadWorker({
      windowClients: [
        { id: "a", url: "http://x/a" },
        { id: "b", url: "http://x/b" },
      ],
    });
    for (const [id, wid] of [["a", "WA"], ["b", "WB"]] as const) {
      await handlers.message!({
        data: { type: "pathland-wid", wid },
        source: { id },
        waitUntil: (p) => void p,
      });
    }

    const fetched: string[] = [];
    vi.stubGlobal("fetch", async (input: unknown) => {
      fetched.push(typeof input === "string" ? input : (input as { url: string }).url);
      return { ok: true };
    });
    let responded: Promise<unknown> | undefined;
    handlers.fetch!({
      request: { mode: "navigate", url: "http://x/b" },
      clientId: "",
      respondWith: (p) => {
        responded = p;
      },
    });
    await responded;
    expect(fetched[0]).toBe("http://x/b?wid=WB");
  });

  it("never guesses between tabs: an ambiguous navigation fails open", async () => {
    const { handlers } = await loadWorker({
      windowClients: [
        { id: "a", url: "http://x/a" },
        { id: "b", url: "http://x/b" },
      ],
    });
    for (const [id, wid] of [["a", "WA"], ["b", "WB"]] as const) {
      await handlers.message!({
        data: { type: "pathland-wid", wid },
        source: { id },
        waitUntil: (p) => void p,
      });
    }

    const fetched: string[] = [];
    vi.stubGlobal("fetch", async (input: unknown) => {
      fetched.push(typeof input === "string" ? input : (input as { url: string }).url);
      return { ok: true };
    });
    let responded: Promise<unknown> | undefined;
    handlers.fetch!({
      request: { mode: "navigate", url: "http://x/other" },
      clientId: "",
      respondWith: (p) => {
        responded = p;
      },
    });
    await responded;
    expect(fetched[0]).toBe("http://x/other"); // no id -> unscoped, never another tab's
  });

  it("warns (instead of failing silently) when the id store is unavailable", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const { handlers } = await loadWorker({ failStore: true });

    await handlers.message!({
      data: { type: "pathland-wid", wid: "W1" },
      source: { id: "c1" },
      waitUntil: (p) => void p,
    });
    expect(warn).toHaveBeenCalled();

    // A navigation whose id cannot be recalled still fails open, but visibly.
    const fetched: string[] = [];
    vi.stubGlobal("fetch", async (input: unknown) => {
      fetched.push(typeof input === "string" ? input : (input as { url: string }).url);
      return { ok: true };
    });
    let responded: Promise<unknown> | undefined;
    handlers.fetch!({
      request: { mode: "navigate", url: "http://x/app" },
      clientId: "c1",
      respondWith: (p) => {
        responded = p;
      },
    });
    await responded;
    expect(fetched[0]).toBe("http://x/app");
    warn.mockRestore();
  });
});
