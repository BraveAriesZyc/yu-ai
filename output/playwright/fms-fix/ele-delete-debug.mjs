import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5777';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
await page
  .locator('button[aria-label="login"], button:has-text("登录")')
  .first()
  .click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/list`, { waitUntil: 'domcontentloaded' });
await sleep(4500);
const idx = await page.evaluate(() => {
  const rows = [
    ...document.querySelectorAll('.vxe-table--body-wrapper tbody tr'),
  ];
  return rows.findIndex((t) => t.innerText.includes('功能验收测试凭证'));
});
console.log('row idx:', idx);
if (idx >= 0) {
  const actionRow = page
    .locator(
      '.vxe-table--fixed-right-wrapper tbody tr, .vxe-table--fixed--right-wrapper tbody tr',
    )
    .nth(idx);
  await actionRow.locator('text=删除').first().click();
  await sleep(1500);
  const dialogs = await page.evaluate(() =>
    [
      ...document.querySelectorAll(
        '[role="dialog"], .el-message-box, .el-overlay',
      ),
    ].map((d) => ({
      cls: String(d.className).slice(0, 60),
      text: d.innerText.slice(0, 120).replaceAll('\n', '|'),
      visible: d.offsetWidth > 0,
    })),
  );
  console.log(JSON.stringify(dialogs, null, 1));
  await page.screenshot({
    path: 'output/playwright/fms-fix/ele-delete-confirm.png',
  });
}
await browser.close();
