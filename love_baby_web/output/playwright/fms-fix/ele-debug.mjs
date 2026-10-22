import { chromium } from 'playwright';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const BASE = 'http://localhost:5777';
const browser = await chromium.launch({ headless: true });
const page = await (
  await browser.newContext({ viewport: { width: 1600, height: 1000 } })
).newPage();
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(e.message.slice(0, 200)));
page.on('console', (m) => {
  if (m.type() === 'error')
    pageErrors.push(`console: ${m.text().slice(0, 200)}`);
});
await page.goto(`${BASE}/auth/login`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
await page
  .locator('button[aria-label="login"], button:has-text("登录")')
  .first()
  .click();
await page.waitForURL((u) => !u.pathname.includes('/auth/login'), {
  timeout: 30_000,
});
await page.goto(`${BASE}/fms/voucher/create`, {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);

await page.locator('.entry-subject .el-select').nth(0).click();
await sleep(1200);
const state1 = await page.evaluate(() => ({
  dropdowns: [...document.querySelectorAll('.el-select-dropdown')].map((d) => ({
    display: getComputedStyle(d).display,
    items: d.querySelectorAll('.el-select-dropdown__item').length,
  })),
}));
await page.keyboard.type('100101');
await sleep(1000);
const state2 = await page.evaluate(() => ({
  items: [...document.querySelectorAll('.el-select-dropdown__item')].map((o) =>
    o.innerText.trim().slice(0, 25),
  ),
  focusedTag: document.activeElement?.tagName,
  focusedClass: String(document.activeElement?.className).slice(0, 60),
}));
await page.screenshot({ path: 'output/playwright/fms-fix/ele-subject-dd.png' });
console.log(
  JSON.stringify(
    { state1, state2, pageErrors: pageErrors.slice(0, 5) },
    null,
    1,
  ),
);
await browser.close();
