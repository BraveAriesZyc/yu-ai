import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(page, base) {
  await page.goto(`${base}/auth/login`, { waitUntil: 'domcontentloaded' });
  await sleep(1500);
  const tenantSelect = page.locator(
    '.ant-select:has-text("请选择租户"), .el-select:has-text("请选择租户")',
  );
  if (await tenantSelect.count()) {
    await tenantSelect.click();
    await sleep(1200);
    await page
      .locator(
        '.ant-select-dropdown .ant-select-item-option, .el-select-dropdown__item',
      )
      .first()
      .click();
    await sleep(500);
  }
  await page.locator('button[aria-label="login"]').click();
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
    timeout: 30_000,
  });
}

const browser = await chromium.launch({ headless: true });
const base = 'http://127.0.0.1:5666';
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();
await login(page, base);
await page.goto(`${base}/oa/process/regular`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
await page
  .locator('button', { hasText: /新\s*增/ })
  .first()
  .click();
await sleep(2000);
const dlg = page.locator('[role="dialog"]:visible').last();
const html = await dlg.evaluate((el) => el.innerHTML);
console.log('html含maxlength:', html.includes('maxlength'));
console.log('html含showCount计数(0 / ):', /0\s*\/\s*\d+/.test(html));
// 标题 Input 的 maxlength
const titleInput = dlg.locator('input[placeholder="请输入标题"]').first();
console.log(
  '标题 input maxlength:',
  await titleInput.getAttribute('maxlength'),
);
// textarea 外层结构
const ta = dlg.locator('textarea[name="experience"]');
console.log('experience textarea exists:', await ta.count());
const parent = ta.locator('xpath=..');
console.log(
  'parent html:',
  (await parent.evaluate((el) => el.outerHTML)).slice(0, 400),
);
// 输入 300 字符测试是否真的限制
if (await ta.count()) {
  await ta.fill('a'.repeat(300));
  const v = await ta.inputValue();
  console.log('输入300字符后长度:', v.length);
}
await browser.close();
