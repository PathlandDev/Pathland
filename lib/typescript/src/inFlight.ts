// In-flight interaction tracking (spec/EVENTS.md "In-flight interaction (MUST)").
//
// A control with an active continuous-input session (a range slider being
// dragged) is authoritative for the state it edits: the DOM client must not
// apply inbound `VALUE` updates to it while the session lasts, so a server echo
// cannot fight the pointer and cause flicker. At most one drag happens at a
// time, so a single active-range slot suffices.

let activeRange: HTMLInputElement | null = null;

/** Mark a range slider as being dragged (its thumb is user-authoritative). */
export function markRangeInFlight(el: HTMLInputElement): void {
  activeRange = el;
}

/** Clear the marker when a specific range is released/blurred. */
export function unmarkRangeInFlight(el: HTMLInputElement): void {
  if (activeRange === el) {
    activeRange = null;
  }
}

/** Clear the marker on any release/cancel/blur. */
export function clearInFlightRange(): void {
  activeRange = null;
}

/** True while `el` is the active drag (inbound `VALUE` must be suppressed). */
export function isRangeInFlight(el: HTMLInputElement): boolean {
  return activeRange === el;
}