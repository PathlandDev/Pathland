// A tiny fixture server for the service-worker E2E. It serves the REAL sw.js (with the
// same headers the starters emit) plus a minimal SSR-ish page that wires the REAL
// registration module, and records whether each navigation carried a `?wid=`.
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..", ".."); // lib/typescript

const swScript = readFileSync(join(root, "sw.js"), "utf8");
const clientScript = readFileSync(join(here, "service-worker.bundle.js"), "utf8");

// Every navigation the server saw, with the window id the worker attached (if any).
const seen = [];

const page = `<!doctype html>
<html data-pathland-base="/_pathland" data-pathland-service-worker="true">
<head><meta charset="utf-8"><title>pathland sw e2e</title></head>
<body>
<h1 id="ok">pathland sw e2e</h1>
<script type="module">
  import { setupServiceWorker } from "/_pathland/service-worker.js";
  let wid = sessionStorage.getItem("pathland-wid");
  if (!wid) { wid = crypto.randomUUID(); sessionStorage.setItem("pathland-wid", wid); }
  setupServiceWorker(wid, "/_pathland");
</script>
</body>
</html>`;

function send(res, status, headers, body) {
  res.writeHead(status, headers);
  res.end(body);
}

const server = createServer((req, res) => {
  const url = new URL(req.url, "http://127.0.0.1");
  const path = url.pathname;
  if (path === "/") {
    seen.push(url.searchParams.get("wid") ?? null);
    send(res, 200, { "content-type": "text/html; charset=utf-8" }, page);
  } else if (path === "/__seen") {
    send(res, 200, { "content-type": "application/json" }, JSON.stringify(seen));
  } else if (path === "/__reset") {
    seen.length = 0;
    send(res, 200, { "content-type": "application/json" }, "[]");
  } else if (path === "/_pathland/sw.js") {
    send(
      res,
      200,
      {
        "content-type": "text/javascript; charset=utf-8",
        // Root scope from a nested script URL — the exact header the real starters emit.
        "service-worker-allowed": "/",
        "cache-control": "no-cache",
      },
      swScript,
    );
  } else if (path === "/_pathland/service-worker.js") {
    send(res, 200, { "content-type": "text/javascript; charset=utf-8" }, clientScript);
  } else {
    send(res, 404, { "content-type": "text/plain" }, "not found");
  }
});

const port = Number(process.env.PORT ?? 4173);
server.listen(port, "127.0.0.1", () => {
  console.log(`pathland sw e2e fixture on http://127.0.0.1:${port}`);
});
