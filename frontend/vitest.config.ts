import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  define: { __QVA_CLASSIC_URL__: JSON.stringify("/") },
  test: { include: ['tests/**/*.component.test.tsx'] },
})
