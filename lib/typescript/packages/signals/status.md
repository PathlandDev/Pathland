# @pathland/signals — status

**Last updated:** October 22, 2026

## Implemented

- Callable `ReadonlySignal` / `WritableSignal` (`signal`).
- Lazy, memoized `computed` with equality suppression and error caching.
- Synchronous `effect` flush, cleanup handles, and `destroy()`.
- `untracked`, `constant`, `isSignal`, `isConstantSignal`.
- `InputSignal.from` (signals pass through; raw values and fallbacks become
  constants).
- Tests cover callable reads, dependency tracking, memoization/equality,
  effect cleanup, untracked reads, input wrapping, and circular dependencies.

## Not implemented / gaps

- No async effects, scheduler priorities, or write-discipline options beyond the
  single-threaded synchronous flush.
- No zone integration.
