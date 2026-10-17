import { expect, test } from "@playwright/test";

// The per-window-id service worker must attach the tab's id to the NEXT navigation, so
// the SSR renders the right persisted-state scope on the first paint. This is the
// real-browser guard for the two bugs jsdom cannot see: a worker registered with a
// narrower scope (Service-Worker-Allowed not honoured) and a relative cache key that
// Safari rejects — both of which silently fell back to the unscoped SSR.
test("the service worker attaches the window id to the next navigation", async ({
  page,
  request,
}) => {
  await request.get("/__reset"); // this project shares the fixture server's log

  await page.goto("/");
  // Wait until the worker controls the page (it claims clients on activate).
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, {
    timeout: 20_000,
  });

  await page.reload();

  await expect
    .poll(
      async () => {
        const seen = (await (await request.get("/__seen")).json()) as Array<string | null>;
        return seen.some((wid) => typeof wid === "string");
      },
      { timeout: 20_000 },
    )
    .toBe(true);
});
