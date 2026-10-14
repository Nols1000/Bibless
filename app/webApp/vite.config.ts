/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';

export default defineConfig({
  root: '.',
  // Set by CI for GitHub Pages, where the app is served from /<repo>/
  base: process.env.VITE_BASE ?? '/',
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // The landing page at the root, the organiser at organise/
    rollupOptions: {
      input: {
        main: resolve(import.meta.dirname, 'index.html'),
        organise: resolve(import.meta.dirname, 'organise/index.html'),
      },
    },
  },
  server: { port: 8080 },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
