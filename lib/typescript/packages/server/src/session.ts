import { decodeEvents } from "@pathland/protocol";
import { Emitter, FrameOpcodeSink, InputDispatcher, type RenderResult } from "@pathland/engine";
import type { SsrRenderer } from "@pathland/ssr";
import { renderPage } from "@pathland/ssr";
import { signal } from "@pathland/signals";
import type { StateStore } from "@pathland/state";
import { PersistentState } from "@pathland/state";
import { withAppContext, type AppContext, type AppPlatform } from "@pathland/view";
import type { PathlandApp } from "./app";
import { DeltaBatcher } from "./batcher";
import type { PathlandConnection } from "./connection";

export interface PathlandSessionOptions {
  sessionId: string;
  app: PathlandApp;
  store: StateStore;
  env: { route: string; viewportWidth?: number; viewportHeight?: number };
  base: string;
  stateScope: string;
  ssrRenderer: SsrRenderer;
  bundleName: string;
}

export interface SessionRenderOptions {
  serviceWorker?: boolean;
  ssrScoped?: boolean;
}

/**
 * One application instance per client session. The mount frame is emitted in the
 * constructor but is only used for SSR; live deltas start after `connect`.
 */
export class PathlandSession {
  private readonly state: PersistentState;
  private readonly route;
  private readonly context: AppContext;
  private readonly sink: FrameOpcodeSink;
  private readonly emitter: Emitter;
  private readonly dispatcher: InputDispatcher;
  private readonly batcher: DeltaBatcher;
  private readonly rootId: number;
  private initialFrame: Uint8Array | null = null;
  private capturing = true;
  private connection: PathlandConnection | null = null;

  constructor(
    private readonly options: PathlandSessionOptions,
    preloaded: Record<string, unknown> = {},
  ) {
    this.state = new PersistentState(options.store, options.stateScope, preloaded);
    this.route = signal(options.env.route);
    const platform: AppPlatform = {
      now: () => Date.now(),
      random: () => Math.random(),
    };
    this.context = { state: this.state, platform, route: this.route };

    this.sink = new FrameOpcodeSink();
    this.sink.onFrame((frame) => this.handleFrame(frame));
    this.emitter = new Emitter(this.sink);
    const root = withAppContext(this.context, () => options.app.newRoot(this.context));
    const result: RenderResult = this.emitter.mount(root, this.context);
    this.capturing = false;
    this.rootId = result.rootId;
    this.dispatcher = new InputDispatcher(result, this.route);
    this.batcher = new DeltaBatcher((bytes) => this.send(bytes));
  }

  /** Load persisted state before mounting, then construct the session. */
  static async create(
    options: PathlandSessionOptions,
    preloaded?: Record<string, unknown>,
  ): Promise<PathlandSession> {
    const state = preloaded ?? ((await options.store.load(options.stateScope)) as Record<string, unknown>);
    return new PathlandSession(options, state ?? {});
  }

  async renderHtml(options: SessionRenderOptions = {}): Promise<string> {
    if (!this.initialFrame) throw new Error("session did not emit a mount frame");
    const document = this.options.ssrRenderer.renderDocument(this.initialFrame, this.rootId);
    return renderPage(document, {
      base: this.options.base,
      bundleName: this.options.bundleName,
      serviceWorker: options.serviceWorker,
      ssrScoped: options.ssrScoped,
    });
  }

  connect(connection: PathlandConnection): void {
    this.connection = connection;
  }

  applyEnvironment(env: { route: string; viewportWidth?: number; viewportHeight?: number }): void {
    this.route.set(env.route);
  }

  dispatch(bytes: Uint8Array): void {
    try {
      this.dispatcher.dispatch(decodeEvents(bytes));
    } catch {
      // Malformed events are dropped; a bad input must never tear down a session.
    }
  }

  resync(): void {
    this.emitter.renderFull();
    this.batcher.flush();
  }

  close(): void {
    this.state.close();
    this.emitter.destroy();
    this.batcher.close();
    this.connection = null;
  }

  private handleFrame(frame: Uint8Array): void {
    if (this.capturing) {
      this.initialFrame = frame;
      return;
    }
    if (this.connection?.isOpen()) this.batcher.append(frame);
  }

  private send(bytes: Uint8Array): void {
    const connection = this.connection;
    if (!connection?.isOpen()) {
      this.connection = null;
      return;
    }
    try {
      connection.send(bytes);
    } catch {
      this.connection = null;
    }
  }
}
