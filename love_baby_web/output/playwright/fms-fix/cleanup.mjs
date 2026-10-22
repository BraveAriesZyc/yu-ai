import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = process.argv[2] || 'http://localhost:5999';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.locator('button[aria-label="login"]').click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/list`, { waitUntil: 'domcontentloaded' });
await sleep(4000);

const rows = await page.locator('tr:has-text("功能验收测试凭证")').count();
console.log('rows found:', rows);
if (rows > 0) {
  const row = page.locator('tr:has-text("功能验收测试凭证")').first();
  const actionsHtml = await row.locator('td').last().innerHTML();
  console.log('action cell:', actionsHtml.slice(0, 400));
  await row.locator('text=删除').first().click();
  await sleep(800);
  // vben confirm 弹窗
  await page
    .locator(
      '.vben-alert button.ant-btn-primary, button.ant-btn-primary:has-text("确")',
    )
    .first()
    .click()
    .catch((error) =>
      console.log('confirm click fail', error.message.slice(0, 100)),
    );
  await sleep(2000);
  const after = await page.evaluate(() =>
    document.body.innerText.includes('功能验收测试凭证'),
  );
  console.log('still in list:', after);
  await page.screenshot({ path: 'output/playwright/fms-fix/cleanup.png' });
}
await browser.close();
