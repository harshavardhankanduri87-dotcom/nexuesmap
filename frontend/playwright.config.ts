import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright E2E configuration for NEXUSMAP.
 * Uses the locally installed Chrome browser channel on Windows to eliminate binary download dependencies.
 */
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  expect: {
    timeout: 10000,
  },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    // Use host Chrome browser channel to avoid sandboxed driver installation issues
    channel: "chrome",
  },
  projects: [
    {
      name: "Desktop Chrome",
      use: {
        ...devices["Desktop Chrome"],
        channel: "chrome",
        viewport: { width: 1280, height: 720 },
      },
    },
  ],
});
