import { afterEach, describe, expect, it, vi } from "vitest";
import { setupServiceWorker } from "../src/service-worker";

/** A minimal fake ServiceWorkerContainer capturing register/unregister/postMessage. */
function fakeServiceWorker() {
  const registered: Array<{ url: string; options?: RegistrationOptions }> = [];
  const unregistered: string[] = [];
  const messages: Array<{ type: string; wid?: string }> = [];
  const active = {
    postMessage: (message: unknown) => messages.push(message as { type: string }),
  };
  const registrations = [{ unregister: async () => { unregistered.push("r"); return true; } }];
  const container = {
    controller: active,
    getRegistrations: async () => registrations,
    register: async (url: string, options?: RegistrationOptions) => {
      registered.push({ url, options });
      return { active, addEventListener: () => undefined };
    },
  };
  return { container, registered, unregistered, messages };
}

function install(container: unknown): void {
  Object.defineProperty(navigator, "serviceWorker", { value: container, configurable: true });
}

afterEach(() => {
  delete (document.documentElement.dataset as { pathlandServiceWorker?: string }).pathlandServiceWorker;
  // Drop the stub so other tests see a clean navigator.
  Reflect.deleteProperty(navigator as unknown as Record<string, unknown>, "serviceWorker");
});

describe("setupServiceWorker", () => {
  it("registers the worker at the root scope and hands it the window id when enabled", async () => {
    const fake = fakeServiceWorker();
    install(fake.container);
    document.documentElement.dataset.pathlandServiceWorker = "true";

    setupServiceWorker("W1", "/_pathland");

    await vi.waitFor(() => expect(fake.registered).toHaveLength(1));
    expect(fake.registered[0]).toEqual({ url: "/_pathland/sw.js", options: { scope: "/" } });
    await vi.waitFor(() => expect(fake.messages).toEqual([{ type: "pathland-wid", wid: "W1" }]));
  });

  it("unregisters any existing worker when the server does not opt in (the kill switch)", async () => {
    const fake = fakeServiceWorker();
    install(fake.container);

    setupServiceWorker("W1", "/_pathland");

    await vi.waitFor(() => expect(fake.unregistered).toHaveLength(1));
    expect(fake.registered).toHaveLength(0);
    expect(fake.messages).toHaveLength(0);
  });
});
