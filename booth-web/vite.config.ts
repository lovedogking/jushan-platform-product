import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3001,
    proxy: {
      // FIX-10：去除 /api 前缀转发到后端
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        // FIX-18：@import → @use 消除自研代码中的 Sass 弃用警告
        additionalData: `@use "@/styles/variables.scss" as *;`,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // FIX-18：手动分包减少主包体积
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-utils': ['axios'],
        },
      },
    },
  },
})
