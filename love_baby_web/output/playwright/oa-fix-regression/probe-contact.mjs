import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = process.argv[2] || 'http://127.0.0.1:5666';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) =>
  console.log('PAGEERROR:', (e.stack || e.message).slice(0, 300)),
);
page.on('console', (m) => {
  if (m.type() === 'error') console.log('CONSOLE:', m.text().slice(0, 200));
});

await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
const tenantSelect = page.locator(
  '.ant-select:has-text("请选择租户"), .el-select:has-text("请选择租户")',
);
if (await tenantSelect.count()) {
  await tenantSelect.click();
  await sleep(1200);
  await page
    .locator(
      '.ant-select-dropdown .ant-select-item-option, .el-select-dropdown__item',
    )
    .first()
    .click();
  await sleep(500);
}
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});

await page.goto(`${base}/oa/contact`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
const item = page
  .locator(
    '.ant-menu-item:has-text("我共享的"), .el-menu-item:has-text("我共享的")',
  )
  .first();
console.log('menu item html:', (await item.innerHTML()).slice(0, 300));
await item.click();
await sleep(1000);
console.log(
  'selected item:',
  JSON.stringify(
    await page.$$eval(
      '.ant-menu-item-selected, .el-menu-item.is-active',
      (els) => els.map((e) => e.textContent.trim()),
    ),
  ),
);
await sleep(4000);
console.log(
  'headers:',
  JSON.stringify(
    await page.$$eval('.vxe-table--header-wrapper th', (els) =>
      els.map((el) => el.textContent.trim()),
    ),
  ),
);
console.log('request urls seen:');
await browser.close();
