// The per-window-id service worker wiring (issue #155). Kept in its own module so it
// is unit-testable without booting the whole client.

/**
 * Opt into (or out of) the per-window-id service worker. The server emits
 * `data-pathland-service-worker` on `<html>` when it wants the worker:
 *
 * - present -> register `{base}/sw.js` (root scope) and hand it this window's id, so
 *   the worker can attach the id to the next navigation and the SSR can render the
 *   right persisted-state scope on the first paint;
 * - absent -> unregister any existing worker (the kill switch — a server that turns
 *   the feature off must also remove a previously installed worker).
 *
 * Best-effort throughout: a host without the worker keeps today's behaviour (the SSR
 * renders defaults and the live connection corrects them).
 */
export function setupServiceWorker(wid: string, base: string): void {
  if (!("serviceWorker" in navigator)) {
    return;
  }
  if (document.documentElement.dataset.pathlandServiceWorker == null) {
    void navigator.serviceWorker
      .getRegistrations()
      .then((regs) => Promise.all(regs.map((reg) => reg.unregister())))
      .catch(() => undefined);
    return;
  }
  void navigator.serviceWorker
    .register(`${base}/sw.js`, { scope: "/" })
    .then((reg) => {
      const post = (): void => {
        const target = reg.active ?? navigator.serviceWorker.controller;
        target?.postMessage({ type: "pathland-wid", wid });
      };
      post();
      reg.addEventListener?.("updatefound", post);
    })
    .catch(() => undefined);
}
