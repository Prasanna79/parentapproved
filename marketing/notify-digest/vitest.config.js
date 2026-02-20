import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  resolve: {
    alias: {
      'cloudflare:email': path.resolve(__dirname, 'tests/mocks/cloudflare-email.js'),
    },
  },
});
