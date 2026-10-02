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

async function tryReload(label, cols) {
  const r = await page.evaluate(async (columns) => {
    for (const el of document.querySelectorAll('*')) {
      const c = el.__vueParentComponent;
      const t = c?.proxy?.reloadColumn ? c.proxy : null;
      if (t && String(el.className).includes('vxe-grid')) {
        try {
          await t.reloadColumn(columns);
          const got = t.getColumns().map((x) => x.title);
          return `resolved, internal=${got.join(',')}`;
        } catch (error) {
          return `threw: ${error.message}`;
        }
      }
    }
    return 'not found';
  }, cols);
  console.log(label, '=>', r);
}

const baseCols = [
  { field: 'name', title: '姓名', minWidth: 130 },
  { field: 'share.userName', title: '接收人', width: 120 },
];

await tryReload('plain', baseCols);
await tryReload('with-string-formatter', [
  ...baseCols,
  {
    field: 'share.createTime',
    title: '共享时间',
    width: 180,
    formatter: 'formatDateTime',
  },
]);
await tryReload('with-slots', [
  { field: 'name', title: '姓名', minWidth: 130, slots: { default: 'name' } },
  {
    field: 'share.handleStatus',
    title: '处理状态',
    width: 100,
    slots: { default: 'shareHandleStatus' },
  },
]);
await tryReload('with-fixed-right', [
  ...baseCols,
  { title: '操作', width: 220, fixed: 'right', slots: { default: 'actions' } },
]);
await browser.close();
