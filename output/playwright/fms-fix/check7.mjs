import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/create`, { waitUntil: 'networkidle' });
await sleep(4000);
await page.screenshot({
  path: 'output/playwright/fms-fix/voucher-create-zoom.png',
  clip: { x: 245, y: 220, width: 320, height: 40 },
});
await browser.close();
