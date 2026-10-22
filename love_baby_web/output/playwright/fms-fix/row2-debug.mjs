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

await page.locator('.entry-digest input').nth(0).fill('功能验收测试凭证');
await page.locator('.entry-subject .ant-select').nth(0).click();
await sleep(800);
await page.keyboard.type('100101');
await sleep(800);
await page
  .locator(
    '.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option:has-text("100101")',
  )
  .first()
  .click();
await sleep(800);

const selCount = await page.locator('.entry-subject .ant-select').count();
await page.locator('.entry-subject .ant-select').nth(1).click();
await sleep(1000);
const state = await page.evaluate(() => ({
  selects: [...document.querySelectorAll('.entry-subject .ant-select')].map(
    (s) => ({
      open: s.classList.contains('ant-select-open'),
      text: s.innerText.trim().slice(0, 25),
    }),
  ),
  dropdowns: [...document.querySelectorAll('.ant-select-dropdown')].map(
    (d) => ({
      hidden: d.classList.contains('ant-select-dropdown-hidden'),
      items: d.querySelectorAll('.ant-select-item-option').length,
    }),
  ),
}));
console.log(JSON.stringify({ selCount, ...state }, null, 1));
await page.screenshot({ path: 'output/playwright/fms-fix/row2-debug.png' });
await browser.close();
