import { randomUUID } from "node:crypto";
import { resolveBundleName, type SsrRenderer } from "@pathland/ssr";
import type { StateStore } from "@pathland/state";
import type { PathlandApp } from "./app";
import type { PathlandConnection } from "./connection";
import { PathlandSession } from "./session";

export interface PathlandRegistryOptions {
  app: PathlandApp;
  store: StateStore;
  ssrRenderer: SsrRenderer;
  base?: string;
  assetDir?: string;
  bundleName?: string;
}

interface PendingSession {
  stateScope: string;
  connection: PathlandConnection;
}

/**
 * Owns sessions 1:1 with connections. Session identity (`uiId`) is separate from
 * the persisted-state scope (`wid`). Sessions mount lazily on the first inbound
 * message so the live tree is seeded from the client's environment and scope.
 */
export class PathlandRegistry {
  private readonly app: PathlandApp;
  private readonly store: StateStore;
  private readonly ssrRenderer: SsrRenderer;
  private readonly bundleName: string;
  private readonly mountBase: string;
  private readonly sessions = new Map<string, PathlandSession>();
  private readonly pending = new Map<string, PendingSession>();
  private readonly queues = new Map<string, Promise<void>>();

  constructor(options: PathlandRegistryOptions) {
    this.app = options.app;
    this.store = options.store;
    this.ssrRenderer = options.ssrRenderer;
    this.bundleName = options.bundleName ?? resolveBundleName(options.assetDir);
    this.mountBase = (options.base ?? "/_pathland").replace(/\/+$/, "") || "/_pathland";
  }

  get base(): string {
    return this.mountBase;
  }

  get bundle(): string {
    return this.bundleName;
  }

  stateScope(windowId: string): string {
    return `/${windowId}`;
  }

  /** SSR uses a throwaway session; a real `wid` makes the document scope-aware. */
  async renderHtml(route: string, windowId?: string): Promise<string> {
    const scoped = typeof windowId === "string" && windowId.length > 0;
    const scope = this.stateScope(scoped ? windowId : randomUUID());
    const session = await PathlandSession.create({
      sessionId: randomUUID(),
      app: this.app,
      store: this.store,
      env: { route: route || "/" },
      base: this.mountBase,
      stateScope: scope,
      ssrRenderer: this.ssrRenderer,
      bundleName: this.bundleName,
    });
    try {
      return await session.renderHtml({ ssrScoped: scoped });
    } finally {
      session.close();
    }
  }

  open(uiId: string, windowId: string, connection: PathlandConnection): void {
    const existing = this.sessions.get(uiId);
    if (existing) {
      existing.close();
      this.sessions.delete(uiId);
    }
    this.pending.set(uiId, { stateScope: this.stateScope(windowId), connection });
  }

  environment(
    uiId: string,
    env: { route: string; viewportWidth?: number; viewportHeight?: number },
  ): Promise<void> {
    return this.enqueue(uiId, async () => {
      const session = await this.ensure(uiId, env);
      session.applyEnvironment(env);
    });
  }

  dispatch(uiId: string, bytes: Uint8Array): void {
    void this.enqueue(uiId, async () => {
      const session = await this.ensure(uiId, { route: "/" });
      session.dispatch(bytes);
    });
  }

  resync(uiId: string): void {
    void this.enqueue(uiId, async () => {
      const session = await this.ensure(uiId, { route: "/" });
      session.resync();
    });
  }

  close(uiId: string): void {
    void this.enqueue(uiId, async () => {
      this.pending.delete(uiId);
      const session = this.sessions.get(uiId);
      if (session) {
        session.close();
        this.sessions.delete(uiId);
      }
    });
  }

  shutdown(): void {
    for (const session of this.sessions.values()) session.close();
    this.sessions.clear();
    this.pending.clear();
  }

  activeSessions(): number {
    return this.sessions.size;
  }

  private async ensure(
    uiId: string,
    env: { route: string; viewportWidth?: number; viewportHeight?: number },
  ): Promise<PathlandSession> {
    const existing = this.sessions.get(uiId);
    if (existing) return existing;
    const pending = this.pending.get(uiId);
    const session = await PathlandSession.create({
      sessionId: uiId,
      app: this.app,
      store: this.store,
      env,
      base: this.mountBase,
      stateScope: pending?.stateScope ?? this.stateScope(uiId),
      ssrRenderer: this.ssrRenderer,
      bundleName: this.bundleName,
    });
    if (pending) {
      session.connect(pending.connection);
      this.pending.delete(uiId);
    }
    this.sessions.set(uiId, session);
    return session;
  }

  private enqueue(uiId: string, task: () => Promise<void>): Promise<void> {
    const previous = this.queues.get(uiId) ?? Promise.resolve();
    const next = previous.then(task, task);
    this.queues.set(uiId, next);
    void next
      .catch(() => undefined)
      .then(() => {
        if (this.queues.get(uiId) === next) this.queues.delete(uiId);
      });
    return next;
  }
}
