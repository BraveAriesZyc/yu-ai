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
await page.goto(`${BASE}/fms/voucher/create`, {
  waitUntil: 'domcontentloaded',
});
await sleep(5000);

await page.locator('.entry-digest input').nth(0).fill('功能验收测试凭证');
await page.locator('.entry-subject .el-select').nth(0).click();
await sleep(800);
await page.keyboard.type('100101');
await sleep(800);
await page
  .locator('.el-select-dropdown__item:has-text("100101 人民币现金"):visible')
  .first()
  .click();
await sleep(600);
await page.locator('td[data-entry-direction="debit"] input').nth(0).fill('100');
await page
  .locator('td[data-entry-direction="debit"] input')
  .nth(0)
  .press('Enter');
await sleep(500);
await page.locator('.entry-subject .el-select').nth(1).click();
await sleep(800);
await page.keyboard.type('1122');
await sleep(800);
await page
  .locator('.el-select-dropdown__item:has-text("1122 应收账款"):visible')
  .first()
  .click();
await sleep(600);
await page
  .locator('td[data-entry-direction="credit"] input')
  .nth(1)
  .fill('100');
await page
  .locator('td[data-entry-direction="credit"] input')
  .nth(1)
  .press('Enter');
await sleep(800);

// 捕获保存响应
const saveResp = page
  .waitForResponse((r) => r.url().includes('/fms/voucher/create'), {
    timeout: 8000,
  })
  .catch(() => null);
await page
  .locator('button')
  .filter({ hasText: /^保\s*存$/ })
  .first()
  .click();
await sleep(2500);
const resp = await saveResp;
const messages = await page.evaluate(() => ({
  msgs: [...document.querySelectorAll('.el-message')].map((m) =>
    m.innerText.slice(0, 100),
  ),
  subjectCells: [...document.querySelectorAll('.entry-subject')]
    .slice(1, 3)
    .map((c) => c.innerText.trim().slice(0, 30)),
  digestCells: [...document.querySelectorAll('.entry-digest input')]
    .slice(0, 2)
    .map((i) => i.value),
}));
console.log(
  JSON.stringify(
    {
      saveApiStatus: resp ? resp.status() : 'no-request',
      saveApiBody: resp ? (await resp.json()).catch(() => null) : null,
      ...messages,
    },
    null,
    1,
  ),
);
await page.screenshot({ path: 'output/playwright/fms-fix/ele-save-debug.png' });
await browser.close();
