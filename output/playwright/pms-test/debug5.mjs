import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
page.on('pageerror', (e) => {
  try {
    console.log(
      'PE-RAW:',
      String(e),
      JSON.stringify(e, Object.getOwnPropertyNames(e)).slice(0, 300),
    );
  } catch {
    console.log('PE-RAW-str', String(e));
  }
});
page.on('console', (m) => {
  if (m.type() === 'error') {
    const args = m.args();
    Promise.all(args.map((a) => a.jsonValue().catch(() => a.toString()))).then(
      (vals) => {
        console.log('CONSOLE-ERROR:', JSON.stringify(vals).slice(0, 500));
      },
    );
  }
});
page.on('response', (r) => {
  if (r.status() >= 400) console.log('HTTP', r.status(), r.url().slice(-80));
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
await page.goto('http://localhost:5999/pms/pm/project/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
await browser.close();
