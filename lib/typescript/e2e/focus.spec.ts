import { expect, test } from "@playwright/test";

/**
 * Keyboard focus must survive a lazy list scrolling under it.
 *
 * A keyed lazy window slides by removing rows above the viewport and moving
 * every surviving row to its new index. Moving a focused element with
 * removeChild + insertBefore blurs it in real browsers, so without batch-level
 * focus restoration the next ArrowDown goes to <body> and navigation dies.
 */
test.skip(!process.env.PATHLAND_URL, "requires a running host (set PATHLAND_URL)");

test("keyboard navigation keeps focus when the lazy list scrolls", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto("/");
  await page.waitForSelector("[data-pathland-focusable]", { timeout: 20_000 });
  // Let hydration + the live WS session settle before sending keys.
  await page.waitForTimeout(1500);

  const active = () =>
    page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      return {
        id: el?.getAttribute("data-pathland-id") ?? el?.tagName ?? "",
        focusable: !!el?.hasAttribute("data-pathland-focusable"),
      };
    });

  await page.locator("[data-pathland-focusable]").first().focus();
  expect((await active()).focusable).toBe(true);

  // Walk past the initial window so the list must scroll and slide its window.
  for (let i = 0; i < 20; i++) {
    const before = (await active()).id;
    await page.keyboard.press("ArrowDown");
    await expect.poll(async () => (await active()).id, { timeout: 5_000 }).not.toBe(before);
    expect((await active()).focusable, `focus after ArrowDown #${i + 1}`).toBe(true);
  }

  // Focus is still live: the next arrow must advance it again.
  const before = (await active()).id;
  await page.keyboard.press("ArrowDown");
  await expect.poll(async () => (await active()).id, { timeout: 5_000 }).not.toBe(before);
  expect((await active()).focusable).toBe(true);
});

test("keyboard scrolling advances one row instead of jumping half a viewport", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto("/");
  await page.waitForSelector("[data-pathland-focusable]", { timeout: 20_000 });
  await page.waitForTimeout(1500);

  const state = () =>
    page.evaluate(() => {
      const lazy = document.querySelector<HTMLElement>("[data-pathland-lazy]");
      let scroller = lazy?.parentElement ?? null;
      while (
        scroller &&
        !(getComputedStyle(scroller).overflowY === "auto" && scroller.scrollHeight > scroller.clientHeight)
      ) {
        scroller = scroller.parentElement;
      }
      const el = document.activeElement as HTMLElement | null;
      const row = el?.getBoundingClientRect();
      const box = scroller?.getBoundingClientRect();
      return {
        pos: Number(el?.getAttribute("aria-posinset") ?? 0),
        focusable: !!el?.hasAttribute("data-pathland-focusable"),
        scrollTop: scroller?.scrollTop ?? 0,
        rowTop: row && box ? Math.round(row.top - box.top) : -1,
        viewportHeight: box?.height ?? 0,
      };
    });

  await page.locator("[data-pathland-focusable]").first().focus();
  let previous = await state();

  // A full row is 64px. A half-viewport jump (the native focus centering bug)
  // is hundreds of pixels; a stable window slides by at most one row per step.
  for (let i = 0; i < 20; i++) {
    await page.keyboard.press("ArrowDown");
    await expect.poll(async () => (await state()).pos, { timeout: 5_000 }).toBe(previous.pos + 1);

    const current = await state();
    expect(current.focusable, `focus after ArrowDown #${i + 1}`).toBe(true);
    expect(current.scrollTop, `scroll never jumps backwards (#${i + 1})`).toBeGreaterThanOrEqual(
      previous.scrollTop - 1,
    );
    expect(current.scrollTop - previous.scrollTop, `scroll delta (#${i + 1})`).toBeLessThanOrEqual(128);
    expect(current.rowTop, `focused row stays in view (#${i + 1})`).toBeGreaterThanOrEqual(-1);
    expect(current.rowTop, `focused row stays in view (#${i + 1})`).toBeLessThanOrEqual(
      current.viewportHeight,
    );
    previous = current;
  }
});
