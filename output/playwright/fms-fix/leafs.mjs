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
await page.goto(`${BASE}/fms/voucher/create`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4500);
await page.locator('.entry-subject .ant-select').nth(0).click();
await sleep(1200);
const opts = await page.evaluate(() =>
  [...document.querySelectorAll('.ant-select-item-option')].map((o) => ({
    text: o.innerText.trim().slice(0, 30),
    disabled: o.getAttribute('aria-disabled'),
  })),
);
console.log(JSON.stringify(opts.slice(0, 25), null, 1));
await browser.close();
