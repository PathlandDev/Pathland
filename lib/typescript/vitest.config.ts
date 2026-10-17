import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "happy-dom",
    // Unit tests only; the real-browser E2E lives in e2e/ and runs under Playwright.
    include: ["test/**/*.test.ts"],
  },
});