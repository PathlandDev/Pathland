/**
 * Legacy TypeScript class decorator marker. Mounting stays explicit: the
 * application calls `body(props)` and passes the returned element to the
 * emitter. This is intentionally not tied to Angular DI or a compiler
 * transform.
 */
export function view<T extends object>(target: T): T {
  return target;
}
