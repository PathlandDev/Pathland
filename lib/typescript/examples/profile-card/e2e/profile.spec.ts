import { expect, test } from "@playwright/test";

test("hydrates the profile card and round-trips a text edit", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("Ada Lovelace")).toBeVisible();
  await expect(page.getByText("Online")).toBeVisible();
  await expect(page.locator('img[src="avatar.png"]')).toHaveCount(1);
  await expect(page.locator('[data-pathland-id="1"]')).toHaveCount(1);
  await expect(page.locator('input[placeholder="Search user..."]')).toBeVisible();

  const input = page.locator('input[placeholder="Search user..."]');
  await input.fill("ali");
  await expect(page.getByText('Searching for "ali"')).toBeVisible();
  await expect(page.locator('[data-pathland-id="1"]')).toHaveCount(1);

  // Same-tab reload: SSR renders defaults, then the client requests RESYNC and
  // restores this window's persisted search state over the WebSocket.
  await page.reload();
  await expect(page.getByText('Searching for "ali"')).toBeVisible();
  await expect(page.locator('input[placeholder="Search user..."]')).toHaveValue("ali");
});
