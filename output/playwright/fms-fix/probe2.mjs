import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const reqs = [];
page.on('request', (r) => {
  if (r.url().includes('voucher')) reqs.push(r.url().split('admin-api/')[1]);
});
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/create`, { waitUntil: 'networkidle' });
await sleep(4000);

const info = await page.evaluate(() => {
  const selects = [...document.querySelectorAll('.ant-select')].map((s) => ({
    cls: s.className.slice(0, 80),
    text: s.innerText.trim().slice(0, 40),
  }));
  return selects;
});
console.log('selects:', JSON.stringify(info, null, 2));

// 点击凭证字下拉（含「1」文本、宽 90px 的那个）
const target = page.locator('.ant-select', { hasText: /^1$/ }).first();
await target.click();
await sleep(1000);
const options = await page.evaluate(() =>
  [...document.querySelectorAll('.ant-select-item-option')].map((o) =>
    o.innerText.trim(),
  ),
);
console.log('dropdown options:', JSON.stringify(options));
console.log('voucher reqs:', JSON.stringify(reqs.slice(0, 8), null, 1));
await page.screenshot({
  path: 'output/playwright/fms-fix/voucher-word-dropdown.png',
});
await browser.close();
