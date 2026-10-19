import { defineConfig, devices } from "@playwright/test";

/**
 * E2E against a **running** Pathland host (the Spring/Quarkus demo). Point
 * `PATHLAND_URL` at it (default `http://localhost:8080`).
 *
 * Chromium launches with `--autoplay-policy=no-user-gesture-required` so the
 * browser's autoplay gate doesn't mask the *progress* behavior under test (the
 * server-driven `MEDIA_TIME_UPDATED` → seek bar/lyrics chain).
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  use: {
    baseURL: process.env.PATHLAND_URL ?? "http://localhost:8080",
    launchOptions: {
      args: ["--autoplay-policy=no-user-gesture-required"],
    },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
