import { test, expect } from "@playwright/test";
import { loginViaUI } from "./test-helpers";

test.describe("E2E Test 9 & 10: Admin Dashboard RBAC & System Resilience", () => {
  test("RBAC: Regular student account cannot access /admin (403 Forbidden page)", async ({
    page,
  }) => {
    // 1. Log in as regular student account
    await loginViaUI(page, "student@nexus.edu", "Nexus@123456");

    // 2. Navigate to /admin
    await page.goto("/admin");
    await page.waitForLoadState("domcontentloaded");

    // 3. Verify 403 Forbidden screen is rendered
    const forbiddenBanner = page.locator("text=403 Forbidden — Admin Access Required");
    await expect(forbiddenBanner).toBeVisible({ timeout: 15000 });
  });

  test("Admin Access: ROLE_ADMIN account accesses dashboard, renders KPI cards and charts", async ({
    page,
  }) => {
    // 1. Log in as seeded administrator account
    await loginViaUI(page, "admin@nexus.edu", "Admin@123456");

    // 2. Navigate to /admin
    await page.goto("/admin");
    await page.waitForLoadState("domcontentloaded");

    // 3. Verify Admin Control Hub header
    await expect(page.locator("h1:has-text('NEXUSMAP Command & Analytics')")).toBeVisible({ timeout: 15000 });

    // 4. Verify KPI summary cards
    await expect(page.locator("text=Registered Users")).toBeVisible({ timeout: 10000 });
    await expect(page.locator("text=Active Zones")).toBeVisible({ timeout: 10000 });

    // 5. Verify Charts and Density Heatmap controls
    await expect(page.locator("text=Geographic User Density Heatmap")).toBeVisible({ timeout: 10000 });
    await expect(page.locator("text=Campus Zone Occupancy")).toBeVisible({ timeout: 10000 });
  });

  test("Client Validation & Error Resilience: short password blocks submission", async ({ page }) => {
    await page.goto("/register");
    await page.fill("#register-name", "Short User");
    await page.fill("#register-email", "short@nexus.edu");
    await page.fill("#register-password", "short"); // < 8 characters
    await page.fill("#register-confirm-password", "short");

    await page.click('button[type="submit"]');

    // Expect character length validation notice
    const lengthNotice = page.getByText(/Password must be at least 8 characters/i);
    await expect(lengthNotice).toBeVisible({ timeout: 5000 });
  });
});
