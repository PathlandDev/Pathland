import { defineConfig, devices } from "@playwright/test";

// Two real-browser suites share this config:
//  - the per-window-id service worker (issue #155) against a local fixture server —
//    the browser-variance bugs (scope handling, relative cache keys) only surface
//    in a real worker engine, never in jsdom/happy-dom;
//  - the media flow against a **running** Pathland host when `PATHLAND_URL` points
//    at one (the Spring/Quarkus demo); without it the media spec is skipped.
const liveHost = process.env.PATHLAND_URL;

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  fullyParallel: false,
  workers: 1, // one shared fixture server; tests reset its per-navigation log
  reporter: "list",
  use: {
    baseURL: liveHost ?? "http://127.0.0.1:4173",
    launchOptions: {
      // The server-driven media flow is about progress, not the autoplay gate
      // (the media spec still starts playback explicitly).
      args: ["--autoplay-policy=no-user-gesture-required"],
    },
  },
  webServer: liveHost
    ? undefined
    : {
        command: "node e2e/fixture/server.mjs",
        url: "http://127.0.0.1:4173/__seen",
        reuseExistingServer: !process.env.CI,
        timeout: 30_000,
      },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});
