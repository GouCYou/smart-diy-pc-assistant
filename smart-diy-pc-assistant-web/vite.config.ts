import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [
      vue(),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: "src/components.d.ts",
      }),
    ],
    server: {
      host: "127.0.0.1",
      port: 5173,
      proxy: {
        "/agent": {
          target: env.VITE_BACKEND_ORIGIN || "http://127.0.0.1:8080",
          changeOrigin: true,
        },
        "/api": {
          target: env.VITE_BACKEND_ORIGIN || "http://127.0.0.1:8080",
          changeOrigin: true,
        },
        "/media": {
          target: env.VITE_BACKEND_ORIGIN || "http://127.0.0.1:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
