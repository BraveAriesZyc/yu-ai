import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) =>
  console.log('PAGEERROR:', (e.stack || e.message).slice(0, 400)),
);
page.on('request', (r) => {
  if (r.url().includes('/admin-api/oa/attendance/week-report'))
    console.log('REQ:', r.url().replace(base, ''));
});

await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
const tenantSelect = page.locator('.ant-select:has-text("请选择租户")');
if (await tenantSelect.count()) {
  await tenantSelect.click();
  await sleep(1200);
  await page
    .locator('.ant-select-dropdown .ant-select-item-option')
    .first()
    .click();
  await sleep(500);
}
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});

await page.goto(`${base}/oa/attendance/report`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);

const headers = () =>
  page.$$eval('.vxe-table--header-wrapper th', (els) =>
    els
      .filter((el) => el.offsetParent !== null)
      .map((el) => el.textContent.trim())
      .filter((t) => t.startsWith('周'))
      .join(','),
  );

// 路径 A：表单改 所在周 + 搜索
const input = page
  .locator('label:has-text("所在周")')
  .locator('xpath=..')
  .locator('input');
await input.click();
await sleep(600);
await input.fill('2026-09-07');
await page.keyboard.press('Enter');
await sleep(800);
await page.keyboard.press('Escape');
await sleep(500);
await page
  .locator('button', { hasText: /^搜\s*索$/ })
  .first()
  .click();
await sleep(3000);
console.log('A 表单搜索后列头:', await headers());
await page.screenshot({
  path: 'output/playwright/oa-fix-regression/antd-recheck-03-week-report-formsearch.png',
});

// 路径 B：再点 上一周 按钮
await page
  .locator('button', { hasText: /上一周/ })
  .first()
  .click();
await sleep(3000);
console.log('B 上一周后列头:', await headers());
await page.screenshot({
  path: 'output/playwright/oa-fix-regression/antd-recheck-03-week-report-prevbtn.png',
});
await browser.close();
