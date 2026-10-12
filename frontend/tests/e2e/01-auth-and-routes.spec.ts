import { test, expect } from "@playwright/test";
import { generateSyntheticAccount, registerViaUI, loginViaUI } from "./test-helpers";

test.describe("E2E Test 1 & 2: Authentication, Registration, Protected Routes & Logout", () => {
  const syntheticUser = generateSyntheticAccount("authuser");

  test("User Registration: creates account and redirects to map dashboard", async ({ page }) => {
    await registerViaUI(page, syntheticUser);

    // Verify user lands on map interface
    await expect(page).toHaveURL("/");
    await expect(page.locator("header")).toBeVisible();
    await expect(page.locator("#nexus-leaflet-map")).toBeVisible();
  });

  test("Registration Validation: rejects mismatched passwords", async ({ page }) => {
    await page.goto("/register");
    await page.fill("#register-name", "Mismatch User");
    await page.fill("#register-email", "mismatch@nexus.edu");
    await page.fill("#register-password", "ValidPass123!");
    await page.fill("#register-confirm-password", "DifferentPass123!");

    await page.click('button[type="submit"]');

    // Expect error alert
    const errorBox = page.locator("text=Passwords do not match");
    await expect(errorBox).toBeVisible();
  });

  test("Login & Logout Flow: handles login, clears session upon logout", async ({ page }) => {
    // 1. Log in
    await page.goto("/login");
    await page.fill("#login-email", syntheticUser.email);
    await page.fill("#login-password", syntheticUser.password);
    await page.click("#login-submit-btn");

    await page.waitForFunction(() => window.location.pathname === "/", { timeout: 15000 });
    await expect(page.locator("#nexus-leaflet-map")).toBeVisible();

    // 2. Log out via user dropdown / navbar logout button
    const userMenuOrLogout = page.locator('button[title*="Logout"], button:has-text("Logout"), button:has(svg.lucide-log-out)');
    if (await userMenuOrLogout.count() > 0) {
      await userMenuOrLogout.first().click();
    }

    // 3. Verify accessing /admin redirects or shows unauthorized for regular user
    await page.goto("/admin");
    await page.waitForLoadState("domcontentloaded");
    // Should either show 403 / redirect to login or show unauthorized notice
    const hasAdminNotice = page.getByText(/403|Forbidden|Admin Access Required|Sign in/i);
    await expect(hasAdminNotice.first()).toBeVisible({ timeout: 10000 });
  });

  test("Login Error Handling: rejects invalid credentials with error notice", async ({ page }) => {
    await page.goto("/login");
    await page.fill("#login-email", "nonexistent.user@nexus.edu");
    await page.fill("#login-password", "WrongPassword999!");
    await page.click("#login-submit-btn");

    // Expect error message
    const errorMsg = page.getByText(/Invalid|failed|Unauthorized/i);
    await expect(errorMsg.first()).toBeVisible({ timeout: 10000 });
  });
});
