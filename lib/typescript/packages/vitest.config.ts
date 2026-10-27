import { defineConfig } from "vitest/config";

// Server-side packages run in Node. The browser DOM client has its own
// happy-dom config at `lib/typescript/vitest.config.ts`.
export default defineConfig({
  test: {
    environment: "node",
    include: ["test/**/*.test.ts"],
  },
});
