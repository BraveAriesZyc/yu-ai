import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 2560, height: 1080 } })
).newPage();
await page.goto('http://localhost/login', { waitUntil: 'domcontentloaded' });
await sleep(2500);
// 源项目登录页：默认账号已填充则直接点登录
const loginBtn = page
  .locator(
    'button:has-text("登 录"), button:has-text("登录"), .el-button:has-text("登")',
  )
  .first();
await loginBtn.click();
await sleep(4000);
console.log('url:', page.url());
await page.goto('http://localhost/#/fms/voucher/list', {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);
await page.screenshot({ path: 'output/playwright/fms-fix/source-list.png' });
await browser.close();
