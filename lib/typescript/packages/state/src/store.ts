export interface StateStore {
  load(scope: string): Promise<Record<string, unknown>> | Record<string, unknown>;
  save(scope: string, key: string, value: unknown): void | Promise<void>;
  delete(scope: string, key: string): void | Promise<void>;
}

/** In-memory store (one map per session scope). */
export class MemoryStateStore implements StateStore {
  private readonly scopes = new Map<string, Map<string, unknown>>();

  load(scope: string): Record<string, unknown> {
    const entries = this.scopes.get(scope);
    return entries ? Object.fromEntries(entries) : {};
  }

  save(scope: string, key: string, value: unknown): void {
    let entries = this.scopes.get(scope);
    if (!entries) {
      entries = new Map();
      this.scopes.set(scope, entries);
    }
    entries.set(key, value);
  }

  delete(scope: string, key: string): void {
    this.scopes.get(scope)?.delete(key);
  }
}
