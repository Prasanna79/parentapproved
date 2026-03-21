import { cloudflareTest } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      isolatedStorage: false,
      wrangler: { configPath: "./wrangler.toml" },
    }),
  ],
  test: {
    exclude: ["test/dashboard-parity.test.ts", "test/browser/**", "**/node_modules/**", "**/dist/**"],
  },
});
