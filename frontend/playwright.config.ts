import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end smoke coverage against the real stack: Postgres, the Spring API, and the SPA.
 * `e2e/start-backend.sh` brings up the database and then the API; Playwright starts the dev
 * server. Everything runs on the ports the app uses in development, so the Vite proxy carries
 * /api and the /ws socket to the API exactly as it does by hand.
 */
export default defineConfig({
  testDir: './e2e',
  // The suite drives one shared simulation clock, so specs must not race each other for it.
  workers: 1,
  fullyParallel: false,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? 'line' : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: './e2e/start-backend.sh',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 240_000,
      stdout: 'ignore',
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 120_000,
      stdout: 'ignore',
    },
  ],
})
