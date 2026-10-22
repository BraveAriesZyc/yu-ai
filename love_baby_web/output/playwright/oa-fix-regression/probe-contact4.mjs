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
page.on('console', (m) => {
  if (['error', 'warning'].includes(m.type()))
    console.log('CONSOLE:', m.text().slice(0, 250));
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

await page.goto(`${base}/oa/contact`, { waitUntil: 'domcontentloaded' });
await sleep(4000);

// 给 grid 实例的 reloadColumn 打补丁，观察应用是否调用及是否报错
const patched = await page.evaluate(() => {
  for (const el of document.querySelectorAll('*')) {
    const c = el.__vueParentComponent;
    const t = c?.proxy?.reloadColumn ? c.proxy : null;
    if (t && String(el.className).includes('vxe-grid')) {
      const orig = t.reloadColumn.bind(t);
      window.__calls = [];
      try {
        Object.defineProperty(t, 'reloadColumn', {
          configurable: true,
          value: async (cols) => {
            window.__calls.push(
              `call:${(cols || []).map((x) => x.title).join(',')}`,
            );
            try {
              const r = await orig(cols);
              window.__calls.push('resolved');
              return r;
            } catch (error) {
              window.__calls.push(`threw:${error.message}`);
              throw error;
            }
          },
        });
        return 'patched on vxe-grid proxy';
      } catch (error) {
        return `patch failed: ${error.message}`;
      }
    }
  }
  return 'not found';
});
console.log(patched);

await page.locator('.ant-menu-item:has-text("我共享的")').first().click();
await sleep(4000);
console.log(
  'calls:',
  JSON.stringify(await page.evaluate(() => window.__calls || 'none')),
);
console.log(
  'headers:',
  JSON.stringify(
    await page.$$eval('.vxe-table--header-wrapper th', (els) =>
      els.map((el) => el.textContent.trim()).filter(Boolean),
    ),
  ),
);
await browser.close();
