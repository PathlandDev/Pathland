import type { WritableSignal } from "@pathland/signals";
import type { PersistentState } from "@pathland/state";

export interface AppPlatform {
  now(): number;
  random(): number;
}

export interface AppContext {
  state: PersistentState;
  platform: AppPlatform;
  route: WritableSignal<string>;
}

const stack: AppContext[] = [];

/** The context of the view currently being mounted or rendered. */
export function useAppContext(): AppContext {
  const context = stack[stack.length - 1];
  if (!context) throw new Error("useAppContext() called outside a Pathland mount");
  return context;
}

/** Run `fn` with `context` available to `useAppContext()`. */
export function withAppContext<T>(context: AppContext, fn: () => T): T {
  stack.push(context);
  try {
    return fn();
  } finally {
    stack.pop();
  }
}
