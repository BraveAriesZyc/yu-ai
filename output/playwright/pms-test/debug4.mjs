import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', async (e) => {
  const stack = e.stack || e.message;
  console.log('PAGEERROR:', stack.split('\n').slice(0, 6).join(' | '));
});
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
  '/pms/pm/workbench',
  '/pms/pm/project/list',
  '/pms/pm/project/detail/1?tabs=task',
  '/pms/pm/project/detail/1?tabs=overview',
  '/pms/pm/project/detail/1?tabs=gantt',
  '/pms/pm/project/template',
  '/pms/pm/project/archive',
  '/pms/pm/project/recycle',
  '/pms/kb/library',
  '/pms/kb/library/1',
  '/pms/kb/library-template',
  '/pms/kb/label',
  '/pms/kb/collection',
  '/pms/kb/recent',
  '/pms/kb/search',
  '/pms/kb/recycle',
]) {
  await page.goto(`http://localhost:5999${p}`, {
    waitUntil: 'domcontentloaded',
  });
  await sleep(3000);
  console.log('visited', p);
}
await browser.close();
