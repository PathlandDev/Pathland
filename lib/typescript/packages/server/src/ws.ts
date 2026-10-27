import type { IncomingMessage, Server } from "node:http";
import type { Duplex } from "node:stream";
import { randomUUID } from "node:crypto";
import { decodeHostMessage, encodePong } from "@pathland/protocol";
import { WebSocket, WebSocketServer, type RawData } from "ws";
import type { PathlandConnection } from "./connection";
import type { PathlandRegistry } from "./registry";

export interface WebSocketAttachOptions {
  /** The reserved framework base; the endpoint is `${base}/ws`. */
  base?: string;
}

/** Attach the Pathland WebSocket endpoint to an HTTP server. */
export function attachPathlandWebSocket(
  server: Server,
  registry: PathlandRegistry,
  options: WebSocketAttachOptions = {},
): WebSocketServer {
  const base = (options.base ?? registry.base).replace(/\/+$/, "");
  const path = `${base}/ws`;
  const serverSockets = new WebSocketServer({ noServer: true });

  server.on("upgrade", (request: IncomingMessage, socket: Duplex, head: Buffer) => {
    const url = new URL(request.url ?? "/", "http://localhost");
    if (url.pathname !== path) return;
    serverSockets.handleUpgrade(request, socket, head, (ws) => {
      handleConnection(ws, url, registry);
    });
  });

  return serverSockets;
}

function handleConnection(ws: WebSocket, url: URL, registry: PathlandRegistry): void {
  const uiId = randomUUID();
  const windowId = url.searchParams.get("wid") ?? randomUUID();
  const connection: PathlandConnection = {
    send(bytes) {
      if (ws.readyState === WebSocket.OPEN) ws.send(bytes, { binary: true });
    },
    isOpen() {
      return ws.readyState === WebSocket.OPEN;
    },
  };
  registry.open(uiId, windowId, connection);

  ws.on("message", (data: RawData, isBinary: boolean) => {
    if (!isBinary) return;
    let bytes: Uint8Array;
    try {
      bytes = toBytes(data);
    } catch {
      return;
    }
    let message;
    try {
      message = decodeHostMessage(bytes);
    } catch {
      return;
    }
    switch (message.type) {
      case "environment":
        void registry.environment(uiId, message.env);
        break;
      case "resync":
        registry.resync(uiId);
        break;
      case "ping":
        connection.send(encodePong());
        break;
      case "pong":
        break;
      case "events":
        registry.dispatch(uiId, bytes);
        break;
    }
  });

  ws.on("close", () => registry.close(uiId));
  ws.on("error", () => undefined);
}

function toBytes(data: RawData): Uint8Array {
  if (data instanceof ArrayBuffer) return new Uint8Array(data);
  if (Array.isArray(data)) {
    const total = data.reduce((sum, part) => sum + part.byteLength, 0);
    const out = new Uint8Array(total);
    let offset = 0;
    for (const part of data) {
      out.set(new Uint8Array(part.buffer, part.byteOffset, part.byteLength), offset);
      offset += part.byteLength;
    }
    return out;
  }
  return new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
}
