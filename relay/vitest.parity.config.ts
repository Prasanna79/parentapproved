import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["test/dashboard-parity.test.ts"],
  },
});
