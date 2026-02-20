import { defineWorkersConfig } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersConfig({
  test: {
    exclude: ["test/dashboard-parity.test.ts", "test/browser/**", "**/node_modules/**", "**/dist/**"],
    poolOptions: {
      workers: {
        isolatedStorage: false,
        wrangler: { configPath: "./wrangler.toml" },
      },
    },
  },
});
