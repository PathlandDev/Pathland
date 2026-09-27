// Per-window identity (wid): the DOM client keeps a window id in sessionStorage (fresh
// per window, kept across reloads of the same tab), sends it as `?wid=` on the WebSocket
// URL so the server can scope persisted state per window, AND reflects it into the page
// URL (`?wid=…`) so the SSR request carries it too. A reload whose URL already carries the
// wid gets the latest state from the HTML directly (no RESYNC); only a reload whose URL
// lacked it (a typed URL, or a first visit before the client ran) requests a RESYNC to
// restore the persisted state. Two windows therefore never share a UI model or state.

import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildBatch, stringEntry } from "./plpl.test";
import { CAT_STYLE, CMD_SET_PROPERTY, PROP_ROUTE, VAL_STRING } from "../src/constants";

const WID_KEY = "pathland.wid";

/** Minimal fake WebSocket capturing the URL + outbound bytes (happy-dom has no WebSocket global). */
class FakeSocket {
  static instances: FakeSocket[] = [];
  readyState = 0;
  binaryType = "";
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readonly sent: Uint8Array[] = [];
  constructor(readonly url: string) {
    FakeSocket.instances.push(this);
  }
  send(data: Uint8Array): void {
    this.sent.push(data);
  }
  close(): void {
    this.readyState = 3;
  }
  emitOpen(): void {
    this.readyState = 1;
    this.onopen?.();
  }
  emitMessage(data: unknown): void {
    this.onmessage?.({ data });
  }
}

let widCounter = 0;

beforeEach(() => {
  FakeSocket.instances = [];
  widCounter = 0;
  sessionStorage.clear();
  history.replaceState(null, "", "/"); // a clean URL (no wid) for every test
  document.documentElement.removeAttribute("data-pathland-base");
  document.documentElement.innerHTML =
    "<head></head><body><div data-pathland-id=\"1\">Hi</div></body>";
  (window as unknown as { WebSocket: unknown }).WebSocket = FakeSocket;
  // Deterministic ids (happy-dom's crypto may lack randomUUID).
  Object.defineProperty(globalThis.crypto ?? window.crypto, "randomUUID", {
    value: () => "00000000-0000-4000-8000-0000000000" + String(++widCounter),
    configurable: true,
  });
});

/** Import the client (it auto-boots) and return the WebSocket it opened. */
async function boot(): Promise<FakeSocket> {
  vi.resetModules();
  await import("../src/index");
  if (document.readyState === "loading") {
    document.dispatchEvent(new Event("DOMContentLoaded"));
  }
  const socket = FakeSocket.instances.at(-1);
  if (!socket) {
    throw new Error("client did not open a WebSocket");
  }
  return socket;
}

describe("per-window id (wid)", () => {
  it("a fresh window generates a wid, stores it, reflects it into the URL, and connects with it — no resync", async () => {
    const socket = await boot();
    const wid = new URL(socket.url).searchParams.get("wid");
    expect(wid).toBeTruthy();
    expect(sessionStorage.getItem(WID_KEY)).toBe(wid);
    expect(new URL(location.href).searchParams.get("wid")).toBe(wid), "the page URL carries the wid for the next SSR";

    socket.emitOpen();
    // Environment first; a fresh window has no persisted state, so no RESYNC.
    expect(socket.sent).toHaveLength(1);
  });

  it("a reload whose URL already carries the wid reuses it and does NOT resync (the HTML is already the latest)", async () => {
    const first = await boot();
    const wid = sessionStorage.getItem(WID_KEY)!;
    first.emitOpen();
    expect(new URL(location.href).searchParams.get("wid")).toBe(wid);

    // Reload: sessionStorage AND the URL keep the wid → SSR rendered this window's
    // persisted state, so no RESYNC.
    const second = await boot();
    expect(new URL(second.url).searchParams.get("wid")).toBe(wid);
    second.emitOpen();
    expect(second.sent).toHaveLength(1);
  });

  it("a reload whose URL lacks the wid (a typed URL) requests RESYNC to restore its state", async () => {
    const first = await boot();
    const wid = sessionStorage.getItem(WID_KEY)!;
    first.emitOpen();

    // Simulate a typed URL: the wid survives in sessionStorage but the URL was reset.
    history.replaceState(null, "", "/kitchen");
    const second = await boot();
    expect(new URL(second.url).searchParams.get("wid")).toBe(wid);
    second.emitOpen();
    // Environment + RESYNC (this load rendered SSR defaults; restore persisted state).
    expect(second.sent).toHaveLength(2);
  });

  it("a mounted app mirrors an app-relative ROUTE into its full mounted URL", async () => {
    document.documentElement.dataset.pathlandBase = "/app2/_pathland";
    const socket = await boot();
    const wid = new URL(socket.url).searchParams.get("wid");

    // The server emits the app-relative route as ROUTE on the nav slot (node 1); the
    // client must push the app's REAL address — the mount prefix prepended.
    socket.emitMessage(
      buildBatch(
        [[CAT_STYLE, CMD_SET_PROPERTY, 0, 1, (VAL_STRING << 16) | PROP_ROUTE, 0]],
        stringEntry("/home"),
      ).buffer as ArrayBuffer,
    );
    expect(new URL(location.href).pathname).toBe("/app2/home");
    expect(new URL(location.href).searchParams.get("wid")).toBe(wid);
  });

  it("a different window gets a different wid", async () => {
    const first = await boot();
    sessionStorage.clear(); // a NEW window/tab starts with empty sessionStorage
    history.replaceState(null, "", "/");
    const second = await boot();
    const widA = new URL(first.url).searchParams.get("wid");
    const widB = new URL(second.url).searchParams.get("wid");
    expect(widA).toBeTruthy();
    expect(widB).toBeTruthy();
    expect(widA).not.toBe(widB);
  });
});