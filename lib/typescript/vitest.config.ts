import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "happy-dom",
    // The Playwright E2E specs live in `e2e/` and run against a live host
    // (`npx playwright test`), not vitest.
    exclude: ["**/node_modules/**", "**/dist/**", "e2e/**"],
  },
});
