import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = process.argv[2] || 'http://127.0.0.1:5666';
const tag = process.argv[3] || 'antd';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1680, height: 1000 } })
).newPage();

// 登录
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

// ---- 日程日历 ----
await page.goto(`${base}/oa/schedule/calendar`, {
  waitUntil: 'domcontentloaded',
});
await sleep(4000);
console.log('== calendar ==');
console.log(
  'radio group html:',
  (
    await page
      .locator('.ant-radio-group, .el-radio-group')
      .first()
      .innerHTML()
      .catch(() => 'NONE')
  ).slice(0, 600),
);
console.log(
  'buttons 今天:',
  await page.locator('button:has-text("今天")').count(),
  '| 今天 any:',
  await page.locator(':has-text("今天")').count(),
);
console.log(
  'period buttons html:',
  (
    await page
      .locator('button:has-text("上一")')
      .first()
      .innerHTML()
      .catch(() => 'NONE')
  ).slice(0, 300),
);

// ---- 写信 ----
await page.goto(`${base}/oa/mail/inbox`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
console.log('== mail ==');
console.log(
  '写信 buttons:',
  await page.locator('button:has-text("写信")').count(),
);
console.log('写信 any el:', await page.locator(':text-is("写信")').count());
console.log(
  'body snippet:',
  (await page.evaluate(() => document.body.innerText))
    .slice(0, 400)
    .replaceAll('\n', ' / '),
);

// ---- 请假表单 ----
await page.goto(`${base}/oa/process/leave`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
console.log('== leave ==');
console.log(
  '新增 buttons:',
  await page.locator('button:has-text("新增")').count(),
);
await page.locator('button:has-text("新增")').first().click();
await sleep(2500);
console.log('dialogs:', await page.locator('[role="dialog"]').count());
console.log(
  'dialog html head:',
  (
    await page
      .locator('[role="dialog"]')
      .first()
      .innerHTML()
      .catch(() => 'NONE')
  ).slice(0, 800),
);
console.log(
  'form labels:',
  JSON.stringify(
    await page.$$eval('[role="dialog"] label', (els) =>
      els.map((e) => e.textContent.trim()),
    ),
  ),
);
await page.keyboard.press('Escape');
await sleep(800);

// ---- 车辆 ----
await page.goto(`${base}/oa/vehicle/list`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
console.log('== vehicle ==');
await page.locator('button:has-text("新增")').first().click();
await sleep(2500);
console.log('dialogs:', await page.locator('[role="dialog"]').count());
console.log(
  'form labels:',
  JSON.stringify(
    await page.$$eval('[role="dialog"] label', (els) =>
      els.map((e) => e.textContent.trim()),
    ),
  ),
);
await page.keyboard.press('Escape');
await sleep(800);

// ---- 通讯录 ----
await page.goto(`${base}/oa/contact`, { waitUntil: 'domcontentloaded' });
await sleep(4000);
console.log('== contact ==');
console.log(
  'menu items:',
  JSON.stringify(
    await page.$$eval('.ant-menu-item, .el-menu-item', (els) =>
      els.map((e) => e.textContent.trim()),
    ),
  ),
);
await page
  .locator(
    '.ant-menu-item:has-text("我共享的"), .el-menu-item:has-text("我共享的")',
  )
  .first()
  .click();
await sleep(4000);
console.log(
  'headers after switch:',
  JSON.stringify(
    await page.$$eval('.vxe-table--header-wrapper th', (els) =>
      els.map((el) => el.textContent.trim()),
    ),
  ),
);

await browser.close();
