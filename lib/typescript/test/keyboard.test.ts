// Keyboard capture must be strictly opt-in: only a node whose
// `data-event-listeners` mask contains KEY_DOWN may receive key events; the
// client must not preventDefault on an ordinary native control that never
// declared the bit. Regression: a slider without a mask (e.g. volume) had its
// arrow keys swallowed by the permissive fallback.

import { beforeEach, describe, expect, it, vi } from "vitest";

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
}

beforeEach(() => {
  FakeSocket.instances = [];
  sessionStorage.clear();
  history.replaceState(null, "", "/");
  document.documentElement.removeAttribute("data-pathland-base");
  document.documentElement.innerHTML =
    '<head></head><body><label data-pathland-id="1" class="pathland-slider">' +
    '<input id="volume" type="range" min="0" max="1" step="any" value="0.7"></label></body>';
  (window as unknown as { WebSocket: unknown }).WebSocket = FakeSocket;
  Object.defineProperty(globalThis.crypto ?? window.crypto, "randomUUID", {
    value: () => "00000000-0000-4000-8000-000000000001",
    configurable: true,
  });
});

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

function pressArrow(): KeyboardEvent {
  const event = new KeyboardEvent("keydown", {
    key: "ArrowRight",
    bubbles: true,
    cancelable: true,
  });
  document.getElementById("volume")!.dispatchEvent(event);
  return event;
}

describe("keyboard capture gating", () => {
  it("does not swallow native arrows unless KEY_DOWN was declared", async () => {
    const socket = await boot();
    socket.emitOpen();
    socket.sent.splice(0);
    const input = document.getElementById("volume") as HTMLInputElement;
    const node = input.closest<HTMLElement>("[data-pathland-id]")!;
    input.focus();
    expect(document.activeElement).toBe(input);

    // No mask: an ordinary native slider — let the browser move it.
    const unconfigured = pressArrow();
    expect(unconfigured.defaultPrevented).toBe(false);
    expect(socket.sent).toHaveLength(0);

    // Other listener bits (EDITING only) must not imply KEY_DOWN either.
    node.setAttribute("data-event-listeners", "64");
    const editingOnly = pressArrow();
    expect(editingOnly.defaultPrevented).toBe(false);
    expect(socket.sent).toHaveLength(0);

    // Explicit KEY_DOWN opts in: report it and suppress the native default.
    node.setAttribute("data-event-listeners", "8");
    const captured = pressArrow();
    expect(captured.defaultPrevented).toBe(true);
    expect(socket.sent).toHaveLength(1);
  });
});
