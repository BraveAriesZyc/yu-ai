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

// 找到 vxe-grid 实例（expose 链上带 reloadColumn 的对象）
const info = await page.evaluate(() => {
  const els = document.querySelectorAll(
    '.vxe-grid, .vxe-table, [class*="vxe"]',
  );
  const found = [];
  for (const el of document.querySelectorAll('*')) {
    const c = el.__vueParentComponent;
    if (!c) continue;
    const targets = [c.exposed, c.proxy, c.ctx];
    for (const t of targets) {
      if (t && typeof t.reloadColumn === 'function') {
        found.push({
          tag: el.tagName,
          cls: String(el.className).slice(0, 80),
          via: targets.indexOf(t),
        });
      }
    }
  }
  return found.slice(0, 10);
});
console.log('grid instances:', JSON.stringify(info));

// 手动调用 reloadColumn 验证 vxe 能力本身
const manual = await page.evaluate(async () => {
  for (const el of document.querySelectorAll('*')) {
    const c = el.__vueParentComponent;
    const t = c?.exposed?.reloadColumn
      ? c.exposed
      : c?.proxy?.reloadColumn
        ? c.proxy
        : null;
    if (t) {
      try {
        await t.reloadColumn([
          { field: 'name', title: '姓名', minWidth: 130 },
          { field: 'share.userName', title: '接收人', width: 120 },
          { field: 'share.createTime', title: '共享时间', width: 180 },
          { field: 'share.handleStatus', title: '处理状态', width: 100 },
        ]);
        return 'manual reloadColumn ok';
      } catch (error) {
        return `manual reloadColumn threw: ${error.message}`;
      }
    }
  }
  return 'no grid instance found';
});
console.log(manual);
await sleep(1500);
console.log(
  'headers after manual reload:',
  JSON.stringify(
    await page.$$eval('.vxe-table--header-wrapper th', (els) =>
      els.map((el) => el.textContent.trim()).filter(Boolean),
    ),
  ),
);
await browser.close();
