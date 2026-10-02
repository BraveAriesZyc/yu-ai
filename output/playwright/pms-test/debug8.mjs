import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({
  viewport: { width: 1680, height: 1000 },
});
await ctx.addInitScript(() => {
  window.addEventListener('unhandledrejection', (ev) => {
    try {
      console.error(
        `UNHANDLED-REJECTION::${JSON.stringify(ev.reason).slice(0, 500)}`,
      );
    } catch {
      console.error(`UNHANDLED-REJECTION::${String(ev.reason)}`);
    }
  });
  window.addEventListener('error', (ev) => {
    console.error(`WINDOW-ERROR::${String(ev.message).slice(0, 300)}`);
  });
});
const page = await ctx.newPage();
page.on('console', (m) => {
  if (
    m.text().startsWith('UNHANDLED-REJECTION::') ||
    m.text().startsWith('WINDOW-ERROR::')
  ) {
    console.log(m.text());
  }
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
await page.goto('http://localhost:5999/pms/pm/project/template', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
await browser.close();
