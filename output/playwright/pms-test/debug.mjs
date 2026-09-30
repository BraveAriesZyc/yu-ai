import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message.slice(0, 500)));
page.on('console', (m) => {
  if (m.type() === 'error' || m.type() === 'warning')
    console.log('CONSOLE:', m.type(), m.text().slice(0, 300));
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
console.log('logged in');
await page.goto('http://localhost:5999/pms/pm/workbench', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
console.log(
  'body text:',
  await page.evaluate(() => document.body.innerText.slice(0, 300)),
);
await browser.close();
