import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
page.on('console', (m) => {
  if (m.type() === 'error') console.log('console:', m.text().slice(0, 150));
});
await page.goto('http://localhost:5666/auth/login', {
  waitUntil: 'domcontentloaded',
});
await sleep(6000);
const form = await page.evaluate(() =>
  [...document.querySelectorAll('.ant-select')].map((s) =>
    s.innerText.trim().slice(0, 30),
  ),
);
console.log('selects:', JSON.stringify(form));
// 点击租户下拉
await page.locator('.ant-select').first().click();
await sleep(1500);
const opts = await page.evaluate(() =>
  [
    ...document.querySelectorAll(
      '.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option',
    ),
  ].map((o) => o.innerText.trim()),
);
console.log('tenant options:', JSON.stringify(opts));
if (opts.length > 0) {
  await page
    .locator(
      '.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option',
    )
    .first()
    .click();
  await sleep(600);
}
await page.locator('button[aria-label="login"]').click();
await sleep(9000);
console.log('url after login:', page.url());
await page.screenshot({ path: 'output/playwright/fms-fix/antd-login2.png' });
await browser.close();
