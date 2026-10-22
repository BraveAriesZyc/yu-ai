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
  if (r.url().includes('week-report') || r.url().includes('weekReport'))
    console.log('REQ:', r.url().replace(base, ''));
});
page.on('response', (r) => {
  if (r.url().includes('week'))
    console.log('RESP:', r.status(), r.url().replace(base, '').slice(0, 120));
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

console.log('t0:', await headers());
await page
  .locator('button', { hasText: /上一周/ })
  .first()
  .click();
for (let i = 1; i <= 6; i++) {
  await sleep(1000);
  console.log(`t=${i}s:`, await headers());
}
await browser.close();
