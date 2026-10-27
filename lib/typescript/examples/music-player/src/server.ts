import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";
import { pathlandExpress } from "@pathland/server/express";
import { loadSsrRenderer } from "@pathland/ssr";
import { MemoryStateStore } from "@pathland/state";
import { MusicPlayerApp } from "./app";

export interface MusicPlayerServerOptions {
  wasmPath?: string;
  publicDir?: string;
  sharedAssetsDir?: string;
}

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../..");

export async function createMusicPlayerServer(options: MusicPlayerServerOptions = {}) {
  const wasmPath =
    options.wasmPath ?? resolve(repoRoot, "lib/typescript/packages/ssr/wasm/pathland_render_html.wasm");
  const publicDir =
    options.publicDir ?? resolve(repoRoot, "lib/typescript/examples/music-player/public");
  const sharedAssetsDir =
    options.sharedAssetsDir ??
    resolve(
      repoRoot,
      "lib/java/pathland-demo-views/src/main/resources/META-INF/resources/_pathland/assets",
    );

  const ssrRenderer = await loadSsrRenderer(readFileSync(wasmPath));
  const app = express();

  // The demo reuses the shared Java demo's album art/audio/lyrics assets.
  app.use("/_pathland/assets", express.static(sharedAssetsDir));
  app.use(express.static(publicDir));

  const pathland = pathlandExpress({
    app: new MusicPlayerApp(),
    store: new MemoryStateStore(),
    ssrRenderer,
    base: "/_pathland",
    assetDir: resolve(publicDir, "_pathland"),
  });
  app.use(pathland.router);

  const server = createServer(app);
  pathland.attach(server);
  return { app, server, registry: pathland.registry };
}
