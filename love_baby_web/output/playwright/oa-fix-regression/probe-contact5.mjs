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

await page.evaluate(() => {
  for (const el of document.querySelectorAll('*')) {
    const c = el.__vueParentComponent;
    const t = c?.proxy?.reloadColumn ? c.proxy : null;
    if (t && String(el.className).includes('vxe-grid')) {
      window.__grid = t;
      const orig = t.reloadColumn.bind(t);
      Object.defineProperty(t, 'reloadColumn', {
        configurable: true,
        value: async (cols) => {
          window.__called = (cols || []).map((x) => x.title);
          return await orig(cols);
        },
      });
      return;
    }
  }
});

await page.locator('.ant-menu-item:has-text("我共享的")').first().click();
for (const t of [500, 1000, 2000, 4000]) {
  await sleep(t === 500 ? 500 : t / 2);
  const state = await page.evaluate(() => {
    const g = window.__grid;
    let internal = 'n/a';
    try {
      internal = (g.getColumns ? g.getColumns() : [])
        .map((c) => c.title)
        .join(',');
    } catch (error) {
      internal = `err:${error.message}`;
    }
    return {
      called: window.__called,
      internal,
      dom: [...document.querySelectorAll('.vxe-table--header-wrapper th')]
        .map((el) => el.textContent.trim())
        .filter(Boolean)
        .join(','),
    };
  });
  console.log(`t~${t}ms:`, JSON.stringify(state));
}
await browser.close();
