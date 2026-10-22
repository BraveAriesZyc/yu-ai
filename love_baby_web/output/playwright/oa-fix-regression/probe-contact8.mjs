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
      window.__log = [];
      const orig = t.reloadColumn.bind(t);
      Object.defineProperty(t, 'reloadColumn', {
        configurable: true,
        value: async (cols) => {
          const before = t
            .getColumns()
            .map((x) => x.title)
            .join(',');
          const r = await orig(cols);
          const after = t
            .getColumns()
            .map((x) => x.title)
            .join(',');
          window.__log.push({
            in: cols.map((x) => x.title).join(','),
            before,
            after,
          });
          return r;
        },
      });
      // 也观察 loadColumn（vxe 内部 props 同步路径）
      if (t.loadColumn) {
        const origLoad = t.loadColumn.bind(t);
        Object.defineProperty(t, 'loadColumn', {
          configurable: true,
          value: async (cols) => {
            window.__log.push({
              loadColumn: (cols || []).map((x) => x.title).join(','),
            });
            return await origLoad(cols);
          },
        });
      }
      return;
    }
  }
});

await page.locator('.ant-menu-item:has-text("我共享的")').first().click();
for (const t of [600, 1200, 2500]) {
  await sleep(t / 2);
  console.log(
    `t~${t}:`,
    JSON.stringify(await page.evaluate(() => window.__log)),
    '| dom:',
    await page.$$eval('.vxe-table--header-wrapper th', (els) =>
      els
        .map((el) => el.textContent.trim())
        .filter(Boolean)
        .join(','),
    ),
  );
}
await browser.close();
