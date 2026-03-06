import { defineConfig } from "vite";

const backend = process.env.VITE_DEV_PROXY_TARGET || "http://localhost:8000";

export default defineConfig({
  server: {
    proxy: {
      "/auth": backend,
      "/mail": backend,
      "/health": backend
    }
  }
});
