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

const cols = [
  { field: 'name', title: '姓名', minWidth: 130, slots: { default: 'name' } },
  {
    field: 'sharedCategoryName',
    title: '分类',
    width: 120,
    slots: { default: 'categoryName' },
  },
  { field: 'mobile', title: '手机号码', width: 140 },
  { field: 'email', title: '邮箱', minWidth: 180 },
  { field: 'companyName', title: '公司名称', minWidth: 160 },
  { field: 'ownerUserName', title: '创建人', width: 120 },
  { field: 'share.userName', title: '接收人', width: 120 },
  {
    field: 'share.createTime',
    title: '共享时间',
    width: 180,
    formatter: 'formatDateTime',
  },
  {
    field: 'share.handleStatus',
    title: '处理状态',
    width: 100,
    slots: { default: 'shareHandleStatus' },
  },
  { title: '操作', width: 220, fixed: 'right', slots: { default: 'actions' } },
];

// 场景 A：空闲时完整列 reload
let r = await page.evaluate(async (columns) => {
  const g = [...document.querySelectorAll('*')]
    .map((el) => el.__vueParentComponent?.proxy)
    .find(
      (p) =>
        p?.reloadColumn && String(p.$el?.className || '').includes('vxe-grid'),
    );
  if (!g) return 'not found';
  window.__grid = g;
  try {
    await g.reloadColumn(columns);
    return `resolved, internal=${g
      .getColumns()
      .map((x) => x.title)
      .join(',')}`;
  } catch (error) {
    return `threw: ${error.message}`;
  }
}, cols);
console.log('A 空闲完整列:', r);
await sleep(1000);
console.log(
  'A DOM:',
  await page.$$eval('.vxe-table--header-wrapper th', (els) =>
    els
      .map((el) => el.textContent.trim())
      .filter(Boolean)
      .join(','),
  ),
);

// 还原
await page.evaluate(async () => {
  await window.__grid.reloadColumn([
    { field: 'name', title: '姓名', minWidth: 130, slots: { default: 'name' } },
    {
      field: 'sharedCategoryName',
      title: '分类',
      width: 120,
      slots: { default: 'categoryName' },
    },
    { field: 'mobile', title: '手机号码', width: 140 },
    { field: 'email', title: '邮箱', minWidth: 180 },
    { field: 'companyName', title: '公司名称', minWidth: 160 },
    { field: 'ownerUserName', title: '创建人', width: 120 },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ]);
});
await sleep(500);

// 场景 B：commitProxy('query') 进行中调用 reloadColumn（模拟 handleSceneSelect 竞态）
r = await page.evaluate(async (columns) => {
  const g = window.__grid;
  const p1 = g.commitProxy('query'); // 不等待，模拟查询进行中
  let res;
  try {
    await g.reloadColumn(columns);
    res = `resolved, internal=${g
      .getColumns()
      .map((x) => x.title)
      .join(',')}`;
  } catch (error) {
    res = `threw: ${error.message}`;
  }
  await p1.catch(() => {});
  return res;
}, cols);
console.log('B 查询中完整列:', r);
await sleep(1000);
console.log(
  'B DOM:',
  await page.$$eval('.vxe-table--header-wrapper th', (els) =>
    els
      .map((el) => el.textContent.trim())
      .filter(Boolean)
      .join(','),
  ),
);
await browser.close();
