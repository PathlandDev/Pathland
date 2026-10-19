import { test, expect } from "@playwright/test";

/**
 * Media progress end-to-end against a **running** host (Spring/Quarkus demo).
 *
 * The app is put into "playing" (so the server keeps it playing) and the
 * controller's playback element is started directly — the client's own play()
 * is subject to the browser autoplay gate, which is orthogonal to the progress
 * chain under test. The assertion: the server advances `position`, so the seek
 * bar (0..100 percent) moves while the track plays.
 */
test.skip(!process.env.PATHLAND_URL, "requires a running host (set PATHLAND_URL)");

test("the seek bar advances while the controller plays", async ({ page }) => {
  await page.goto("/");

  const seekValue = () =>
    page.evaluate(() => {
      const seek = Array.from(document.querySelectorAll<HTMLInputElement>("input[type=range]"))
        .find((r) => r.max === "100");
      return seek ? Number(seek.value) : null;
    });
  const mediaTime = () =>
    page.evaluate(() => document.querySelector<HTMLVideoElement>("video[data-pathland-media]")?.currentTime ?? -1);

  // Put the app into playing state (the transport button toggles `playing`).
  await page.locator('button:has([data-pathland-icon="play"])').first().click();
  await page.waitForTimeout(300);

  // Start the controller's playback element directly (bypass the autoplay gate).
  await page.evaluate(async () => {
    const v = document.querySelector<HTMLVideoElement>("video[data-pathland-media]");
    if (!v) throw new Error("no controller playback element");
    v.muted = false;
    await v.play();
  });

  await page.waitForTimeout(1500);
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
