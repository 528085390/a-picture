import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  preview: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8000', changeOrigin: true, ws: true },
    },
  },
  server: {
    watch: { ignored: ['**/scratch/**', '**/shots/**', '**/design-samples/**', '**/dist/**'] },
    proxy: {
      '/api': { target: 'http://127.0.0.1:8000', changeOrigin: true, ws: true },
    },
  },
})
