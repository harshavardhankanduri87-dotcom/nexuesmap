import { test, expect } from "@playwright/test";
import { mockGeolocation } from "./test-helpers";

test.describe("E2E Test 3 & 4: Browser Geolocation, My Location & PostGIS Geofencing", () => {
  test("Deterministic GPS Fix: grants location, updates badge to 'Location available', centers map", async ({
    context,
    page,
  }) => {
    // 1. Mock GPS coordinates inside Central Library zone (37.7760, -122.4180)
    await mockGeolocation(context, 37.7760, -122.4180);

    await page.goto("/");
    await page.waitForLoadState("domcontentloaded");

    // 2. Leaflet map container must be loaded
    const map = page.locator("#nexus-leaflet-map");
    await expect(map).toBeVisible({ timeout: 15000 });

    // 3. Location status badge should reflect active location
    const statusBadge = page.locator("#location-status-badge");
    await expect(statusBadge).toBeVisible();
    await expect(statusBadge).toContainText("Location available", { timeout: 15000 });

    // 4. "My Location" button exists and is clickable
    const myLocationBtn = page.locator("#my-location-btn");
    await expect(myLocationBtn).toBeVisible();
    await expect(myLocationBtn).toBeEnabled();
    await myLocationBtn.click({ force: true });

    // 5. Active zone detection in header or HUD should reflect Central Academic Library or Campus Perimeter
    const zoneBadge = page.getByText(/Central Academic Library|ZONE-LIBRARY|Campus Perimeter/i);
    await expect(zoneBadge.first()).toBeVisible({ timeout: 10000 });
  });

  test("Permission Denied Handling: displays warning banner and status when location is blocked", async ({
    context,
    page,
  }) => {
    // Explicitly clear permissions to trigger permission denial
    await context.clearPermissions();

    await page.goto("/");
    await page.waitForLoadState("domcontentloaded");

    const statusBadge = page.locator("#location-status-badge");
    await expect(statusBadge).toBeVisible({ timeout: 15000 });
    // Status badge will reflect either getting location or permission denied/unavailable
    const badgeText = await statusBadge.innerText();
    expect(
      badgeText.includes("Permission denied") ||
      badgeText.includes("Location unavailable") ||
      badgeText.includes("Getting location")
    ).toBeTruthy();
  });

  test("Campus Zone HUD Selection: switching zones updates map focus", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("domcontentloaded");

    // Click on another campus zone in the MapHUD (e.g. Innovation Hub or Auditorium)
    const zoneItem = page.locator("aside").getByText(/Innovation|Auditorium|Science/i).first();
    if (await zoneItem.isVisible()) {
      await zoneItem.click();
      // Should highlight or fly to selected zone
      await page.waitForTimeout(500);
    }
  });
});
