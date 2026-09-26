import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发模式：npm run dev 起在 5173，/api 代理到本地 Spring Boot(8080)
// 构建模式：npm run build 产物直接输出到 Spring Boot 的 static 目录，
//           最终一个 jar 同时服务后端 API + 前端页面（面试演示只跑一个进程）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
