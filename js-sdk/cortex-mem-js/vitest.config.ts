import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
  },
  resolve: {
    alias: {
      '../src': new URL('./src/index.ts', import.meta.url).pathname,
    },
  },
});
