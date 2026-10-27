import { effect, signal, type EffectRef, type WritableSignal } from "@pathland/signals";
import type { StateStore } from "./store";

/**
 * A persisted field holder. Declare it as a class field and call
 * `PersistentState.connect(this)` before mounting; the field name becomes the
 * store key unless an explicit key was supplied.
 */
export class State<T> {
  private bound: WritableSignal<T> | null = null;
  private fieldName = "";

  constructor(
    private readonly initial: T,
    private readonly explicitKey?: string,
  ) {}

  get key(): string {
    return this.explicitKey ?? this.fieldName;
  }

  get signal(): WritableSignal<T> {
    if (!this.bound) throw new Error(`State "${this.key}" is not connected`);
    return this.bound;
  }

  bind(state: PersistentState, fieldName: string): void {
    this.fieldName = fieldName;
    this.bound = state.state(this.explicitKey ?? fieldName, this.initial);
  }

  get(): T {
    return this.signal();
  }

  set(value: T): void {
    this.signal.set(value);
  }

  update(fn: (current: T) => T): void {
    this.signal.update(fn);
  }
}

/** Per-session connection between State fields and a StateStore. */
export class PersistentState {
  private readonly signals = new Map<string, WritableSignal<unknown>>();
  private readonly effects: EffectRef[] = [];

  constructor(
    private readonly store: StateStore,
    private readonly scope: string,
    private readonly preloaded: Record<string, unknown> = {},
  ) {}

  state<T>(key: string, initial: T): WritableSignal<T> {
    const existing = this.signals.get(key);
    if (existing) return existing as WritableSignal<T>;

    const loaded = Object.prototype.hasOwnProperty.call(this.preloaded, key)
      ? (this.preloaded[key] as T)
      : initial;
    const value = signal(loaded);
    this.signals.set(key, value as WritableSignal<unknown>);
    this.effects.push(
      effect(() => {
        try {
          const result = this.store.save(this.scope, key, value());
          if (result instanceof Promise) result.catch(() => undefined);
        } catch {
          // Persistence is best-effort; a UI update must not fail with the store.
        }
      }),
    );
    return value;
  }

  /** Bind every State field on `target` (recursively through nested view objects). */
  connect(target: object): void {
    this.connectObject(target, new WeakSet());
  }

  private connectObject(target: object, seen: WeakSet<object>): void {
    if (seen.has(target)) return;
    seen.add(target);
    for (const key of Object.keys(target)) {
      const value = (target as Record<string, unknown>)[key];
      if (value instanceof State) {
        value.bind(this, key);
      } else if (isTraversable(value)) {
        this.connectObject(value, seen);
      }
    }
  }

  close(): void {
    for (const ref of this.effects) ref.destroy();
    this.effects.length = 0;
    this.signals.clear();
  }
}

function isTraversable(value: unknown): value is object {
  if (value === null || typeof value !== "object") return false;
  if (Array.isArray(value) || value instanceof State) return false;
  if (value instanceof Map || value instanceof Set || value instanceof Date) return false;
  return true;
}
