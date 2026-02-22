import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  testMatch: 'playwright-smoke.spec.ts',
  timeout: 60_000,
  globalTimeout: 300_000,
  retries: 0,
  use: {
    headless: true,
    browserName: 'chromium',
  },
  reporter: [['list']],
});
