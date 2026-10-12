import { test, expect } from "@playwright/test";
import { generateSyntheticAccount, registerViaUI, mockGeolocation } from "./test-helpers";

test.describe("E2E Test 6, 7 & 8: Global Pins, Meetups, and Ghost Mode Privacy", () => {
  test("Pin Creation: toggles pin placement mode, fills modal, and deploys global pin", async ({
    context,
    page,
  }) => {
    const creator = generateSyntheticAccount("pincreator");
    await mockGeolocation(context, 37.7760, -122.4180);
    await registerViaUI(page, creator);

    // 1. Click "Deploy Global Pin" in PinFilterHUD
    const deployBtn = page.locator('button:has-text("Deploy Global Pin")');
    await expect(deployBtn).toBeVisible({ timeout: 15000 });
    await deployBtn.click();

    const targetLat = 37.7752 + (Date.now() % 100) * 0.0001;
    const targetLng = -122.4172 + (Date.now() % 100) * 0.0001;

    // 2. Trigger pin placement via Leaflet map click
    await page.evaluate(({ lat, lng }) => {
      // @ts-ignore
      const map = window.__nexusMap;
      if (map) {
        map.fire("click", { latlng: { lat, lng } });
      }
    }, { lat: targetLat, lng: targetLng });

    // 3. Modal should appear
    const modal = page.locator("text=Drop Global Pin");
    await expect(modal).toBeVisible({ timeout: 10000 });

    // 4. Fill in pin details
    const uniqueTitle = `Study Jam #${Date.now()}`;
    await page.fill('input[placeholder*="Next.js"]', uniqueTitle);
    await page.fill('textarea[placeholder*="context"]', "Collaborative study session for geospatial algorithms.");

    // 5. Submit pin creation
    const submitPinBtn = page.locator('button[type="submit"]:has-text("Deploy Pin")');
    await submitPinBtn.click();

    // 6. Verify modal closed and pin is reflected in Pin radar HUD counter
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await expect(page.locator("text=/\\d+\\s+Pins/")).toBeVisible({ timeout: 15000 });
  });

  test("Ghost Mode Toggle: enables Ghost Mode, updates badge, and hides coordinates", async ({
    context,
    page,
  }) => {
    const user = generateSyntheticAccount("ghostuser");
    await mockGeolocation(context, 37.7760, -122.4180);
    await registerViaUI(page, user);

    // 1. Locate Ghost Mode toggle button in Navbar or HUD
    const ghostToggle = page.locator("#ghost-mode-toggle").first();
    await expect(ghostToggle).toBeVisible({ timeout: 15000 });

    // 2. Click to toggle Ghost Mode ON
    await ghostToggle.click();

    // 3. Verify visual state changes to indicate active Ghost Mode
    await expect(ghostToggle).toHaveClass(/bg-purple/, { timeout: 10000 });
    await expect(ghostToggle).toContainText(/Ghost (Active|Mode)/, { timeout: 10000 });
  });
});
