import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 2560, height: 1080 } })
).newPage();
await page.goto('http://localhost/login', { waitUntil: 'domcontentloaded' });
await sleep(2500);
await page
  .locator('button:has-text("登"), .el-button:has-text("登")')
  .first()
  .click();
await sleep(5000);
await page.goto('http://localhost/index#/fms/voucher/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(2000);
await page.goto('http://localhost/index#/fms/voucher/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
console.log('url:', page.url());
await page.screenshot({ path: 'output/playwright/fms-fix/source-list2.png' });
await browser.close();
