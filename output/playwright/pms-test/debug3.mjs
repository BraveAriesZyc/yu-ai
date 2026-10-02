import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
let errors = [];
page.on('pageerror', (e) => errors.push((e.stack || e.message).slice(0, 250)));
await page.goto('http://localhost:5999/auth/login', {
  waitUntil: 'domcontentloaded',
});
await sleep(1000);
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
for (const p of [
  '/pms/pm/project/detail/1',
  '/pms/kb/library/1',
  '/pms/kb/label',
  '/pms/kb/collection',
  '/pms/kb/recent',
  '/pms/kb/search',
]) {
  errors = [];
  await page.goto(`http://localhost:5999${p}`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(4000);
  console.log(p, 'errors:', errors.length);
  errors.forEach((e) => console.log('  -', e.split('\n')[0]));
}
await browser.close();
