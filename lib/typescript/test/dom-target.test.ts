import { describe, expect, it } from "vitest";
import { eventNodeOf, nodeOf } from "../src/dom-target";
import { LISTEN_POINTER_UP } from "../src/constants";

/**
 * Raw-input target resolution: an event's `target` is frequently a **Text node**
 * (e.g. a wheel over a `<span>`'s text) rather than an Element — `closest` then
 * threw `t.closest is not a function`. Both helpers must guard and return null.
 */
describe("nodeOf / eventNodeOf", () => {
  it("returns null for a non-Element target instead of throwing", () => {
    const text = document.createTextNode("hi");
    expect(() => nodeOf(text)).not.toThrow();
    expect(nodeOf(text)).toBeNull();
    expect(eventNodeOf(text, LISTEN_POINTER_UP)).toBeNull();
    expect(nodeOf(null)).toBeNull();
    expect(eventNodeOf(null, LISTEN_POINTER_UP)).toBeNull();
  });

  it("resolves an element to its closest pathland node", () => {
    document.body.innerHTML =
      `<div data-pathland-id="1"><div data-pathland-id="2" data-event-listeners="${LISTEN_POINTER_UP}">` +
      `<span id="leaf">x</span></div></div>`;
    const leaf = document.getElementById("leaf")!;

    expect(nodeOf(leaf)?.getAttribute("data-pathland-id")).toBe("2");
    // eventNodeOf walks up to the nearest LISTENING id-bearing ancestor.
    expect(eventNodeOf(leaf, LISTEN_POINTER_UP)?.getAttribute("data-pathland-id")).toBe("2");
    // A bit the node does not listen for walks past it to the (non-listening) root → null.
    expect(eventNodeOf(leaf, 1 << 30)).toBeNull();
  });
});
