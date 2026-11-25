import { expect, test } from "@playwright/test";

/**
 * A reconnect creates a fresh server session. The replacement snapshot is a
 * mount stream with no INSERT_CHILD for the root, so the DOM client must
 * reattach the new root after discarding the old tree. Without that, the page
 * is left blank even though the new tree was built.
 */
test.skip(!process.env.PATHLAND_URL, "requires a running host (set PATHLAND_URL)");

test("reconnect reattaches the replacement root", async ({ page }) => {
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    const state = window as unknown as {
      __pathlandWs?: WebSocket;
      __pathlandWsCount?: number;
    };
    state.__pathlandWsCount = 0;
    window.WebSocket = class extends Native {
      constructor(url: string | URL, protocols?: string | string[]) {
        super(url, protocols);
        state.__pathlandWs = this;
        state.__pathlandWsCount = (state.__pathlandWsCount ?? 0) + 1;
      }
    };
  });

  await page.goto("/");
  await page.waitForSelector('[data-pathland-id="1"]', { timeout: 20_000 });
  await page.waitForTimeout(1500);
  await expect(page.locator('[data-pathland-id="1"]')).toBeAttached();

  await page.evaluate(() => {
    (window as unknown as { __pathlandWs?: WebSocket }).__pathlandWs?.close();
  });

  await expect
    .poll(
      async () =>
        page.evaluate(() => {
          const state = window as unknown as {
            __pathlandWs?: WebSocket;
            __pathlandWsCount?: number;
          };
          return {
            count: state.__pathlandWsCount ?? 0,
            open: state.__pathlandWs?.readyState === WebSocket.OPEN,
          };
        }),
      { timeout: 10_000 },
    )
    .toEqual({ count: 2, open: true });

  // The snapshot may span several batches; let it settle, then verify the page
  // was rebuilt rather than left with an empty body.
  await page.waitForTimeout(1500);
  const state = await page.evaluate(() => {
    const root = document.querySelector<HTMLElement>('[data-pathland-id="1"]');
    return {
      connected: root?.isConnected ?? false,
      bodyLength: document.body.innerHTML.length,
      text: document.body.innerText.trim().length,
    };
  });
  expect(state.connected).toBe(true);
  expect(state.bodyLength).toBeGreaterThan(100);
  expect(state.text).toBeGreaterThan(0);
});
