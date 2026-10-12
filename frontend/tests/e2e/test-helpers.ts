import { Page, BrowserContext } from "@playwright/test";

export interface SyntheticAccount {
  name: string;
  email: string;
  password: string;
}

export function generateSyntheticAccount(prefix: string = "tester"): SyntheticAccount {
  const ts = Date.now();
  const rand = Math.floor(Math.random() * 10000);
  return {
    name: `E2E ${prefix} ${rand}`,
    email: `e2e.${prefix}.${ts}.${rand}@nexus.edu`,
    password: "StrongPassword123!",
  };
}

/**
 * Register a synthetic account via the web UI.
 */
export async function registerViaUI(page: Page, account: SyntheticAccount): Promise<void> {
  await page.goto("/register");
  await page.waitForLoadState("domcontentloaded");

  await page.fill("#register-name", account.name);
  await page.fill("#register-email", account.email);
  await page.fill("#register-password", account.password);
  await page.fill("#register-confirm-password", account.password);

  const submitBtn = page.locator('button[type="submit"]');
  await submitBtn.click();

  // Expect redirection to homepage or login (Next.js SPA client navigation)
  await page.waitForFunction(() => window.location.pathname === "/" || window.location.pathname === "/login", {
    timeout: 15000,
  });
}

/**
 * Log in an existing synthetic account via the web UI.
 */
export async function loginViaUI(page: Page, email: string, password: string): Promise<void> {
  await page.goto("/login");
  await page.waitForLoadState("domcontentloaded");

  await page.fill("#login-email", email);
  await page.fill("#login-password", password);

  const submitBtn = page.locator("#login-submit-btn");
  await submitBtn.click();

  // Redirection to map home page (Next.js SPA client navigation)
  await page.waitForFunction(() => window.location.pathname === "/", { timeout: 15000 });
}

/**
 * Setup deterministic browser geolocation and grant permissions.
 */
export async function mockGeolocation(
  context: BrowserContext,
  latitude: number,
  longitude: number
): Promise<void> {
  try {
    await context.grantPermissions(["geolocation"], { origin: "http://localhost:3000" });
    await context.setGeolocation({ latitude, longitude, accuracy: 10 });
  } catch {
    // Ignore if already set
  }

  // Inject deterministic browser navigator.geolocation mock across all pages
  await context.addInitScript(
    ({ lat, lng }) => {
      const mockPos = {
        coords: {
          latitude: lat,
          longitude: lng,
          accuracy: 10,
          altitude: null,
          altitudeAccuracy: null,
          heading: null,
          speed: null,
        },
        timestamp: Date.now(),
      };

      const mockGeo = {
        getCurrentPosition: (success: any) => {
          setTimeout(() => success(mockPos), 10);
        },
        watchPosition: (success: any) => {
          setTimeout(() => success(mockPos), 10);
          return 1;
        },
        clearWatch: () => {},
      };

      try {
        Object.defineProperty(navigator, "geolocation", {
          value: mockGeo,
          configurable: true,
          writable: true,
        });
      } catch {
        (navigator as any).geolocation = mockGeo;
      }
    },
    { lat: latitude, lng: longitude }
  );
}
