/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      // The dev server proxies the API so the browser sees a single origin; this keeps the
      // SameSite=Strict refresh cookie working without cross-origin cookie configuration.
      proxy: {
        '/api': { target: env.VITE_API_PROXY_TARGET || 'http://localhost:8090', changeOrigin: false },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  };
});
