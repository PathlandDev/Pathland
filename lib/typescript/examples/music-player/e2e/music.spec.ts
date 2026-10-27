import { expect, test } from "@playwright/test";

test("renders the music library, selects a track, and toggles playback", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("Library", { exact: true })).toBeVisible();
  await expect(page.getByText("Now Playing", { exact: true })).toBeVisible();
  await expect(page.getByText("Building on Solid Ground").first()).toBeVisible();
  await expect(page.locator('[data-pathland-icon="play"]')).toHaveCount(1);
  await expect(page.locator("input[type=range]")).toHaveCount(2);

  // Select the second track; the sidebar + player bar update through deltas.
  await page.locator("button", { hasText: "Pathland Crossing" }).click();
  await expect(page.getByText("The Crossings · Native Operations")).toBeVisible();
  await expect(page.locator('video[data-pathland-media]')).toHaveAttribute("src", /track2\.mp3/);
  await expect(page.locator('img[src*="cover2.jpg"]')).toHaveCount(3);
  await expect(page.locator('[data-pathland-icon="pause"]')).toHaveCount(1);

  // Pause toggles the transport icon back.
  await page.locator('[data-pathland-icon="pause"]').click();
  await expect(page.locator('[data-pathland-icon="play"]')).toHaveCount(1);

  // The seek slider is a live range control.
  const seek = page.locator("input[type=range]").first();
  await seek.evaluate((el) => {
    const input = el as HTMLInputElement;
    input.value = "50";
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
});
