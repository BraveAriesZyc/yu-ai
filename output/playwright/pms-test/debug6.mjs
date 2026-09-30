import { chromium } from 'playwright';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({
  viewport: { width: 1680, height: 1000 },
});
const page = await ctx.newPage();
page.on('pageerror', (e) => {
  console.log(
    'PE name:',
    e.name,
    '| msg:',
    e.message.slice(0, 300),
    '| stack:',
    (e.stack || '').split('\n').slice(0, 4).join(' | '),
  );
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
await page.goto('http://localhost:5999/pms/pm/workbench', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
console.log('--- workbench done, go list');
await page.goto('http://localhost:5999/pms/pm/project/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
console.log('--- list done, open detail');
const first = page.locator('table .ant-table-cell .truncate').first();
await first.click();
await sleep(5000);
console.log('url:', page.url());
// 回工作台打开工作项详情抽屉
await page.goto('http://localhost:5999/pms/pm/workbench', {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
const wi = page.locator('table .ant-btn-link').first();
if (await wi.count()) {
  console.log('workitem link text:', await wi.innerText());
  await wi.click();
  await sleep(5000);
  console.log('drawer opened');
}
// kb 文档中心
await page.goto('http://localhost:5999/pms/kb/library', {
  waitUntil: 'domcontentloaded',
});
await sleep(3000);
const lib = page.locator('table .ant-table-cell .truncate').first();
if (await lib.count()) {
  await lib.click();
  await sleep(5000);
  console.log('kb lib url:', page.url());
}
await browser.close();
