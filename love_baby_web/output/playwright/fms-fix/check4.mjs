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
const texts = await page.evaluate(() =>
  [...document.querySelectorAll('.ant-select-selection-item')]
    .map((e) => e.innerText.trim())
    .slice(0, 8),
);
await page.screenshot({
  path: 'output/playwright/fms-fix/voucher-create-4.png',
  clip: { x: 230, y: 190, width: 1200, height: 130 },
});
console.log(JSON.stringify({ texts }));
await browser.close();
