// DOM node resolution for raw input events: map an event target to the Pathland
// node that owns it. A raw input's `target` is frequently a **Text node** (or the
// document) rather than an Element — e.g. a wheel over a `<span>`'s text — so both
// helpers guard with `instanceof Element` before `closest`/`hasAttribute` (a
// non-Element has no `closest`, which threw `t.closest is not a function`).

/** The owning Pathland node (the closest `[data-pathland-id]` ancestor). */
export function nodeOf(target: EventTarget | null): HTMLElement | null {
  return target instanceof Element ? target.closest<HTMLElement>("[data-pathland-id]") : null;
}

/**
 * The element an input event belongs to: the nearest element (self or ancestor)
 * that has a `data-pathland-id` AND a `data-event-listeners` mask including `bit`.
 * Unlike {@link nodeOf} (which returns the deepest id-bearing element), this walks
 * up so a gesture declared on a container (e.g. a tap on a `VStack` row whose child
 * `Text` is what's actually clicked) is attributed to the node that listens — the
 * contract the GTK renderer follows (gesture attached to any widget with the bits).
 * Elements that declare no listeners are skipped, so non-listening children never
 * steal an event from a listening ancestor.
 */
export function eventNodeOf(target: EventTarget | null, bit: number): HTMLElement | null {
  let el: Element | null = target instanceof Element ? target : null;
  while (el && el !== document.body) {
    if (el.hasAttribute("data-pathland-id")) {
      const raw = el.getAttribute("data-event-listeners");
      if (raw !== null && (Number(raw) & bit) !== 0) {
        return el as HTMLElement;
      }
    }
    el = el.parentElement;
  }
  return null;
}
