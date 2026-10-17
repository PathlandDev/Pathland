// The Pathland per-window-id service worker (issue #155).
//
// A top-level navigation is issued by the browser before any page JS runs, so the
// server cannot see the tab's `wid` (it lives in sessionStorage + the WebSocket URL,
// never in the page URL). This worker attaches it to navigations, so the SSR can
// render the right persisted-state scope on the first paint — without the id ever
// appearing in the address bar.
//
// It is strictly pass-through: it never caches responses and it fails open (any
// error -> the original request). Non-navigation requests are untouched.
//
// The id is keyed by the tab's client id, so two tabs never share a scope. The map
// lives in the Cache API (the worker is evicted when idle) and is seeded by the page
// on boot (`postMessage`) and carried across documents via `resultingClientId`.

const STORE = "pathland-sw";
const KEY_PREFIX = "/__pathland-wid/";

async function remember(clientId, wid) {
  if (!clientId || !wid) return;
  try {
    const cache = await caches.open(STORE);
    await cache.put(new Request(KEY_PREFIX + clientId), new Response(wid));
  } catch (_) {
    // best effort — a missing id just means the SSR renders defaults
  }
}

async function recall(clientId) {
  if (!clientId) return null;
  try {
    const cache = await caches.open(STORE);
    const hit = await cache.match(new Request(KEY_PREFIX + clientId));
    return hit ? await hit.text() : null;
  } catch (_) {
    return null;
  }
}

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

// The page registers its id on boot (belt-and-braces for the first, uncontrolled
// load — later navigations carry it via `resultingClientId`).
self.addEventListener("message", (event) => {
  const data = event.data;
  if (!data || data.type !== "pathland-wid") return;
  const clientId = event.source && event.source.id;
  event.waitUntil(remember(clientId, data.wid));
});

self.addEventListener("fetch", (event) => {
  if (event.request.mode !== "navigate") return; // pass everything else through
  event.respondWith((async () => {
    try {
      const wid = await recall(event.clientId);
      if (!wid) return fetch(event.request); // fail open: no id -> the plain request
      const url = new URL(event.request.url);
      url.searchParams.set("wid", wid);
      const response = await fetch(url.toString(), event.request);
      // Carry the id to the next document in this tab, so a reload keeps resolving
      // even if the client id changes across documents.
      await remember(event.resultingClientId, wid);
      return response;
    } catch (_) {
      return fetch(event.request); // never break a navigation
    }
  })());
});
