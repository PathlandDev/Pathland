import express, { type RequestHandler, type Router } from "express";
import type { Server } from "node:http";
import {
  PathlandRegistry,
  type PathlandRegistryOptions,
} from "./registry";
import { attachPathlandWebSocket } from "./ws";

export interface PathlandExpressOptions extends PathlandRegistryOptions {
  /** Static directory containing the content-hashed DOM client and manifest. */
  assetDir?: string;
}

export interface PathlandExpress {
  registry: PathlandRegistry;
  router: Router;
  attach(server: Server): void;
}

/**
 * Express adapter: serve the DOM client assets and SSR every HTML GET; attach
 * the WebSocket endpoint with `attach(httpServer)`.
 */
export function pathlandExpress(options: PathlandExpressOptions): PathlandExpress {
  const registry = new PathlandRegistry(options);
  const router = express.Router();
  const base = registry.base;

  if (options.assetDir) {
    router.use(base, express.static(options.assetDir));
  }

  const ssr: RequestHandler = async (request, response, next) => {
    if (request.method !== "GET" || !request.accepts("html")) {
      next();
      return;
    }
    if (request.path === base || request.path.startsWith(`${base}/`)) {
      next();
      return;
    }
    try {
      const wid = typeof request.query.wid === "string" ? request.query.wid : undefined;
      const html = await registry.renderHtml(request.path || "/", wid);
      response.type("html").send(html);
    } catch (error) {
      next(error);
    }
  };
  router.use(ssr);

  return {
    registry,
    router,
    attach(server: Server) {
      attachPathlandWebSocket(server, registry, { base });
    },
  };
}
