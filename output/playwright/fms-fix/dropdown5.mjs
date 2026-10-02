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
await page.goto(`${BASE}/infra/webSocket`, { waitUntil: 'networkidle' });
await sleep(3000);
await page.locator('.ant-select').first().click();
await sleep(1000);
const r = await page.evaluate(() => {
  const dd = [...document.querySelectorAll('.ant-select-dropdown')].find(
    (d) => !d.classList.contains('ant-select-dropdown-hidden'),
  );
  return dd
    ? {
        count: dd.querySelectorAll('.ant-select-item-option').length,
        text: dd.innerText.slice(0, 80),
      }
    : null;
});
console.log(JSON.stringify(r));
await browser.close();
