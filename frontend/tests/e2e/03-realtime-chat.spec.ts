import { test, expect } from "@playwright/test";
import { generateSyntheticAccount, registerViaUI, mockGeolocation } from "./test-helpers";

test.describe("E2E Test 5: Real-Time Zone Chat Between Independent Users", () => {
  test("Two authenticated users in the same zone exchange real-time STOMP chat messages", async ({
    browser,
  }) => {
    const userA = generateSyntheticAccount("chata");
    const userB = generateSyntheticAccount("chatb");

    // Context A (User 1)
    const contextA = await browser.newContext();
    await mockGeolocation(contextA, 37.7760, -122.4180);
    const pageA = await contextA.newPage();
    await registerViaUI(pageA, userA);

    // Context B (User 2)
    const contextB = await browser.newContext();
    await mockGeolocation(contextB, 37.7760, -122.4180);
    const pageB = await contextB.newPage();
    await registerViaUI(pageB, userB);

    // Ensure GPS location syncs with PostGIS zone membership on backend
    await pageA.waitForResponse((res) => res.url().includes("/api/location/update") && res.status() === 200, { timeout: 8000 }).catch(() => {});
    await pageB.waitForResponse((res) => res.url().includes("/api/location/update") && res.status() === 200, { timeout: 8000 }).catch(() => {});

    // Open Chat Drawer on both contexts
    const chatToggleA = pageA.locator('#zone-chat-toggle-btn');
    await expect(chatToggleA).toBeVisible({ timeout: 15000 });
    await chatToggleA.click();

    const chatToggleB = pageB.locator('#zone-chat-toggle-btn');
    await expect(chatToggleB).toBeVisible({ timeout: 15000 });
    await chatToggleB.click();

    // Await active connection
    await expect(pageA.getByText("CONNECTED")).toBeVisible({ timeout: 10000 });
    await expect(pageB.getByText("CONNECTED")).toBeVisible({ timeout: 10000 });

    // User A: Send a transmission message
    const chatInputA = pageA.locator('#zone-chat-input');
    await expect(chatInputA).toBeVisible({ timeout: 10000 });
    const uniqueMessage = `Transmission E2E #${Date.now()}`;
    await chatInputA.fill(uniqueMessage);
    const sendBtnA = pageA.locator('#zone-chat-send-btn');
    await sendBtnA.click();

    // Verify User A sees their own sent message
    await expect(pageA.getByText(uniqueMessage)).toBeVisible({ timeout: 10000 });

    // Verify User B receives the real-time broadcast message
    await expect(pageB.getByText(uniqueMessage)).toBeVisible({ timeout: 15000 });

    // Clean up contexts
    await contextA.close();
    await contextB.close();
  });
});
