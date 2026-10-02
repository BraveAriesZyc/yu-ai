import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) =>
  console.log('PAGEERROR:', (e.stack || e.message).slice(0, 600)),
);
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
await page.goto('http://localhost:5999/pms/pm/project/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
const links = page.locator('table .ant-btn-link, table a');
console.log('link count:', await links.count());
const first = links.first();
console.log('first text:', await first.innerText().catch(() => 'N/A'));
await first.click();
await sleep(5000);
console.log('url after click:', page.url());
await browser.close();
