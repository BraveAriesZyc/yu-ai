import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message.slice(0, 200)));
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(`console: ${m.text().slice(0, 150)}`);
});
await page.goto('http://localhost:5666/auth/login', {
  waitUntil: 'domcontentloaded',
});
await sleep(6000);
const hasLoginBtn = await page.locator('button[aria-label="login"]').count();
console.log('login buttons:', hasLoginBtn, 'url:', page.url());
await page.screenshot({ path: 'output/playwright/fms-fix/antd-login.png' });
if (hasLoginBtn) {
  await page.locator('button[aria-label="login"]').click();
  await sleep(8000);
  console.log('after click url:', page.url());
  await page.screenshot({
    path: 'output/playwright/fms-fix/antd-after-login.png',
  });
}
console.log(JSON.stringify({ errors: errors.slice(0, 8) }, null, 1));
await browser.close();
