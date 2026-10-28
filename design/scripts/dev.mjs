// Rebuild design/build/base.css whenever the tokens or hand-authored CSS change.
// Pass `--serve` to also serve the static preview at http://localhost:4173/.

import { watch } from "chokidar";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { buildDesign } from "./build.mjs";
import { DESIGN_ROOT } from "./token-model.mjs";

const serve = process.argv.slice(2).includes("--serve");
let building = false;
let queued = false;

async function rebuild(reason) {
  if (building) {
    queued = true;
    return;
  }
  building = true;
  try {
    const basePath = await buildDesign();
    console.log(`design: rebuilt ${basePath}${reason ? ` (${reason})` : ""}`);
  } catch (error) {
    console.error("design: build failed");
    console.error(error);
  } finally {
    building = false;
    if (queued) {
      queued = false;
      await rebuild("queued change");
    }
  }
}

function startServer() {
  const types = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
  };
  const server = createServer(async (req, res) => {
    const url = decodeURIComponent((req.url ?? "/").split("?")[0]);
    const relative = url === "/" ? "preview/index.html" : url.replace(/^\/+/, "");
    const file = join(DESIGN_ROOT, relative);
    if (!file.startsWith(DESIGN_ROOT)) {
      res.writeHead(403).end("forbidden");
      return;
    }
    try {
      const body = await readFile(file);
      res.writeHead(200, {
        "content-type": types[extname(file)] ?? "application/octet-stream",
        "cache-control": "no-store",
      });
      res.end(body);
    } catch {
      res.writeHead(404).end("not found");
    }
  });
  server.listen(4173, "127.0.0.1", () => {
    console.log("design: preview at http://127.0.0.1:4173/");
  });
}

await rebuild("initial");
watch(
  [
    join(DESIGN_ROOT, "tokens"),
    join(DESIGN_ROOT, "html"),
    join(DESIGN_ROOT, "style-dictionary.config.mjs"),
    join(DESIGN_ROOT, "scripts/formats.mjs"),
    join(DESIGN_ROOT, "scripts/token-model.mjs"),
  ],
  { ignoreInitial: true },
).on("all", (event, path) => {
  void rebuild(`${event} ${path.replace(DESIGN_ROOT + "/", "")}`);
});

if (serve) startServer();

console.log("design: watching tokens/ and html/ (Ctrl+C to stop)");
