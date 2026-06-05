import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendOrigin = (env.VITE_BACKEND_ORIGIN || 'https://api-smartdiy.cctstudio.cn/').trim()

  return {
    plugins: [vue()],
    server: {
      host: true,
      allowedHosts: ['6.cctstudio.cn'],
      proxy: {
        '/api': {
          target: backendOrigin,
          changeOrigin: true,
        },
        '/media': {
          target: backendOrigin,
          changeOrigin: true,
        },
      },
    },
  }
})
