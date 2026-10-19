import { test, expect } from "@playwright/test";

/**
 * Media progress end-to-end against a **running** host (Spring/Quarkus demo).
 *
 * The controller's playback element is started directly (the browser autoplay
 * gate is orthogonal to the chain under test) and the assertion is that the
 * **seek bar advances** while the track plays — i.e. the client's
 * `MEDIA_TIME_UPDATED` reports reach the server, advance `position`, and the
 * re-emitted seek-bar `VALUE` moves the bar.
 */
test.skip(!process.env.PATHLAND_URL, "requires a running host (set PATHLAND_URL)");

test("the seek bar advances while the controller plays", async ({ page }) => {
  await page.goto("/");
  await page.waitForSelector("video[data-pathland-media]", { timeout: 20_000 });
  // Let the resync + fit-slot downgrade settle.
  await page.waitForTimeout(1500);

  const seekValue = () =>
    page.evaluate(() => {
      const seek = Array.from(document.querySelectorAll<HTMLInputElement>("input[type=range]"))
        .find((r) => r.max === "100");
      return seek ? Number(seek.value) : null;
    });
  const mediaTime = () =>
    page.evaluate(() => document.querySelector<HTMLVideoElement>("video[data-pathland-media]")?.currentTime ?? -1);

  // Put the app in its playing state (so the server keeps the track playing).
  const playIcon = page.locator('button:has([data-pathland-icon="play"])').first();
  if ((await playIcon.count()) > 0) {
    await playIcon.click();
    await page.waitForTimeout(500);
  }

  // Start the playback element directly.
  await page.evaluate(async () => {
    const v = document.querySelector<HTMLVideoElement>("video[data-pathland-media]");
    if (v) {
      v.muted = false;
      await v.play().catch(() => undefined);
    }
  });

  await page.waitForTimeout(2000);
  const first = await seekValue();
  await page.waitForTimeout(4000);
  const second = await seekValue();
  const t = await mediaTime();

  // eslint-disable-next-line no-console
  console.log(`seek ${first} -> ${second} (media t=${t.toFixed(2)})`);

  expect(t).toBeGreaterThan(0);
  expect(first).not.toBeNull();
  expect(second!).toBeGreaterThan(first!);
});
