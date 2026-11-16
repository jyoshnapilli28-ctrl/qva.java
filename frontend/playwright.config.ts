import { defineConfig } from '@playwright/test'

const packagedBaseUrl = process.env.QVA_TEST_BASE_URL

export default defineConfig({
  testDir: 'tests',
  testMatch: '**/*.spec.ts',
  workers: 1,
  use: {
    baseURL: packagedBaseUrl ?? 'http://127.0.0.1:5173',
    launchOptions: { executablePath: process.env.QVA_CHROMIUM_PATH },
  },
  webServer: packagedBaseUrl ? undefined : {
    command: 'npm run dev',
    url: 'http://127.0.0.1:5173/app/',
    reuseExistingServer: false,
  },
})
