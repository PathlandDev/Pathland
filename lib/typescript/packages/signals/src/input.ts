import { constant, isSignal, type MaybeSignal, type ReadonlySignal } from "./signal";

export const InputSignal = {
  from<T>(value: MaybeSignal<T> | null | undefined, fallback?: T): ReadonlySignal<T> {
    if (isSignal(value)) return value as ReadonlySignal<T>;
    if (value === null || value === undefined) return constant(fallback as T);
    return constant(value);
  },
};
