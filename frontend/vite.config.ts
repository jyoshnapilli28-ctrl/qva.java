import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwind from '@tailwindcss/vite'

const backend = process.env.QVA_API_TARGET ?? 'http://127.0.0.1:18080'

export default defineConfig({
  base: '/app/',
  plugins: [react(), tailwind()],
  define: { __QVA_CLASSIC_URL__: JSON.stringify(backend) },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backend, ws: true },
    },
  },
})
