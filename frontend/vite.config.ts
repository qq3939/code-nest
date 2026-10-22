import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// 开发时把 /api 转给本机后端，/files 转给 MinIO 的公开桶；生产由 Nginx 做同样的转发（deploy/nginx/nginx.conf）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/files': {
        target: 'http://localhost:9000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/files/, '/code-nest'),
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 大依赖单独分包，业务代码改动时这些包的缓存不失效
        manualChunks(id) {
          if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender')) return 'echarts'
          if (id.includes('node_modules/md-editor-v3')) return 'md-editor'
          if (id.includes('node_modules/element-plus')) return 'element-plus'
          return undefined
        },
      },
    },
  },
})
