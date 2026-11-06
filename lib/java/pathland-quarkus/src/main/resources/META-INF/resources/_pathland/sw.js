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

// The cache key must be an ABSOLUTE URL: Safari has thrown on a relative URL in the
// worker's Request constructor (and the throw used to be swallowed silently).
function widKey(clientId) {
  return new Request(new URL(KEY_PREFIX + clientId, self.location.origin).href);
}

async function remember(clientId, wid) {
  if (!clientId || !wid) return;
  try {
    const cache = await caches.open(STORE);
    await cache.put(widKey(clientId), new Response(wid));
  } catch (err) {
    // Best effort — but visible: a silent failure here means the SSR renders defaults.
    console.warn("[pathland] failed to remember the window id", err);
  }
}

async function recall(clientId) {
  if (!clientId) return null;
  try {
    const cache = await caches.open(STORE);
    const hit = await cache.match(widKey(clientId));
    return hit ? await hit.text() : null;
  } catch (err) {
    console.warn("[pathland] failed to recall the window id", err);
    return null;
  }
}

// Resolve the window id for a navigation. Chrome reports the *initiating document's*
// client id on the fetch event; WebKit leaves it EMPTY for navigations (it only sets
// `resultingClientId`, which names the document that does not exist yet), so we fall
// back to client identity only when it is unambiguous — never guess between tabs, as a
// wrong id would render another window's persisted state.
async function widFor(event) {
  if (event.clientId) return recall(event.clientId);
  const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
  if (windows.length === 1) return recall(windows[0].id);
  const sameUrl = windows.filter((client) => client.url === event.request.url);
  if (sameUrl.length === 1) return recall(sameUrl[0].id);
  return null; // ambiguous -> fail open (the SSR renders defaults, the live link corrects)
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
      const wid = await widFor(event);
      if (!wid) return fetch(event.request); // fail open: no id -> the plain request
      const url = new URL(event.request.url);
      url.searchParams.set("wid", wid);
      // NEVER pass the navigation Request as the init: its `mode` is "navigate", which a
      // script cannot construct — fetch() would throw a TypeError and we would fall back
      // to the unscoped request (the "still flashes defaults" bug). Build an explicit
      // init instead. `cache: "reload"` keeps a same-window reload from being served a
      // stale SSR body out of the HTTP cache (the rewritten URL is its own cache key).
      const response = await fetch(url.toString(), {
        method: "GET",
        headers: event.request.headers,
        credentials: "same-origin",
        mode: "same-origin",
        redirect: "manual",
        cache: "reload",
      });
      // Carry the id to the next document in this tab, so a reload keeps resolving
      // even if the client id changes across documents.
      await remember(event.resultingClientId, wid);
      return response;
    } catch (err) {
      // Never break a navigation — but make a silent no-op visible (the SW's own
      // DevTools console context) instead of failing open invisibly.
      console.warn("[pathland] service worker navigation rewrite failed; loading unscoped", err);
      return fetch(event.request);
    }
  })());
});
