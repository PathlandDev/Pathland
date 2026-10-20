// Per-window identity (wid): the DOM client keeps a window id in sessionStorage (fresh
// per window, kept across reloads of the same tab) and sends it as `?wid=` on the
// WebSocket URL so the server can scope persisted state per window. It is NEVER
// reflected into the page URL — the address bar stays clean, and the SSR request has no
// wid (it renders defaults). A fresh window therefore needs no RESYNC (its defaults match
// the fresh session's defaults); a same-tab reload (or a load whose URL carried a stale
// wid) requests a RESYNC to restore the window's persisted state. Two windows therefore
// never share a UI model or state.

import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildBatch, stringEntry } from "./plpl.test";
import { CAT_PARAMETER, CMD_SET_PROPERTY, PROP_ROUTE, VAL_STRING } from "../src/constants";

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
  it("a fresh window generates a wid, stores it, and connects with it — no URL wid, no resync", async () => {
    const socket = await boot();
    const wid = new URL(socket.url).searchParams.get("wid");
    expect(wid).toBeTruthy();
    expect(sessionStorage.getItem(WID_KEY)).toBe(wid);
    // The wid never reaches the page URL (clean address bar / history / referrer).
    expect(new URL(location.href).searchParams.has("wid")).toBe(false);

    socket.emitOpen();
    // Environment first; a fresh window has no persisted state, so no RESYNC.
    expect(socket.sent).toHaveLength(1);
  });

  it("a same-tab reload reuses the wid and requests RESYNC (SSR rendered defaults)", async () => {
    const first = await boot();
    const wid = sessionStorage.getItem(WID_KEY)!;
    first.emitOpen();
    expect(new URL(location.href).searchParams.has("wid")).toBe(false);

    // Reload: sessionStorage keeps the wid → the WS connects with it, but the SSR (no
    // URL wid) rendered defaults, so the client re-syncs this window's persisted state.
    const second = await boot();
    expect(new URL(second.url).searchParams.get("wid")).toBe(wid);
    second.emitOpen();
    expect(second.sent).toHaveLength(2); // environment + RESYNC
  });

  it("a load whose URL carries a stale wid strips it from the URL and requests RESYNC", async () => {
    history.replaceState(null, "", "/kitchen?wid=stale-wid&keep=1");
    const socket = await boot();
    // The stale wid is cleaned out of the address bar; other params survive.
    const url = new URL(location.href);
    expect(url.searchParams.has("wid")).toBe(false);
    expect(url.searchParams.get("keep")).toBe("1");
    socket.emitOpen();
    // The SSR rendered with the stale wid's scope → re-sync to the fresh session's scope.
    expect(socket.sent).toHaveLength(2);
  });

  it("a mounted app mirrors an app-relative ROUTE into its full mounted URL (no wid)", async () => {
    document.documentElement.dataset.pathlandBase = "/app2/_pathland";
    const socket = await boot();
    const wid = new URL(socket.url).searchParams.get("wid");
    expect(wid).toBeTruthy();

    // The server emits the app-relative route as ROUTE on the nav slot (node 1); the
    // client must push the app's REAL address — the mount prefix prepended.
    socket.emitMessage(
      buildBatch(
        [[CAT_PARAMETER, CMD_SET_PROPERTY, 0, 1, (VAL_STRING << 16) | PROP_ROUTE, 0]],
        stringEntry("/home"),
      ).buffer as ArrayBuffer,
    );
    expect(new URL(location.href).pathname).toBe("/app2/home");
    expect(new URL(location.href).searchParams.has("wid")).toBe(false);
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