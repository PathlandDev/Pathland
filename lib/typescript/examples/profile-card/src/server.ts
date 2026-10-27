import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";
import { pathlandExpress } from "@pathland/server/express";
import { loadSsrRenderer } from "@pathland/ssr";
import { MemoryStateStore } from "@pathland/state";
import { ProfileCardApp } from "./app";

export interface ExampleServerOptions {
  wasmPath?: string;
  publicDir?: string;
}

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../..");

export async function createProfileCardServer(options: ExampleServerOptions = {}) {
  const wasmPath =
    options.wasmPath ?? resolve(repoRoot, "lib/typescript/packages/ssr/wasm/pathland_render_html.wasm");
  const publicDir = options.publicDir ?? resolve(repoRoot, "lib/typescript/examples/profile-card/public");
  const assetDir = resolve(publicDir, "_pathland");

  const ssrRenderer = await loadSsrRenderer(readFileSync(wasmPath));
  const app = express();
  app.use(express.static(publicDir));

  const pathland = pathlandExpress({
    app: new ProfileCardApp(),
    store: new MemoryStateStore(),
    ssrRenderer,
    base: "/_pathland",
    assetDir,
  });
  app.use(pathland.router);

  const server = createServer(app);
  pathland.attach(server);
  return { app, server, registry: pathland.registry };
}
