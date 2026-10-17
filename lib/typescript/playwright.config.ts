import { defineConfig, devices } from "@playwright/test";

// Real-browser E2E for the per-window-id service worker. It exercises the SHIPPED
// sw.js + registration logic against Chromium and WebKit: the browser-variance bugs
// (scope handling, relative cache keys) only surface in a real worker engine, never
// in jsdom/happy-dom — which is why the unit tests cannot guard them.
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  fullyParallel: false,
  workers: 1, // one shared fixture server; tests reset its per-navigation log
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:4173",
  },
  webServer: {
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
